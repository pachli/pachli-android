package app.pachli.components.account

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.common.extensions.stateFlow
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.AccountRepository
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.data.repository.getOrNull
import app.pachli.core.domain.accounts.BlockAccountUseCase
import app.pachli.core.domain.accounts.BlockDomainUseCase
import app.pachli.core.domain.accounts.FollowAccountUseCase
import app.pachli.core.domain.accounts.MuteAccountUseCase
import app.pachli.core.domain.accounts.SubscribeAccountUseCase
import app.pachli.core.domain.accounts.UnblockAccountUseCase
import app.pachli.core.domain.accounts.UnfollowAccountUseCase
import app.pachli.core.domain.accounts.UnmuteAccountUseCase
import app.pachli.core.domain.accounts.UnsubscribeAccountUseCase
import app.pachli.core.eventhub.EventHub
import app.pachli.core.eventhub.ProfileEditedEvent
import app.pachli.core.model.Account
import app.pachli.core.model.Relationship
import app.pachli.core.network.model.asModel
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.ui.getDomain
import app.pachli.util.Error
import app.pachli.util.Loading
import app.pachli.util.Resource
import app.pachli.util.Success
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel(assistedFactory = AccountViewModel.Factory::class)
class AccountViewModel @AssistedInject constructor(
    @Assisted private val pachliAccountId: Long,
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub,
    accountManager: AccountManager,
    private val followAccountUseCase: FollowAccountUseCase,
    private val unfollowAccountUseCase: UnfollowAccountUseCase,
    private val blockAccountUseCase: BlockAccountUseCase,
    private val unblockAccountUseCase: UnblockAccountUseCase,
    private val muteAccountUseCase: MuteAccountUseCase,
    private val unmuteAccountUseCase: UnmuteAccountUseCase,
    private val subscribeAccountUseCase: SubscribeAccountUseCase,
    private val unsubscribeAccountUseCase: UnsubscribeAccountUseCase,
    private val blockDomainUseCase: BlockDomainUseCase,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    val relationshipData = MutableLiveData<Resource<Relationship>>()

    val noteSaved = MutableLiveData<Boolean>()

    val isRefreshing = MutableLiveData<Boolean>().apply { value = false }

    /** The domain of the viewed account **/
    var domain = ""

    /** True if the viewed account has the same domain as the active account */
    var isFromOwnDomain = false

    private var noteUpdateJob: Job? = null

    /** Flow of data about the account identified by [pachliAccountId]. */
    private val pachliAccount = accountManager.getPachliAccountFlow(pachliAccountId).filterNotNull()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    /** Emit into this to trigger a reload. */
    private val reload = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    /** Flow that triggers a reload when `Unit` is emitted into it. */
    private val accountId = MutableSharedFlow<String>(replay = 1)

    /** Flow of changes to statusDisplayOptions, for use by the UI */
    val statusDisplayOptions = statusDisplayOptionsRepository.flow

    /**
     * Flow of [Account]. Starts with the account data from the API, and updates on
     * receiving a matching [ProfileEditedEvent].
     */
    val accountData = stateFlow(viewModelScope, Ok(Loadable.Loading)) {
        // Take any ProfileEditedEvents that match accountId...
        val profileUpdates = combine(
            accountId,
            eventHub.events.filterIsInstance<ProfileEditedEvent>(),
        ) { accountId, event ->
            if (event.newProfileData.serverId == accountId) {
                Ok(Loadable.Loaded(event.newProfileData))
            } else {
                null
            }
        }.filterNotNull()

        // ... with the result from the API...
        val remoteAccount = combine(reload, pachliAccount, accountId) { _, pachliAccount, accountId ->
            accountRepository.getAccount(accountId)
                .onSuccess { account ->
                    domain = getDomain(account.url)
                    isFromOwnDomain = domain == pachliAccount.domain
                    val isSelf = pachliAccount.accountId == accountId
                    if (!isSelf) obtainRelationship(accountId)
                }
                .onFailure {
                    Timber.w("failed obtaining account: %s", it)
                }
                .map { Loadable.Loaded(it) }
        }

        // ... and merge them together. Include the `reload` flow, to ensure the
        // content transitions to a `Loadable.Loading` state before each load.
        // Practically, the first emission will be the data from the API, subsequent
        // emissions will be from profileUpdates
        merge(reload.map { Ok(Loadable.Loading) }, profileUpdates, remoteAccount).flowWhileShared(SharingStarted.WhileSubscribed(5000))
    }

    /** True if the loaded account in [accountData] is the user's account. */
    val isSelf = combine(accountData, pachliAccount) { accountData, pachliAccount ->
        pachliAccount.accountId == accountData.get()?.getOrNull()?.serverId
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun obtainRelationship(accountId: String, reload: Boolean = false) {
        if (relationshipData.value == null || reload) {
            relationshipData.postValue(Loading())

            viewModelScope.launch {
                mastodonApi.relationships(listOf(accountId))
                    .onSuccess {
                        val relationships = it.body.asModel()
                        relationshipData.postValue(if (relationships.isNotEmpty()) Success(relationships[0]) else Error())
                    }
                    .onFailure {
                        Timber.w("failed obtaining relationships: %s", it)
                        relationshipData.postValue(Error(cause = it.throwable))
                    }
            }
        }
    }

    fun changeFollowState(account: Account) {
        val relationship = relationshipData.value?.data
        if (relationship?.following == true || relationship?.requested == true) {
            changeRelationship(account, RelationShipAction.UNFOLLOW)
        } else {
            changeRelationship(account, RelationShipAction.FOLLOW)
        }
    }

    fun changeBlockState(account: Account) {
        if (relationshipData.value?.data?.blocking == true) {
            changeRelationship(account, RelationShipAction.UNBLOCK)
        } else {
            changeRelationship(account, RelationShipAction.BLOCK)
        }
    }

    fun muteAccount(account: Account, notifications: Boolean, duration: Int?) {
        changeRelationship(account, RelationShipAction.MUTE, notifications, duration)
    }

    fun unmuteAccount(account: Account) {
        changeRelationship(account, RelationShipAction.UNMUTE)
    }

    fun changeSubscribingState(account: Account) {
        val relationship = relationshipData.value?.data
        if (relationship?.notifying == true ||
            // Mastodon 3.3.0rc1
            relationship?.subscribing == true // Pleroma
        ) {
            changeRelationship(account, RelationShipAction.UNSUBSCRIBE)
        } else {
            changeRelationship(account, RelationShipAction.SUBSCRIBE)
        }
    }

    fun blockDomain(instance: String) {
        viewModelScope.launch {
            blockDomainUseCase(pachliAccountId, instance)
                .onSuccess {
                    val relation = relationshipData.value?.data
                    if (relation != null) {
                        relationshipData.postValue(Success(relation.copy(blockingDomain = true)))
                    }
                }
                .onFailure { e ->
                    Timber.e("Error muting %s: %s", instance, e)
                }
        }
    }

    fun unblockDomain(instance: String) {
        viewModelScope.launch {
            mastodonApi.unblockDomain(instance)
                .onSuccess {
                    val relation = relationshipData.value?.data
                    if (relation != null) {
                        relationshipData.postValue(Success(relation.copy(blockingDomain = false)))
                    }
                }
                .onFailure { e ->
                    Timber.e("Error unmuting %s: %s", instance, e)
                }
        }
    }

    fun changeShowReblogsState(account: Account) {
        if (relationshipData.value?.data?.showingReblogs == true) {
            changeRelationship(account, RelationShipAction.HIDE_REBLOGS)
        } else {
            changeRelationship(account, RelationShipAction.SHOW_REBLOGS)
        }
    }

    /**
     * @param account ID of the account that is the target of [relationshipAction].
     * @param relationshipAction The action to take.
     * @param parameter showReblogs if RelationShipAction.FOLLOW, notifications if MUTE
     */
    private fun changeRelationship(
        account: Account,
        relationshipAction: RelationShipAction,
        parameter: Boolean? = null,
        duration: Int? = null,
    ) = viewModelScope.launch {
        val relation = relationshipData.value?.data
        val cachedAccount = accountData.value.get()?.getOrNull()
        val isMastodon = relationshipData.value?.data?.notifying != null

        if (relation != null && cachedAccount != null) {
            // optimistically post new state for faster response

            val newRelation = when (relationshipAction) {
                RelationShipAction.FOLLOW -> {
                    if (cachedAccount.locked) {
                        relation.copy(requested = true)
                    } else {
                        relation.copy(following = true)
                    }
                }
                RelationShipAction.UNFOLLOW -> relation.copy(following = false)
                RelationShipAction.BLOCK -> relation.copy(blocking = true)
                RelationShipAction.UNBLOCK -> relation.copy(blocking = false)
                RelationShipAction.MUTE -> relation.copy(muting = true)
                RelationShipAction.UNMUTE -> relation.copy(muting = false)
                RelationShipAction.SUBSCRIBE -> {
                    if (isMastodon) {
                        relation.copy(notifying = true)
                    } else {
                        relation.copy(subscribing = true)
                    }
                }
                RelationShipAction.UNSUBSCRIBE -> {
                    if (isMastodon) {
                        relation.copy(notifying = false)
                    } else {
                        relation.copy(subscribing = false)
                    }
                }

                RelationShipAction.SHOW_REBLOGS -> relation.copy(showingReblogs = true)
                RelationShipAction.HIDE_REBLOGS -> relation.copy(showingReblogs = false)
            }
            relationshipData.postValue(Loading(newRelation))
        }

        val response = when (relationshipAction) {
            RelationShipAction.FOLLOW -> followAccountUseCase(pachliAccountId, account, showReblogs = true)
            RelationShipAction.UNFOLLOW -> unfollowAccountUseCase(pachliAccountId, account.serverId)
            RelationShipAction.BLOCK -> blockAccountUseCase(pachliAccountId, account.serverId)
            RelationShipAction.UNBLOCK -> unblockAccountUseCase(pachliAccountId, account.serverId)
            RelationShipAction.MUTE -> muteAccountUseCase(
                pachliAccountId,
                account.serverId,
                notifications = parameter ?: true,
                duration,
            )

            RelationShipAction.UNMUTE -> unmuteAccountUseCase(pachliAccountId, account.serverId)
            RelationShipAction.SUBSCRIBE -> {
                if (isMastodon) {
                    followAccountUseCase(pachliAccountId, account, notify = true)
                } else {
                    subscribeAccountUseCase(pachliAccountId, account.serverId)
                }
            }
            RelationShipAction.UNSUBSCRIBE -> {
                if (isMastodon) {
                    followAccountUseCase(pachliAccountId, account, notify = false)
                } else {
                    unsubscribeAccountUseCase(pachliAccountId, account.serverId)
                }
            }

            RelationShipAction.SHOW_REBLOGS -> followAccountUseCase(pachliAccountId, account, showReblogs = true)
            RelationShipAction.HIDE_REBLOGS -> followAccountUseCase(pachliAccountId, account, showReblogs = false)
        }

        response
            .onSuccess { relationshipData.postValue(Success(it)) }
            .onFailure { e ->
                Timber.w("failed loading relationship: %s", e)
                relationshipData.postValue(Error(relation, cause = e.throwable))
            }
    }

    fun noteChanged(accountId: String, newNote: String) {
        noteSaved.postValue(false)
        noteUpdateJob?.cancel()
        noteUpdateJob = viewModelScope.launch {
            delay(1500)
            mastodonApi.updateAccountNote(accountId, newNote)
                .onSuccess {
                    noteSaved.postValue(true)
                    delay(4000)
                    noteSaved.postValue(false)
                }
                .onFailure { e ->
                    Timber.w("Error updating note: %s", e)
                }
        }
    }

    fun refresh() {
        this.reload.tryEmit(Unit)
    }

    /**
     * Loads data about [accountId], which will trigger an update to [accountData].
     *
     * @param accountId Server ID of the account to load.
     */
    fun loadAccount(accountId: String) {
        viewModelScope.launch { this@AccountViewModel.accountId.emit(accountId) }
    }

    enum class RelationShipAction {
        FOLLOW,
        UNFOLLOW,
        BLOCK,
        UNBLOCK,
        MUTE,
        UNMUTE,
        SUBSCRIBE,
        UNSUBSCRIBE,
        SHOW_REBLOGS,
        HIDE_REBLOGS,
    }

    @AssistedFactory
    interface Factory {
        /** Creates [AccountViewModel] with [pachliAccountId] as the active account. */
        fun create(pachliAccountId: Long): AccountViewModel
    }
}
