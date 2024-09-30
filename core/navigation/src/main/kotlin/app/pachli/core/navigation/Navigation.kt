/*
 * Copyright 2023 Pachli Association
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

package app.pachli.core.navigation

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import app.pachli.core.database.model.DraftAttachment
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.Timeline
import app.pachli.core.navigation.LoginActivityIntent.LoginMode
import app.pachli.core.navigation.TimelineActivityIntent.Companion.bookmarks
import app.pachli.core.navigation.TimelineActivityIntent.Companion.conversations
import app.pachli.core.navigation.TimelineActivityIntent.Companion.favourites
import app.pachli.core.navigation.TimelineActivityIntent.Companion.hashtag
import app.pachli.core.navigation.TimelineActivityIntent.Companion.list
import app.pachli.core.navigation.TimelineActivityIntent.Companion.publicFederated
import app.pachli.core.navigation.TimelineActivityIntent.Companion.publicLocal
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.NewPoll
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.Status
import com.gaelmarhic.quadrant.QuadrantConstants
import kotlinx.parcelize.Parcelize

/**
 * @param context
 * @param accountId Server ID of the account to view
 * @see [app.pachli.components.account.AccountActivity]
 */
class AccountActivityIntent(context: Context, pachliAccountId: Long, accountId: String) : Intent() {
    init {
        setClassName(context, QuadrantConstants.ACCOUNT_ACTIVITY)
        this.pachliAccountId = pachliAccountId
        putExtra(EXTRA_KEY_ACCOUNT_ID, accountId)
    }

    companion object {
        private const val EXTRA_KEY_ACCOUNT_ID = "id"

        /** @return the account ID passed in this intent */
        fun getAccountId(intent: Intent) = intent.getStringExtra(EXTRA_KEY_ACCOUNT_ID)!!
    }
}

/**
 * @param context
 * @param kind The kind of accounts to show
 * @param id Optional ID. Sometimes an account ID, sometimes a status ID, and
 *     sometimes ignored. See [Kind] for details of how `id` is interpreted.
 * @see [app.pachli.components.accountlist.AccountListActivity]
 */
class AccountListActivityIntent(context: Context, pachliAccountId: Long, kind: Kind, id: String? = null) : Intent() {
    enum class Kind {
        /** Show the accounts the account with `id` is following */
        FOLLOWS,

        /** Show the accounts following the account with `id` */
        FOLLOWERS,

        /** Show the accounts the account with `id` is blocking */
        BLOCKS,

        /** Show the accounts the account with `id` is muting */
        MUTES,

        /** Show the logged in account's follow requests (`id` is ignored) */
        FOLLOW_REQUESTS,

        /** Show the accounts that reblogged the status with `id` */
        REBLOGGED,

        /** Show the accounts that favourited the status with `id` */
        FAVOURITED,
    }

    init {
        setClassName(context, QuadrantConstants.ACCOUNT_LIST_ACTIVITY)
        this.pachliAccountId = pachliAccountId
        putExtra(EXTRA_KIND, kind)
        putExtra(EXTRA_ID, id)
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ID = "id"

        /** @return The [Kind] passed in this intent */
        fun getKind(intent: Intent) = intent.getSerializableExtra(EXTRA_KIND) as Kind

        /** @return The ID passed in this intent, or null */
        fun getId(intent: Intent) = intent.getStringExtra(EXTRA_ID)
    }
}

/**
 * @param context
 * @see [app.pachli.components.compose.ComposeActivity]
 */
class ComposeActivityIntent(context: Context) : Intent() {
    @Parcelize
    data class ComposeOptions(
        val scheduledTootId: String? = null,
        val draftId: Int? = null,
        val content: String? = null,
        val mediaUrls: List<String>? = null,
        val mediaDescriptions: List<String>? = null,
        val mentionedUsernames: Set<String>? = null,
        val inReplyToId: String? = null,
        val replyVisibility: Status.Visibility? = null,
        val visibility: Status.Visibility? = null,
        val contentWarning: String? = null,
        val replyingStatusAuthor: String? = null,
        val replyingStatusContent: String? = null,
        val mediaAttachments: List<Attachment>? = null,
        val draftAttachments: List<DraftAttachment>? = null,
        val scheduledAt: String? = null,
        val sensitive: Boolean? = null,
        val poll: NewPoll? = null,
        val modifiedInitialState: Boolean? = null,
        val language: String? = null,
        val statusId: String? = null,
        val kind: ComposeKind? = null,
        val initialCursorPosition: InitialCursorPosition = InitialCursorPosition.END,
    ) : Parcelable {
        /**
         * Status' kind. This particularly affects how the status is handled if the user
         * backs out of the edit.
         */
        enum class ComposeKind {
            /** Status is new */
            NEW,

            /** Editing a posted status */
            EDIT_POSTED,

            /** Editing a status started as an existing draft */
            EDIT_DRAFT,

            /** Editing an existing scheduled status */
            EDIT_SCHEDULED,
        }

        /**
         * Initial position of the cursor in EditText when the compose button is clicked
         * in a hashtag timeline
         */
        enum class InitialCursorPosition {
            /** Position the cursor at the start of the line */
            START,

            /** Position the cursor at the end of the line */
            END,
        }
    }

