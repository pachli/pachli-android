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
import app.pachli.core.database.model.DraftAttachment
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.Timeline
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.LoginActivityIntent.LoginMode
import app.pachli.core.navigation.TimelineActivityIntent.Companion.bookmarks
import app.pachli.core.navigation.TimelineActivityIntent.Companion.conversations
import app.pachli.core.navigation.TimelineActivityIntent.Companion.favourites
import app.pachli.core.navigation.TimelineActivityIntent.Companion.hashtag
import app.pachli.core.navigation.TimelineActivityIntent.Companion.list
import app.pachli.core.navigation.TimelineActivityIntent.Companion.publicFederated
import app.pachli.core.navigation.TimelineActivityIntent.Companion.publicLocal
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.model.NewPoll
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.Status
import app.pachli.core.network.parseAsMastodonHtml
import com.gaelmarhic.quadrant.QuadrantConstants
import java.util.Date
import kotlinx.parcelize.Parcelize

private const val EXTRA_PACHLI_ACCOUNT_ID = "app.pachli.EXTRA_PACHLI_ACCOUNT_ID"
const val PACHLI_ACCOUNT_ID_ACTIVE = -1L

/**
 * The Pachli Account ID passed to this intent. This is the
 * [id][app.pachli.core.database.model.AccountEntity.id] of the account that is
 * "active" for the purposes of this activity.
 */
var Intent.pachliAccountId: Long
    get() = getLongExtra(EXTRA_PACHLI_ACCOUNT_ID, PACHLI_ACCOUNT_ID_ACTIVE)
    set(value) {
        putExtra(EXTRA_PACHLI_ACCOUNT_ID, value)
        return
    }

/**
 * @param context
 * @param pachliAccountId See [pachliAccountId][Intent.pachliAccountId].
 * @param accountId Server ID of the account to view.
 * @see [app.pachli.components.account.AccountActivity]
 */
class AccountActivityIntent(context: Context, pachliAccountId: Long, accountId: String) : Intent() {
    init {
        setClassName(context, QuadrantConstants.ACCOUNT_ACTIVITY)
        this.pachliAccountId = pachliAccountId
        putExtra(EXTRA_ACCOUNT_ID, accountId)
    }

    companion object {
        private const val EXTRA_ACCOUNT_ID = "app.pachli.EXTRA_KEY_ACCOUNT_ID"

        /** @return the account ID passed in this intent */
        fun getAccountId(intent: Intent) = intent.getStringExtra(EXTRA_ACCOUNT_ID)!!
    }
}

/**
 * @param context
 * @param pachliAccountId
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
        private const val EXTRA_KIND = "app.pachli.EXTRA_KIND"
        private const val EXTRA_ID = "app.pachli.EXTRA_ID"

        /** @return The [Kind] passed in this intent */
        fun getKind(intent: Intent) = intent.getSerializableExtra(EXTRA_KIND) as Kind

        /** @return The ID passed in this intent, or null */
        fun getId(intent: Intent) = intent.getStringExtra(EXTRA_ID)
    }
}

/**
 * @param context
 * @param pachliAccountId
 * @param composeOptions
 * @see [app.pachli.components.compose.ComposeActivity]
 */
class ComposeActivityIntent(context: Context, pachliAccountId: Long, composeOptions: ComposeOptions? = null) : Intent() {
    @Parcelize
    data class ComposeOptions(
        val scheduledTootId: String? = null,
        val draftId: Int? = null,
        val content: String? = null,
        val mediaUrls: List<String>? = null,
        val mediaDescriptions: List<String>? = null,
        val mentionedUsernames: Set<String>? = null,
        val replyVisibility: Status.Visibility? = null,
        val visibility: Status.Visibility? = null,
        val contentWarning: String? = null,
        val inReplyTo: InReplyTo? = null,
        val mediaAttachments: List<Attachment>? = null,
        val draftAttachments: List<DraftAttachment>? = null,
        val scheduledAt: Date? = null,
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

        /** Composing a reply to an existing status. */
        @Parcelize
        sealed class InReplyTo : Parcelable {
            abstract val statusId: String

            /**
             * Holds the ID of the status being replied to.
             *
             * Use when the caller only has the ID, and needs ComposeActivity to
             * fetch the contents of the in-reply-to status.
             */
            data class Id(override val statusId: String) : InReplyTo()

            /**
             * Holds the content of the status being replied to.
             *
             * Use when the caller already has the in-reply-to status content which
             * can be reused without a network round trip.
             */
            data class Status(
                override val statusId: String,
                val avatarUrl: String,
                val isBot: Boolean,
                val displayName: String,
                val username: String,
                val emojis: List<Emoji>?,
                val contentWarning: String,
                val content: String,
            ) : InReplyTo() {
                companion object {
                    fun from(status: app.pachli.core.network.model.Status) = Status(
                        statusId = status.id,
                        avatarUrl = status.account.avatar,
                        isBot = status.account.bot,
                        displayName = status.account.name,
                        username = status.account.localUsername,
                        emojis = status.emojis,
                        contentWarning = status.spoilerText,
                        content = status.content.parseAsMastodonHtml().toString(),
                    )
                }
            }
        }
    }

