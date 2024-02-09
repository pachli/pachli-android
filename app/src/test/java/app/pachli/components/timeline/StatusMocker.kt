package app.pachli.components.timeline

import app.pachli.core.database.model.StatusViewDataEntity
import app.pachli.core.database.model.TimelineAccountEntity
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.database.model.TimelineStatusWithAccount
import app.pachli.core.database.model.TranslationState
import app.pachli.core.network.json.GuardedAdapter.Companion.GuardedAdapterFactory
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.TimelineAccount
import app.pachli.viewdata.StatusViewData
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import java.util.Date

private val fixedDate = Date(1638889052000)

fun mockStatus(
    id: String = "100",
    inReplyToId: String? = null,
    inReplyToAccountId: String? = null,
    spoilerText: String = "",
    reblogged: Boolean = false,
    favourited: Boolean = true,
    bookmarked: Boolean = true,
) = Status(
    id = id,
    url = "https://mastodon.example/@ConnyDuck/$id",
    account = TimelineAccount(
        id = "1",
        localUsername = "connyduck",
        username = "connyduck@mastodon.example",
        displayName = "Conny Duck",
        note = "This is their bio",
        url = "https://mastodon.example/@ConnyDuck",
        avatar = "https://mastodon.example/system/accounts/avatars/000/150/486/original/ab27d7ddd18a10ea.jpg",
    ),
    inReplyToId = inReplyToId,
    inReplyToAccountId = inReplyToAccountId,
    reblog = null,
    content = "Test",
    createdAt = fixedDate,
    editedAt = null,
    emojis = emptyList(),
    reblogsCount = 1,
    favouritesCount = 2,
    repliesCount = 3,
    reblogged = reblogged,
    favourited = favourited,
    bookmarked = bookmarked,
    sensitive = true,
    spoilerText = spoilerText,
    visibility = Status.Visibility.PUBLIC,
    attachments = ArrayList(),
    mentions = emptyList(),
    tags = emptyList(),
    application = Status.Application("Pachli", "https://pachli.app"),
    pinned = false,
    muted = false,
    poll = null,
    card = null,
    language = null,
    filtered = null,
)

fun mockStatusViewData(
    id: String = "100",
    inReplyToId: String? = null,
    inReplyToAccountId: String? = null,
    isDetailed: Boolean = false,
    spoilerText: String = "",
    isExpanded: Boolean = false,
    isShowingContent: Boolean = false,
    isCollapsed: Boolean = !isDetailed,
    reblogged: Boolean = false,
    favourited: Boolean = true,
    bookmarked: Boolean = true,
) = StatusViewData(
    status = mockStatus(
        id = id,
        inReplyToId = inReplyToId,
        inReplyToAccountId = inReplyToAccountId,
        spoilerText = spoilerText,
        reblogged = reblogged,
        favourited = favourited,
        bookmarked = bookmarked,
    ),
    isExpanded = isExpanded,
    isShowingContent = isShowingContent,
    isCollapsed = isCollapsed,
    isDetailed = isDetailed,
    translationState = TranslationState.SHOW_ORIGINAL,
)

fun mockStatusEntityWithAccount(
    id: String = "100",
    userId: Long = 1,
    expanded: Boolean = false,
): TimelineStatusWithAccount {
    val mockedStatus = mockStatus(id)
    val moshi = Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .add(GuardedAdapterFactory())
        .build()

    return TimelineStatusWithAccount(
        status = TimelineStatusEntity.from(
            mockedStatus,
            timelineUserId = userId,
            moshi = moshi,
        ),
        account = TimelineAccountEntity.from(
            mockedStatus.account,
            accountId = userId,
            moshi = moshi,
        ),
        viewData = StatusViewDataEntity(
            serverId = id,
            timelineUserId = userId,
            expanded = expanded,
            contentShowing = false,
            contentCollapsed = true,
            translationState = TranslationState.SHOW_ORIGINAL,
        ),
    )
}