    init {
        setClassName(context, QuadrantConstants.COMPOSE_ACTIVITY)
    }

    /**
     * @param context
     * @param options Configure the initial state of the activity
     * @see [app.pachli.components.compose.ComposeActivity]
     */
    constructor(context: Context, options: ComposeOptions) : this(context) {
        putExtra(EXTRA_COMPOSE_OPTIONS, options)
    }

    companion object {
        private const val EXTRA_COMPOSE_OPTIONS = "composeOptions"

        /** @return the [ComposeOptions] passed in this intent, or null */
        fun getOptions(intent: Intent) = IntentCompat.getParcelableExtra(intent, EXTRA_COMPOSE_OPTIONS, ComposeOptions::class.java)
    }
}

/**
 * Launch with an empty content filter to edit.
 *
 * @param context
 * @param pachliAccountId The account that will own the filter
 * @see [app.pachli.components.filters.EditContentFilterActivity]
 */
class EditContentFilterActivityIntent(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.EDIT_CONTENT_FILTER_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }

    companion object {
        private const val EXTRA_CONTENT_FILTER_TO_EDIT = "contentFilterToEdit"
        private const val EXTRA_CONTENT_FILTER_ID_TO_LOAD = "contentFilterIdToLoad"

        /**
         * Launch with [contentFilter] displayed, ready to edit.
         *
         * @param context
         * @param contentFilter Content filter to edit
         * @param accountId The account that owns the filter
         * @see [app.pachli.components.filters.EditContentFilterActivity]
         */
        fun edit(context: Context, accountId: Long, contentFilter: ContentFilter) = EditContentFilterActivityIntent(context, accountId).apply {
            putExtra(EXTRA_CONTENT_FILTER_TO_EDIT, contentFilter)
        }

        /**
         * Launch and load [filterId], display it ready to edit.
         *
         * @param context
         * @param accountId The account that owns the filter
         * @param filterId ID of the content filter to load
         * @see [app.pachli.components.filters.EditContentFilterActivity]
         */
        fun edit(context: Context, accountId: Long, filterId: String) = EditContentFilterActivityIntent(context, accountId).apply {
            putExtra(EXTRA_CONTENT_FILTER_ID_TO_LOAD, filterId)
        }

        /** @return the [ContentFilter] passed in this intent, or null */
        fun getContentFilter(intent: Intent) = IntentCompat.getParcelableExtra(intent, EXTRA_CONTENT_FILTER_TO_EDIT, ContentFilter::class.java)

        /** @return the content filter ID passed in this intent, or null */
        fun getContentFilterId(intent: Intent) = intent.getStringExtra(EXTRA_CONTENT_FILTER_ID_TO_LOAD)
    }
}

/**
 * @param context
 * @param loginMode See [LoginMode]
 * @see [app.pachli.feature.login.LoginActivity]
 */
class LoginActivityIntent(context: Context, loginMode: LoginMode = LoginMode.DEFAULT) : Intent() {
    /** How to log in */
    enum class LoginMode {
        DEFAULT,

        /** Already logged in, log in with an additional account */
        ADDITIONAL_LOGIN,

        /** Update the OAuth scope granted to the client */
        MIGRATION,
    }

    init {
        setClassName(context, QuadrantConstants.LOGIN_ACTIVITY)
        putExtra(EXTRA_LOGIN_MODE, loginMode)
    }

    companion object {
        private const val EXTRA_LOGIN_MODE = "loginMode"

        /** @return the `loginMode` passed to this intent */
        fun getLoginMode(intent: Intent) = intent.getSerializableExtra(EXTRA_LOGIN_MODE)!! as LoginMode
    }
}

private const val EXTRA_PACHLI_ACCOUNT_ID = "pachliAccountId"

var Intent.pachliAccountId: Long
    get() = getLongExtra(EXTRA_PACHLI_ACCOUNT_ID, -1L)
    set(value) {
        putExtra(EXTRA_PACHLI_ACCOUNT_ID, value)
        return
    }

