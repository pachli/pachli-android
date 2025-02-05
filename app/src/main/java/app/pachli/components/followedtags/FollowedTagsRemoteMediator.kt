package app.pachli.components.followedtags

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.pachli.core.network.model.HashTag
import app.pachli.core.network.model.HttpHeaderLink
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.network.retrofit.apiresult.ApiResult
import com.github.michaelbull.result.getOrElse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@OptIn(ExperimentalPagingApi::class)
class FollowedTagsRemoteMediator(
    private val api: MastodonApi,
    private val viewModel: FollowedTagsViewModel,
) : RemoteMediator<String, String>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, String>,
    ): MediatorResult {
        return try {
            val response = request(loadType)
                ?: return MediatorResult.Success(endOfPaginationReached = true)

            return applyResponse(response)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            MediatorResult.Error(e)
        }
    }

    private suspend fun request(loadType: LoadType): ApiResult<List<HashTag>>? {
        return when (loadType) {
            LoadType.PREPEND -> null
            LoadType.APPEND -> api.followedTags(maxId = viewModel.nextKey)
            LoadType.REFRESH -> {
                viewModel.nextKey = null
                viewModel.tags.clear()
                api.followedTags()
            }
        }
    }

    private fun applyResponse(result: ApiResult<List<HashTag>>): MediatorResult {
        val response = result.getOrElse {
            return MediatorResult.Error(it.throwable)
        }
        val tags = response.body

        val links = HttpHeaderLink.parse(response.headers["Link"])
        viewModel.nextKey = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter("max_id")
        viewModel.tags.addAll(tags)
        viewModel.currentSource?.invalidate()

        return MediatorResult.Success(endOfPaginationReached = viewModel.nextKey == null)
    }
}
