/*
 * Copyright (c) 2026 Pachli Association
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.feature.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.common.extensions.stateFlow
import app.pachli.core.common.extensions.throttleFirst
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.ICollectionsRepository
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.RelationshipsRepository
import app.pachli.core.data.repository.StatusDisplayOptionsRepository
import app.pachli.core.data.repository.mapLoaded
import app.pachli.core.domain.accounts.FollowAccountUseCase
import app.pachli.core.domain.accounts.UnfollowAccountUseCase
import app.pachli.core.model.Account
import app.pachli.core.model.Collection
import app.pachli.core.model.CollectionWithAccounts
import app.pachli.core.model.Relationship
import app.pachli.core.ui.OperationCounter
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapEither
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class CollectionViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val collectionsRepository: ICollectionsRepository,
    private val relationshipsRepository: RelationshipsRepository,
    private val followAccountUseCase: FollowAccountUseCase,
    private val unfollowAccountUseCase: UnfollowAccountUseCase,
    statusDisplayOptionsRepository: StatusDisplayOptionsRepository,
) : ViewModel(), ICollectionViewModel {
    private val uiAction = MutableSharedFlow<ICollectionViewModel.UiAction>()
    override val accept: (ICollectionViewModel.UiAction) -> Unit = { viewModelScope.launch { uiAction.emit(it) } }

    private val _uiResult =
        Channel<Result<ICollectionViewModel.UiSuccess, ICollectionViewModel.UiError>>()
    override val uiResult = _uiResult.receiveAsFlow()

    private val operationCounter = OperationCounter()
    override val operationCount = operationCounter.count

    private val reload = MutableSharedFlow<Unit>(replay = 1) // .apply { tryEmit(Unit) }

    private val pachliAccountId = MutableSharedFlow<Long>(replay = 1)

    private val pachliAccount = pachliAccountId.distinctUntilChanged().flatMapLatest {
        accountManager.getPachliAccountFlow(it).filterNotNull()
    }.shareIn(viewModelScope, SharingStarted.Companion.Eagerly, replay = 1)

    private val collectionId = MutableSharedFlow<String>(replay = 1)

    private val collectionAndOwner = combine(
        pachliAccountId,
        collectionId,
    ) { pachliAccountId, collectionId ->
        pachliAccountId to collectionId
    }.flatMapLatest { (pachliAccountId, collectionId) ->
        collectionsRepository.getCollection(pachliAccountId, collectionId)
            .map { Triple(pachliAccountId, collectionId, it) }
    }

    override val uiOptions = stateFlow(
        viewModelScope,
        ICollectionViewModel.UiOptions.Companion.from(statusDisplayOptionsRepository.flow.value),
    ) {
        statusDisplayOptionsRepository.flow.map { ICollectionViewModel.UiOptions.Companion.from(it) }
            .flowWhileShared(SharingStarted.Companion.WhileSubscribed(5000))
    }

    /**
     * Map from [account IDs][app.pachli.core.model.ITimelineAccount.serverId] to the
     * user's [app.pachli.core.model.Relationship] to that account, so that appropriate actions (follow,
     * unfollow, etc) can be shown in the UI.
     */
    private val relationships = MutableStateFlow<Map<String, Relationship>>(emptyMap())

    /**
     * IDs of accounts the user can't interact with (e.g., because they have active
     * network operations).
     */
    private val disabledAccountIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Either successfully loaded [app.pachli.core.model.CollectionWithAccounts], or the error from the
     * most recent attempt to reload from the server.
     */
    private val _collectionWithAccounts =
        MutableStateFlow<Result<Loadable<CollectionWithAccounts>, ICollectionViewModel.UiError.GetCollection>>(
            Ok(Loadable.Loading),
        )

    // Combines the pachliAccount, the most recent collection data, relationships, and
    // disabled accounts to produce the CollectionViewData.
    override val collectionViewData = stateFlow(viewModelScope, Ok(Loadable.Loading)) {
        combine(
            pachliAccount,
            _collectionWithAccounts,
            relationships,
            disabledAccountIds,
        ) { pachliAccount, collectionWithAccounts, relationships, disabledAccountIds ->
            collectionWithAccounts.map {
                it.mapLoaded { collectionWithAccounts ->
                    val collection = collectionWithAccounts.collection
                    val owner = collectionWithAccounts.owner
                    val accounts = collectionWithAccounts.accounts
                    ICollectionViewModel.CollectionViewData(
                        collection = collection,
                        owner = owner?.let {
                            AccountViewData(
                                pachliAccountId = pachliAccount.id,
                                collectionId = collection.serverId,
                                account = owner,
                                relationship = relationships[owner.serverId],
                                isEnabled = !disabledAccountIds.contains(it.serverId),
                                isSelf = owner.serverId == pachliAccount.accountId,
                                primaryAction = makePrimaryAction(
                                    pachliAccountId = pachliAccount.id,
                                    collection = collection,
                                    account = owner,
                                    isSelf = owner.serverId == pachliAccount.accountId,
                                    relationship = relationships[it.serverId],
                                ),
                            )
                        },
                        accounts = accounts.map {
                            AccountViewData(
                                pachliAccountId = pachliAccount.id,
                                collectionId = collection.serverId,
                                account = it,
                                relationship = relationships[it.serverId],
                                isEnabled = !disabledAccountIds.contains(it.serverId),
                                isSelf = it.serverId == pachliAccount.accountId,
                                primaryAction = makePrimaryAction(
                                    pachliAccountId = pachliAccount.id,
                                    collection = collection,
                                    account = it,
                                    isSelf = it.serverId == pachliAccount.accountId,
                                    relationship = relationships[it.serverId],
                                ),
                            )
                        },
                        isMember = accounts.firstOrNull { it.serverId == pachliAccount.accountId },
                    )
                }
            }
        }.flowWhileShared(SharingStarted.Companion.WhileSubscribed(5000))
    }

    init {
        viewModelScope.launch {
            combine(reload, pachliAccountId, collectionId) { _, pachliAccountId, collectionId ->
                Pair(pachliAccountId, collectionId)
            }.collect { (pachliAccountId, collectionId) ->
                _collectionWithAccounts.value = Ok(Loadable.Loading)
                collectionsRepository.reloadCollection(pachliAccountId, collectionId)
                    .mapError { ICollectionViewModel.UiError.GetCollection(it) }
                    .onFailure { _collectionWithAccounts.value = Err(it) }
            }
        }

        viewModelScope.launch {
            collectionAndOwner.collect { (pachliAccountId, collectionId, collectionWithAccounts) ->
                _collectionWithAccounts.value = Ok(Loadable.Loading)
                if (collectionWithAccounts != null) {
                    _collectionWithAccounts.value = Ok(Loadable.Loaded(collectionWithAccounts))
                    return@collect
                }

                reload.emit(Unit)
//                collectionsRepository.reloadCollection(pachliAccountId, collectionId)
//                    .mapError { UiError.GetCollection(it) }
//                    .onFailure { _cwa.value = Err(it) }
            }
        }

        viewModelScope.launch {
            uiAction.filterIsInstance<ICollectionViewModel.UiAction.Refresh>().collect {
                reload.emit(Unit)
            }
        }
        viewModelScope.launch {
            uiAction.filterIsInstance<ICollectionViewModel.UiAction.GetCollection>().collect(::onGetCollection)
        }

        viewModelScope.launch {
            uiAction.filterIsInstance<ICollectionViewModel.CollectionAction>().collect(::onCollectionAction)
        }

        viewModelScope.launch {
            uiAction.filterIsInstance<ICollectionViewModel.AccountAction>().throttleFirst().collect(::onAccountAction)
        }
    }

    private suspend fun onCollectionAction(action: ICollectionViewModel.CollectionAction) {
    }

    private fun makePrimaryAction(pachliAccountId: Long, collection: Collection, account: Account, isSelf: Boolean, relationship: Relationship?): ICollectionViewModel.AccountAction? {
        relationship ?: return null

        if (isSelf) {
            return ICollectionViewModel.AccountAction.Revoke(
                pachliAccountId = pachliAccountId,
                account = account,
                collection = collection,
            )
        }

        if (relationship.blocking) {
            return ICollectionViewModel.AccountAction.UnblockAccount(
                pachliAccountId = pachliAccountId,
                account = account,
            )
        }

        if (relationship.blockingDomain) {
            return ICollectionViewModel.AccountAction.UnblockDomain(
                pachliAccountId = pachliAccountId,
                account = account,
            )
        }

        if (relationship.muting) {
            return ICollectionViewModel.AccountAction.UnmuteAccount(
                pachliAccountId = pachliAccountId,
                account = account,
            )
        }

        return when (relationship.followState) {
            Relationship.FollowState.NOT_FOLLOWING -> ICollectionViewModel.AccountAction.FollowAccount(
                pachliAccountId = pachliAccountId,
                account = account,
            )

            Relationship.FollowState.FOLLOWING -> ICollectionViewModel.AccountAction.UnfollowAccount(
                pachliAccountId = pachliAccountId,
                account = account,
            )

            Relationship.FollowState.REQUESTED -> ICollectionViewModel.AccountAction.CancelFollowRequest(
                pachliAccountId = pachliAccountId,
                account = account,
            )
        }
    }

    /**
     * Gets the most recent collection and relationship data, updating [collectionAndOwner]
     * and [relationships].
     */
    private suspend fun onGetCollection(action: ICollectionViewModel.UiAction.GetCollection) = operationCounter {
        pachliAccountId.emit(action.pachliAccountId)
        collectionId.emit(action.collectionId)
    }

    private suspend fun onAccountAction(action: ICollectionViewModel.AccountAction) {
        disabledAccountIds.update { it + action.account.serverId }

        val result = when (action) {
            is ICollectionViewModel.AccountAction.FollowAccount -> onFollowAccount(action)
            is ICollectionViewModel.AccountAction.UnfollowAccount -> onUnfollowAccount(action)
            is ICollectionViewModel.AccountAction.CancelFollowRequest -> TODO()
            is ICollectionViewModel.AccountAction.UnblockAccount -> TODO()
            is ICollectionViewModel.AccountAction.UnblockDomain -> TODO()
            is ICollectionViewModel.AccountAction.UnmuteAccount -> TODO()
            is ICollectionViewModel.AccountAction.Revoke -> onRevokeCollection(action)
        }.onSuccess { relationship ->
            relationship?.let { relationships.update { it + (action.account.serverId to relationship) } }
        }.mapEither(
            { ICollectionViewModel.UiSuccess.Companion.from(action) },
            { ICollectionViewModel.UiError.Companion.make(it, action) },
        )

        _uiResult.send(result)
        disabledAccountIds.update { it - action.account.serverId }
    }

    private suspend fun onFollowAccount(action: ICollectionViewModel.AccountAction.FollowAccount) = operationCounter {
        followAccountUseCase(action.pachliAccountId, action.account.serverId)
            .mapError { ICollectionViewModel.UiError.FollowAccount(action, it) }
    }

    private suspend fun onUnfollowAccount(action: ICollectionViewModel.AccountAction.UnfollowAccount) = operationCounter {
        unfollowAccountUseCase(action.pachliAccountId, action.account.serverId)
            .mapError { ICollectionViewModel.UiError.UnfollowAccount(action, it) }
    }

    private suspend fun onRevokeCollection(action: ICollectionViewModel.AccountAction.Revoke) = operationCounter {
        collectionsRepository.revokeFromCollection(action.pachliAccountId, action.collection.serverId, action.account.serverId)
//            .onSuccess {
//            collection.update {
//                it.map { loadable ->
//                    loadable.mapLoaded { (collection, accounts) ->
//                        Pair(collection, accounts.filterNot { it.serverId == action.account.serverId })
//                    }
//                }
//            }
//        }
            .mapEither(
                // Other account actions return a Relationship. We can't here, so return null
                // cast to the correct value, so the return type of this method matches the
                // other on... methods that operate on AccountAction.
                { null as Relationship? },
                { ICollectionViewModel.UiError.Companion.make(it, action) },
            )
    }
}