class MainActivityIntent constructor(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.MAIN_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }

    companion object {
        private const val EXTRA_NOTIFICATION_TYPE = "notificationType"
        private const val EXTRA_COMPOSE_OPTIONS = "composeOptions"
        private const val EXTRA_NOTIFICATION_TAG = "notificationTag"
        private const val EXTRA_NOTIFICATION_ID = "notificationId"
        private const val EXTRA_REDIRECT_URL = "redirectUrl"
        private const val EXTRA_OPEN_DRAFTS = "openDrafts"

        fun hasComposeOptions(intent: Intent) = intent.hasExtra(EXTRA_COMPOSE_OPTIONS)
        fun hasNotificationType(intent: Intent) = intent.hasExtra(EXTRA_NOTIFICATION_TYPE)

        fun getPachliAccountId(intent: Intent) = intent.getLongExtra(EXTRA_PACHLI_ACCOUNT_ID, -1)
        fun getNotificationType(intent: Intent) = intent.getSerializableExtra(EXTRA_NOTIFICATION_TYPE) as Notification.Type
        fun getNotificationTag(intent: Intent) = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)
        fun getNotificationId(intent: Intent) = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        fun getRedirectUrl(intent: Intent) = intent.getStringExtra(EXTRA_REDIRECT_URL)
        fun getOpenDrafts(intent: Intent) = intent.getBooleanExtra(EXTRA_OPEN_DRAFTS, false)

        /**
         * Switches the active account to the provided accountId and then stays on MainActivity
         */
//        fun withAccount(context: Context, pachliAccountId: Long) = MainActivityIntent(context).apply {
//            putExtra(EXTRA_PACHLI_ACCOUNT_ID, pachliAccountId)
//        }

        /**
         *
         */
        fun withShortCut(context: Context, pachliAccountId: Long) = MainActivityIntent(context, pachliAccountId).apply {
            action = ACTION_SEND
            type = "text/plain"
            putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, pachliAccountId.toString())
        }

        /**
         * Switches the active account to the accountId and takes the user to the correct place
         * according to the notification they clicked
         */
        fun openNotification(
            context: Context,
            pachliAccountId: Long,
            type: Notification.Type,
        ) = MainActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_NOTIFICATION_TYPE, type)
        }

        /**
         * Switches the active account to the accountId and then opens ComposeActivity with the provided options
         * @param pachliAccountId the id of the Pachli account to open the screen with.
         * @param notificationId optional id of the notification that should be cancelled when this intent is opened
         * @param notificationTag optional tag of the notification that should be cancelled when this intent is opened
         */
        fun openCompose(
            context: Context,
            options: ComposeActivityIntent.ComposeOptions,
            pachliAccountId: Long,
            notificationTag: String? = null,
            notificationId: Int = -1,
        ) = MainActivityIntent(context, pachliAccountId).apply {
            action = ACTION_SEND
            putExtra(EXTRA_COMPOSE_OPTIONS, options)
            putExtra(EXTRA_NOTIFICATION_TAG, notificationTag)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
        }

        /**
         * Switches the active account to [pachliAccountId] and then tries to resolve and
         * show the provided url
         */
        fun redirect(
            context: Context,
            pachliAccountId: Long,
            url: String,
        ) = MainActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_REDIRECT_URL, url)
            flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
        }

        /**
         * switches the active account to the provided accountId and then opens drafts
         */
        fun openDrafts(context: Context, pachliAccountId: Long) = MainActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_OPEN_DRAFTS, true)
        }
    }
}

/**
 * @param context
 * @param screen The preference screen to show
 * @see [app.pachli.components.preference.PreferencesActivity]
 */
class PreferencesActivityIntent(context: Context, pachliAccountId: Long, screen: PreferenceScreen) : Intent() {
    /** A specific preference screen */
    enum class PreferenceScreen {
        /** General preferences */
        GENERAL,

        /** Account-specific preferences */
        ACCOUNT,

        /** Notification preferences */
        NOTIFICATION,
    }
    init {
        setClassName(context, QuadrantConstants.PREFERENCES_ACTIVITY)
        this.pachliAccountId = pachliAccountId
        putExtra(EXTRA_PREFERENCE_SCREEN, screen)
    }

    companion object {
        private const val EXTRA_PREFERENCE_SCREEN = "preferenceScreen"

        /** @return the `screen` passed to this intent */
        fun getPreferenceType(intent: Intent) = intent.getSerializableExtra(EXTRA_PREFERENCE_SCREEN)!! as PreferenceScreen
    }
}

