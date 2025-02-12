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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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

            val conversations = conversationsResponse.body

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

                conversations.filterNot { it.lastStatus == null }.forEach {
                    val lastStatus = it.lastStatus!!
                    accounts.add(lastStatus.account)
                    lastStatus.reblog?.account?.let { accounts.add(it) }
                    statuses.add(lastStatus)
                    conversationEntities.add(ConversationEntity.from(it, pachliAccountId)!!)
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
}
