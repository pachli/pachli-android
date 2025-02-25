package app.pachli.components.conversation

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.dao.StatusDao
import app.pachli.core.database.dao.TimelineDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.ConversationData
import app.pachli.core.database.model.ConversationEntity
import app.pachli.core.database.model.StatusEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.network.model.HttpHeaderLink
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import timber.log.Timber

@OptIn(ExperimentalPagingApi::class)
class ConversationsRemoteMediator(
    private val pachliAccountId: Long,
    private val api: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val conversationsDao: ConversationsDao,
    private val statusDao: StatusDao,
    private val timelineDao: TimelineDao,
) : RemoteMediator<Int, ConversationData>() {

    private var nextKey: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ConversationData>,
    ): MediatorResult {
        if (loadType == LoadType.PREPEND) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        if (loadType == LoadType.REFRESH) {
            nextKey = null
        }

        try {
            val conversationsResponse = api.getConversations(maxId = nextKey, limit = state.config.pageSize)
                .getOrElse { return MediatorResult.Error(it.throwable) }

            val conversations = conversationsResponse.body.filterNot { it.lastStatus == null }

            if (conversations.isEmpty()) {
                return MediatorResult.Success(endOfPaginationReached = loadType != LoadType.REFRESH)
            }

            transactionProvider {
                if (loadType == LoadType.REFRESH) {
                    conversationsDao.deleteForAccount(pachliAccountId)
                }

                val linkHeader = conversationsResponse.headers["Link"]
                val links = HttpHeaderLink.parse(linkHeader)
                nextKey = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter("max_id")

                val accounts = mutableSetOf<TimelineAccount>()
                val conversationEntities = mutableSetOf<ConversationEntity>()
                val statuses = mutableSetOf<Status>()

                val conversationStarter = isConversationStarter(conversations.map { it.lastStatus!! })

                conversations.forEach {
                    val lastStatus = it.lastStatus!!
                    accounts.add(lastStatus.account)
                    lastStatus.reblog?.account?.let { accounts.add(it) }
                    statuses.add(lastStatus)
                    conversationEntities.add(
                        ConversationEntity.from(
                            it,
                            pachliAccountId,
                            conversationStarter[it.lastStatus!!.id] == true,
                        )!!,
                    )
                }

                timelineDao.upsertAccounts(accounts.map { TimelineAccountEntity.from(it, pachliAccountId) })
                statusDao.upsertStatuses(statuses.map { StatusEntity.from(it, pachliAccountId) })
                conversationsDao.upsert(conversationEntities)
            }

            return MediatorResult.Success(endOfPaginationReached = nextKey == null)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return MediatorResult.Error(e)
        }
    }

    /**
     * Determine if each status in [statuses] is the conversation starter for the
     * thread it belongs to.
     *
     * The conversation starter is either:
     *
     * - The first status in the thread, if the second status is a reply to that
     * by a different account. I.e., if the thread is {A, B, ...}, and A and B are by
     * different accounts, the conversation starter is A.
     * - The Nth status in the thread where all the previous statuses in the thread
     * are by the same account. I.e., the thread is {A, B, C, D, E} and {A, B, C} are
     * by the same account then the conversation starter is C.
     *
     * Conversations are messages with `direct` visibility. So if the thread is
     * {A, B, C, D, E}, and {A, B, C} are public and D is the first `direct` status
     * and D and E are written by different accounts then D is the conversation starter.
     *
     * @param statuses List of statuses to check.
     * @return Mapping of statusIDs to Boolean, true if the status ID is the
     * conversation starter, false otherwise.
     */
    private suspend fun isConversationStarter(statuses: List<Status>): Map<String, Boolean> {
        /**
         * Statuses that will need to be checked remotely.
         *
         * Maps from the status ID to check (which is a parent status) and its
         * child status ID, which will be an ID of one of [statuses].
         */
        val statusesToCheck = mutableMapOf<String, String>()
        val result = mutableMapOf<String, Boolean>()

        // Start by processing all the statuses where there is enough local data
        // to make a decision. Where there isn't enough data collect the IDs of
        // the statuses to check and their children in `statusesToCheck`.
        for (status in statuses) {
            // If inReplyToId is null then this is the first message in the thread.
            if (status.inReplyToId == null) {
                result[status.id] = true
                continue
            }

            // If the account posting this status is also the account that posted
            // the parent status hen this is part of chain of statuses that started
            // the thread, all posted by the same account.  It doesn't matter that
            // it's multiple statuses, the whole chain is considered to have started
            // the thread.
            if (status.account.id == status.inReplyToAccountId) {
                result[status.id] = true
                continue
            }

            // Not enough information in lastStatus to make a decision, record this
            // as a status to check.
            statusesToCheck[status.inReplyToId!!] = status.id
        }

        val statusIdsToCheck = statusesToCheck.keys.toList()
        if (statusIdsToCheck.isNotEmpty()) {
            api.statuses(statusIdsToCheck).onSuccess {
                it.body.forEach { parentStatus ->
                    val childStatusId = statusesToCheck[parentStatus.id]
                    result[childStatusId!!] = parentStatus.visibility != Status.Visibility.DIRECT
                }
            }.onFailure {
                Timber.e("Failed: $it")
            }
        }

        return result
    }
}