/**
 * @param context
 * @param accountId The ID of the account to report
 * @param userName The username of the account to report
 * @param statusId Optional ID of a status to include in the report
 * @see [app.pachli.components.report.ReportActivity]
 */
class ReportActivityIntent(context: Context, pachliAccountId: Long, accountId: String, userName: String, statusId: String? = null) : Intent() {
    init {
        setClassName(context, QuadrantConstants.REPORT_ACTIVITY)
        this.pachliAccountId = pachliAccountId
        putExtra(EXTRA_ACCOUNT_ID, accountId)
        putExtra(EXTRA_ACCOUNT_USERNAME, userName)
        putExtra(EXTRA_STATUS_ID, statusId)
    }

    companion object {
        private const val EXTRA_ACCOUNT_ID = "accountId"
        private const val EXTRA_ACCOUNT_USERNAME = "accountUsername"
        private const val EXTRA_STATUS_ID = "statusId"

        /** @return the `accountId` passed to this intent */
        fun getAccountId(intent: Intent) = intent.getStringExtra(EXTRA_ACCOUNT_ID)!!

        /** @return the `userName` passed to this intent */
        fun getAccountUserName(intent: Intent) = intent.getStringExtra(EXTRA_ACCOUNT_USERNAME)!!

        /** @return the `statusId` passed to this intent, or null */
        fun getStatusId(intent: Intent) = intent.getStringExtra(EXTRA_STATUS_ID)
    }
}

/**
 * Use one of [bookmarks], [conversations], [favourites], [hashtag], [list], [publicFederated],
 * or [publicLocal] to construct.
 */
class TimelineActivityIntent private constructor(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.TIMELINE_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }

    companion object {
        private const val EXTRA_TIMELINE = "timeline"

        /**
         * Show the user's bookmarks.
         *
         * @param context
         */
        fun bookmarks(context: Context, pachliAccountId: Long) = TimelineActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_TIMELINE, Timeline.Bookmarks)
        }

        /**
         * Show the user's conversations (direct messages).
         *
         * @param context
         */
        fun conversations(context: Context, pachliAccountId: Long) = TimelineActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_TIMELINE, Timeline.Conversations)
        }

        /**
         * Show the user's favourites.
         *
         * @param context
         */
        fun favourites(context: Context, pachliAccountId: Long) = TimelineActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_TIMELINE, Timeline.Favourites)
        }

        /**
         * Show statuses containing [hashtag].
         *
         * @param context
         * @param hashtag The hashtag to show, without the leading "`#`"
         */
        fun hashtag(context: Context, pachliAccountId: Long, hashtag: String) = TimelineActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_TIMELINE, Timeline.Hashtags(listOf(hashtag)))
        }

        /**
         * Show statuses from a list.
         *
         * @param context
         * @param listId ID of the list to show
         * @param title The title to display
         */
        fun list(context: Context, pachliAccountId: Long, listId: String, title: String) = TimelineActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_TIMELINE, Timeline.UserList(listId, title))
        }

        /**
         * Show statuses from the Public Federated feed
         *
         * @param context
         */
        fun publicFederated(context: Context, pachliAccountId: Long) = TimelineActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_TIMELINE, Timeline.PublicFederated)
        }

        /**
         * Show statuses from the Public Local feed
         *
         * @param context
         */
        fun publicLocal(context: Context, pachliAccountId: Long) = TimelineActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_TIMELINE, Timeline.PublicLocal)
        }

        /**
         * Show notifications timeline
         *
         * @param context
         */
        fun notifications(context: Context, pachliAccountId: Long) = TimelineActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_TIMELINE, Timeline.Notifications)
        }

        /** @return The [Timeline] to show */
        fun getTimeline(intent: Intent) = IntentCompat.getParcelableExtra(intent, EXTRA_TIMELINE, Timeline::class.java)!!
    }
}