    init {
        setClassName(context, QuadrantConstants.COMPOSE_ACTIVITY)
        this.pachliAccountId = pachliAccountId

        composeOptions?.let { putExtra(EXTRA_COMPOSE_OPTIONS, it) }
    }

    companion object {
        private const val EXTRA_COMPOSE_OPTIONS = "app.pachli.EXTRA_COMPOSE_OPTIONS"

        /** @return the [ComposeOptions] passed in this intent, or null */
        fun getComposeOptions(intent: Intent) = IntentCompat.getParcelableExtra(intent, EXTRA_COMPOSE_OPTIONS, ComposeOptions::class.java)
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
        private const val EXTRA_CONTENT_FILTER_TO_EDIT = "app.pachli.EXTRA_CONTENT_FILTER_TO_EDIT"
        private const val EXTRA_CONTENT_FILTER_ID_TO_LOAD =
            "app.pachli.EXTRA_CONTENT_FILTER_ID_TO_LOAD"

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
         * Launch and load [contentFilterId], display it ready to edit.
         *
         * @param context
         * @param accountId The account that owns the filter
         * @param contentFilterId ID of the content filter to load
         * @see [app.pachli.components.filters.EditContentFilterActivity]
         */
        fun edit(context: Context, accountId: Long, contentFilterId: String) =
            EditContentFilterActivityIntent(context, accountId).apply {
                putExtra(EXTRA_CONTENT_FILTER_ID_TO_LOAD, contentFilterId)
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
class LoginActivityIntent(context: Context, loginMode: LoginMode = LoginMode.Default) : Intent() {
    /** How to log in */
    sealed interface LoginMode : Parcelable {
        @Parcelize
        data object Default : LoginMode

        /** Already logged in, log in with an additional account */
        @Parcelize
        data object AdditionalLogin : LoginMode

        /** Allow the user to reauthenticate the account (at [domain]). */
        @Parcelize
        data class Reauthenticate(val domain: String) : LoginMode
    }

    init {
        setClassName(context, QuadrantConstants.LOGIN_ACTIVITY)
        putExtra(EXTRA_LOGIN_MODE, loginMode)
    }

    companion object {
        private const val EXTRA_LOGIN_MODE = "app.pachli.EXTRA_LOGIN_MODE"

        /** @return the `loginMode` passed to this intent */
        fun getLoginMode(intent: Intent) = IntentCompat.getParcelableExtra(intent, EXTRA_LOGIN_MODE, LoginMode::class.java)
    }
}

class MainActivityIntent(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.MAIN_ACTIVITY)
        this.pachliAccountId = pachliAccountId
    }

    /** Describes where the activity was started from. */
    sealed interface Payload : Parcelable {
        /**
         * Started from the "Compose post" quick tile. This cannot include
         * information about the account to start with. See
         * [PachliTileService][app.pachli.service.PachliTileService].
         */
        @Parcelize
        data object QuickTile : Payload

        /**
         * Started from the "Compose" button in a notification.
         *
         * @param composeOptions
         * @param notificationId Notification's ID
         * @param notificationTag Notification's tag (Mastodon notification ID)
         */
        @Parcelize
        data class NotificationCompose(
            val composeOptions: ComposeOptions,
            val notificationId: Int,
            val notificationTag: String?,
        ) : Payload

        /**
         * Started from a shortcut.
         */
        @Parcelize
        data object Shortcut : Payload

        /**
         * Started by tapping on a notification body.
         *
         * @param notificationId Notification's ID
         * @param notificationTag Notification's tag (Mastodon notification ID)
         * @param notificationType Notification's type
         */
        @Parcelize
        data class Notification(
            val notificationId: Int,
            val notificationTag: String?,
            val notificationType: app.pachli.core.network.model.Notification.Type,
        ) : Payload

        /**
         * Started to open drafts (e.g., after a post failed to send).
         */
        @Parcelize
        data object OpenDrafts : Payload

        /**
         * Started to redirect to [url].
         */
        @Parcelize
        data class Redirect(val url: String) : Payload
    }

    companion object {
        private const val EXTRA_PAYLOAD = "app.pachli.EXTRA_PAYLOAD"

        // Shortcuts use PersistableBundles which can't serialize a Payload. This
        // extra is used as a marker that the payload is a shortcut.
        private const val EXTRA_PAYLOAD_SHORTCUT = "app.pachli.EXTRA_PAYLOAD_SHORTCUT"

        /** @return The [Payload] in [intent]. May be null if the intent has no payload. */
        fun payload(intent: Intent): Payload? {
            val payload = IntentCompat.getParcelableExtra(
                intent,
                EXTRA_PAYLOAD,
                Payload::class.java,
            )

            if (payload != null) return payload

            if (intent.getBooleanExtra(EXTRA_PAYLOAD_SHORTCUT, false)) return Payload.Shortcut

            return null
        }

        /**
         * Open MainActivity from a tap on a quick tile. There is a single quick tile
         * irrespective of how many accounts are logged in, so use -1L as the account ID.
         */
        fun fromQuickTile(context: Context) = MainActivityIntent(context, -1L).apply {
            putExtra(EXTRA_PAYLOAD, Payload.QuickTile).apply {
                flags = FLAG_ACTIVITY_NEW_TASK
            }
        }

        /**
         * Open MainActivity from a tap on a shortcut.
         */
        fun fromShortcut(context: Context, pachliAccountId: Long) = MainActivityIntent(context, pachliAccountId).apply {
            action = ACTION_MAIN
            putExtra(EXTRA_PAYLOAD_SHORTCUT, true)
        }

        /**
         * Open MainActivity from a notification's "Compose" button.
         *
         * @param context
         * @param pachliAccountId
         * @param composeOptions
         * @param notificationId Notification's ID
         * @param notificationTag Notification's tag (Mastodon notification ID)
         */
        fun fromNotificationCompose(
            context: Context,
            pachliAccountId: Long,
            composeOptions: ComposeOptions,
            notificationId: Int,
            notificationTag: String,
        ) =
            MainActivityIntent(context, pachliAccountId).apply {
                putExtra(
                    EXTRA_PAYLOAD,
                    Payload.NotificationCompose(
                        composeOptions,
                        notificationId,
                        notificationTag,
                    ),
                )
            }

        /**
         * Switches the active account to [pachliAccountId] and takes the user to the correct place
         * according to the notification they clicked.
         *
         * @param context
         * @param pachliAccountId
         * @param notificationId Notification's ID. May be -1 if this is from a summary
         * notification.
         * @param notificationTag Notification's tag (Mastodon notification ID). May be null
         * if this is from a summary notification.
         * @param type
         */
        fun fromNotification(
            context: Context,
            pachliAccountId: Long,
            notificationId: Int,
            notificationTag: String?,
            type: Notification.Type,
        ) = MainActivityIntent(context, pachliAccountId).apply {
            putExtra(
                EXTRA_PAYLOAD,
                Payload.Notification(
                    notificationId,
                    notificationTag,
                    type,
                ),
            )
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
            putExtra(EXTRA_PAYLOAD, Payload.Redirect(url))
            flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
        }

        /**
         * Switches the active account to the provided accountId and then opens drafts
         */
        fun fromDraftsNotification(context: Context, pachliAccountId: Long) = MainActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_PAYLOAD, Payload.OpenDrafts)
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
        private const val EXTRA_PREFERENCE_SCREEN = "app.pachli.EXTRA_PREFERENCE_SCREEN"

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
        private const val EXTRA_ACCOUNT_ID = "app.pachli.EXTRA_ACCOUNT_ID"
        private const val EXTRA_ACCOUNT_USERNAME = "app.pachli.EXTRA_ACCOUNT_USERNAME"
        private const val EXTRA_STATUS_ID = "app.pachli.EXTRA_STATUS_ID"

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
        private const val EXTRA_TIMELINE = "app.pachli.EXTRA_TIMELINE"

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
         * Show statuses that reference a trending link.
         *
         * @param context
         * @param url URL of trending link.
         * @param title URL's title.
         */
        fun link(context: Context, pachliAccountId: Long, url: String, title: String) = TimelineActivityIntent(context, pachliAccountId).apply {
            putExtra(EXTRA_TIMELINE, Timeline.Link(url, title))
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
        private const val EXTRA_OWNING_USERNAME = "app.pachli.EXTRA_OWNING_USERNAME"
        private const val EXTRA_ATTACHMENTS = "app.pachli.EXTRA_ATTACHMENTS"
        private const val EXTRA_ATTACHMENT_INDEX = "app.pachli.EXTRA_ATTACHMENT_INDEX"
        private const val EXTRA_SINGLE_IMAGE_URL = "app.pachli.EXTRA_SINGLE_IMAGE_URL"

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
        private const val EXTRA_STATUS_ID = "app.pachli.EXTRA_STATUS_ID"
        private const val EXTRA_STATUS_URL = "app.pachli.EXTRA_STATUS_URL"

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

class AnnouncementsActivityIntent(context: Context, pachliAccountId: Long) : Intent() {
    init {
        setClassName(context, QuadrantConstants.ANNOUNCEMENTS_ACTIVITY)
        this.pachliAccountId = pachliAccountId
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

class ListsActivityIntent(context: Context, pachliAccountId: Long) : Intent() {
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
