package app.pachli.appstore

import app.pachli.TabData
import app.pachli.entity.Account
import app.pachli.entity.Poll
import app.pachli.entity.Status

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
data class StatusScheduledEvent(val status: Status) : Event
data class StatusEditedEvent(val originalId: String, val status: Status) : Event
data class ProfileEditedEvent(val newProfileData: Account) : Event
//data class PreferenceChangedEvent(val preferenceKey: String) : Event
// context is a toString of a Filter.Kind enum value
// Or a FilterV1.HOME, FilterV1.NOTIFICATIONS, FilterV1... value
// That overlap should be fixed, see
// - NotificationsViewModel
// - TimelineViewModel
data class FilterChangedEvent(val context: String) : Event
data class MainTabsChangedEvent(val newTabs: List<TabData>) : Event
data class PollVoteEvent(val statusId: String, val poll: Poll) : Event
data class DomainMuteEvent(val instance: String) : Event
data class AnnouncementReadEvent(val announcementId: String) : Event
data class PinEvent(val statusId: String, val pinned: Boolean) : Event