class ViewMediaActivityIntent private constructor(context: Context, accountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.VIEW_MEDIA_ACTIVITY)
        pachliAccountId = accountId
    }

    /**
     * Show a collection of media attachments.
     *
     * @param context
     * @param accountId ID of the Pachli account viewing the media
     * @param owningUsername The username that owns the media. See
     * [SFragment.viewMedia][app.pachli.fragment.SFragment.viewMedia].
     * @param attachments The attachments to show
     * @param index The index of the attachment in [attachments] to focus on
     */
    constructor(context: Context, accountId: Long, owningUsername: String, attachments: List<AttachmentViewData>, index: Int) : this(context, accountId) {
        putExtra(EXTRA_OWNING_USERNAME, owningUsername)
        putParcelableArrayListExtra(EXTRA_ATTACHMENTS, ArrayList(attachments))
        putExtra(EXTRA_ATTACHMENT_INDEX, index)
    }

    /**
     * Show a single image identified by a URL
     *
     * @param context
     * @param accountId ID of the Pachli account viewing the media
     * @param owningUsername The username that owns the media. See
     * [SFragment.viewMedia][app.pachli.fragment.SFragment.viewMedia].
     * @param url The URL of the image
     */
    constructor(context: Context, accountId: Long, owningUsername: String, url: String) : this(context, accountId) {
        putExtra(EXTRA_OWNING_USERNAME, owningUsername)
        putExtra(EXTRA_SINGLE_IMAGE_URL, url)
    }

    companion object {
        private const val EXTRA_OWNING_USERNAME = "owningUsername"
        private const val EXTRA_ATTACHMENTS = "attachments"
        private const val EXTRA_ATTACHMENT_INDEX = "index"
        private const val EXTRA_SINGLE_IMAGE_URL = "singleImage"

        /** @return the owningUsername passed in this intent. */
        fun getOwningUsername(intent: Intent): String = intent.getStringExtra(EXTRA_OWNING_USERNAME)!!

        /** @return the list of [AttachmentViewData] passed in this intent, or null */
        fun getAttachments(intent: Intent): List<AttachmentViewData>? = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_ATTACHMENTS, AttachmentViewData::class.java)

        /** @return the index of the attachment to show, or 0 */
        fun getAttachmentIndex(intent: Intent) = intent.getIntExtra(EXTRA_ATTACHMENT_INDEX, 0)

        /** @return the URL of the single image to show, null if no URL was included */
        fun getImageUrl(intent: Intent) = intent.getStringExtra(EXTRA_SINGLE_IMAGE_URL)
    }
}

/**
 * @param context
 * @param accountId ID of the Pachli account viewing the thread
 * @param statusId ID of the status to start from (may be in the middle of the thread)
 * @param statusUrl Optional URL of the status in `statusId`
 * @see [app.pachli.components.viewthread.ViewThreadActivity]
 */
class ViewThreadActivityIntent(context: Context, accountId: Long, statusId: String, statusUrl: String? = null) : Intent() {
    init {
        setClassName(context, QuadrantConstants.VIEW_THREAD_ACTIVITY)
        pachliAccountId = accountId
        putExtra(EXTRA_STATUS_ID, statusId)
        putExtra(EXTRA_STATUS_URL, statusUrl)
    }

    companion object {
        private const val EXTRA_STATUS_ID = "id"
        private const val EXTRA_STATUS_URL = "url"

        /** @return the `statusId` passed to this intent */
        fun getStatusId(intent: Intent) = intent.getStringExtra(EXTRA_STATUS_ID)!!

        /** @return the `statusUrl` passed to this intent, or null */
        fun getUrl(intent: Intent) = intent.getStringExtra(EXTRA_STATUS_URL)
    }
}

class AboutActivityIntent(context: Context) : Intent() {
    init {
        setClassName(context, QuadrantConstants.ABOUT_ACTIVITY)
    }
}

class AnnouncementsActivityIntent(context: Context, accountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.ANNOUNCEMENTS_ACTIVITY)
        pachliAccountId = accountId
    }
}

class DraftsActivityIntent(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.DRAFTS_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }
}

class EditProfileActivityIntent(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.EDIT_PROFILE_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }
}

class ContentFiltersActivityIntent(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.CONTENT_FILTERS_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }
}

class FollowedTagsActivityIntent(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.FOLLOWED_TAGS_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }
}

class InstanceListActivityIntent(context: Context) : Intent() {
    init {
        setClassName(context, QuadrantConstants.INSTANCE_LIST_ACTIVITY)
    }
}

class ListActivityIntent constructor(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.LISTS_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }
}

class LoginWebViewActivityIntent(context: Context) : Intent() {
    init {
        setClassName(context, QuadrantConstants.LOGIN_WEB_VIEW_ACTIVITY)
    }
}

class ScheduledStatusActivityIntent(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.SCHEDULED_STATUS_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }
}

class SearchActivityIntent(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.SEARCH_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }
}

class SuggestionsActivityIntent(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.SUGGESTIONS_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }
}

class TabPreferenceActivityIntent(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.TAB_PREFERENCE_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }
}

class TrendingActivityIntent(context: Context, accountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.TRENDING_ACTIVITY)
        pachliAccountId = accountId
    }
}
