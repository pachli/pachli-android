package app.pachli.components.conversation

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.pachli.core.database.dao.ConversationsDao
import app.pachli.core.database.di.TransactionProvider
import app.pachli.core.database.model.ConversationEntity
import app.pachli.core.network.model.HttpHeaderLink
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.database.AccountManager
import retrofit2.HttpException

@OptIn(ExperimentalPagingApi::class)
class ConversationsRemoteMediator(
    private val api: MastodonApi,
    private val transactionProvider: TransactionProvider,
    private val conversationsDao: ConversationsDao,
    accountManager: AccountManager,
) : RemoteMediator<Int, ConversationEntity>() {

    private var nextKey: String? = null

    private var order: Int = 0

    private val activeAccount = accountManager.activeAccount!!

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ConversationEntity>,
    ): MediatorResult {
        if (loadType == LoadType.PREPEND) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        if (loadType == LoadType.REFRESH) {
            nextKey = null
            order = 0
        }

        try {
            val conversationsResponse = api.getConversations(maxId = nextKey, limit = state.config.pageSize)

            val conversations = conversationsResponse.body()
            if (!conversationsResponse.isSuccessful || conversations == null) {
                return MediatorResult.Error(HttpException(conversationsResponse))
            }

            transactionProvider {
                if (loadType == LoadType.REFRESH) {
                    conversationsDao.deleteForAccount(activeAccount.id)
                }

                val linkHeader = conversationsResponse.headers()["Link"]
                val links = HttpHeaderLink.parse(linkHeader)
                nextKey = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter("max_id")

                conversationsDao.insert(
                    conversations
                        .filterNot { it.lastStatus == null }
                        .map { conversation ->
                            ConversationEntity.from(
                                conversation,
                                accountId = activeAccount.id,
                                order = order++,
                                expanded = activeAccount.alwaysOpenSpoiler,
                                contentShowing = activeAccount.alwaysShowSensitiveMedia || !conversation.lastStatus!!.sensitive,
                                contentCollapsed = true,
                            )
                        },
                )
            }
            return MediatorResult.Success(endOfPaginationReached = nextKey == null)
        } catch (e: Exception) {
            return MediatorResult.Error(e)
        }
    }
}
