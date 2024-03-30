package app.pachli.appstore

import app.pachli.core.model.Timeline
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status

data class FavoriteEvent(val statusId: String, val favourite: Boolean) : Event
data class ReblogEvent(val statusId: String, val reblog: Boolean) : Event
data class BookmarkEvent(val statusId: String, val bookmark: Boolean) : Event
data class MuteConversationEvent(val statusId: String, val mute: Boolean) : Event
data class UnfollowEvent(val accountId: String) : Event
data class BlockEvent(val accountId: String) : Event
data class MuteEvent(val accountId: String) : Event
data class StatusDeletedEvent(val statusId: String) : Event

/** A status the user wrote was successfully sent */
// TODO: Rename, calling it "Composed" does not imply anything about the sent state
data class StatusComposedEvent(val status: Status) : Event
data object StatusScheduledEvent : Event
data class StatusEditedEvent(val originalId: String, val status: Status) : Event
data class ProfileEditedEvent(val newProfileData: Account) : Event
data class FilterChangedEvent(val filterContext: FilterContext) : Event
data class MainTabsChangedEvent(val newTabs: List<Timeline>) : Event
data class PollVoteEvent(val statusId: String, val poll: Poll) : Event
data class DomainMuteEvent(val instance: String) : Event
data class AnnouncementReadEvent(val announcementId: String) : Event
data class PinEvent(val statusId: String, val pinned: Boolean) : Event
