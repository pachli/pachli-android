package app.pachli.core.preferences

/**
 * Current preferences schema version. Format is 4-digit year + 2 digit month (zero padded) + 2
 * digit day (zero padded) + 2 digit counter (zero padded).
 *
 * If you make an incompatible change to the preferences schema you must:
 *
 * - Update this value
 * - Update the code in
 *   [PachliApplication.upgradeSharedPreferences][app.pachli.PachliApplication.upgradeSharedPreferences]
 *   to migrate from the old schema version to the new schema version.
 *
 * An incompatible change is:
 *
 * - Deleting a preference. The migration should delete the old preference.
 * - Changing a preference's default value (e.g., from true to false, or from one enum value to
 *   another). The migration should check to see if the user had set an explicit value for
 *   that preference ([SharedPreferences.contains][android.content.SharedPreferences.contains]);
 *   if they hadn't then the migration should set the *old* default value as the preference's
 *   value, so the app behaviour does not unexpectedly change.
 * - Changing a preference's type (e.g,. from a boolean to an enum). If you do this you may want
 *   to give the preference a different name, but you still need to migrate the user's previous
 *   preference value to the new preference.
 * - Renaming a preference key. The migration should copy the user's previous value for the
 *   preference under the old key to the value for the new, and delete the old preference.
 * - Deleting an option from an enum preference.
 *
 * A compatible change is:
 *
 * - Adding a new preference that does not change the interpretation of an existing preference
 */
const val SCHEMA_VERSION = 2025033001

/** The schema version for fresh installs */
const val NEW_INSTALL_SCHEMA_VERSION = 0

object PrefKeys {
    // Note: not all of these keys are actually used as SharedPreferences keys but we must give
    // each preference a key for it to work.

    const val SCHEMA_VERSION: String = "schema_version"
    const val APP_THEME = "appTheme"
    const val FAB_HIDE = "fabHide"
    const val LANGUAGE = "language"
    const val FONT_FAMILY = "fontFamily"
    const val STATUS_TEXT_SIZE = "statusTextSize"
    const val MAIN_NAV_POSITION = "mainNavPosition"
    const val DEFAULT_AUDIO_PLAYBACK = "defaultAudioPlayback"
    const val HIDE_TOP_TOOLBAR = "hideTopToolbar"
    const val ABSOLUTE_TIME_VIEW = "absoluteTimeView"
    const val SHOW_BOT_OVERLAY = "showBotOverlay"
    const val ANIMATE_GIF_AVATARS = "animateGifAvatars"
    const val USE_BLURHASH = "useBlurhash"
    const val SHOW_SELF_USERNAME = "showSelfUsername"
    const val SHOW_CARDS_IN_TIMELINES = "showCardsInTimelines"
    const val CONFIRM_REBLOGS = "confirmReblogs"
    const val CONFIRM_FAVOURITES = "confirmFavourites"
    const val ENABLE_SWIPE_FOR_TABS = "enableSwipeForTabs"
    const val ANIMATE_CUSTOM_EMOJIS = "animateCustomEmojis"
    const val SHOW_STATS_INLINE = "showStatsInline"

    const val CUSTOM_TABS = "customTabs"

    /**
     * True if post stats (# boosts, # favourites) are hidden in a
     * status' detailed view. False to show them.
     */
    const val WELLBEING_HIDE_STATS_POSTS = "wellbeingHideStatsPosts"

    /**
     * True if the following should be hidden on profile pages:
     *
     * - Whether or not the account follows you
     * - # statuses posted by this account
     * - # followers this account has
     * - # accounts this account is following
     */
    const val WELLBEING_HIDE_STATS_PROFILE = "wellbeingHideStatsProfile"

    const val HTTP_PROXY_ENABLED = "httpProxyEnabled"
    const val HTTP_PROXY_SERVER = "httpProxyServer"
    const val HTTP_PROXY_PORT = "httpProxyPort"

    // Server-side preference, only written to from AccountPreferenceFragment.
    const val DEFAULT_POST_PRIVACY = "defaultPostPrivacy"

    // Server-side preference, only written to from AccountPreferenceFragment.
    const val DEFAULT_POST_LANGUAGE = "defaultPostLanguage"

    // Server-side preference, only written to from AccountPreferenceFragment.
    const val DEFAULT_MEDIA_SENSITIVITY = "defaultMediaSensitivity"

    // Account preference
    const val MEDIA_PREVIEW_ENABLED = "mediaPreviewEnabled"

    // Account preference
    const val ALWAYS_SHOW_SENSITIVE_MEDIA = "alwaysShowSensitiveMedia"

    // Account preference
    const val ALWAYS_OPEN_SPOILER = "alwaysOpenSpoiler"

    // Account preference
    const val NOTIFICATIONS_ENABLED = "notificationsEnabled"

    // Account preference
    const val NOTIFICATION_ALERT_LIGHT = "notificationAlertLight"

    // Account preference
    const val NOTIFICATION_ALERT_VIBRATE = "notificationAlertVibrate"

    // Account preference
    const val NOTIFICATION_ALERT_SOUND = "notificationAlertSound"

    // Account preference
    const val NOTIFICATION_FILTER_POLLS = "notificationFilterPolls"

    // Account preference
    const val NOTIFICATION_FILTER_FAVS = "notificationFilterFavourites"

    // Account preference
    const val NOTIFICATION_FILTER_REBLOGS = "notificationFilterReblogs"

    // Account preference
    const val NOTIFICATION_FILTER_FOLLOW_REQUESTS = "notificationFilterFollowRequests"

    // Account preference
    const val NOTIFICATIONS_FILTER_FOLLOWS = "notificationFilterFollows"

    // Account preference
    const val NOTIFICATION_FILTER_SUBSCRIPTIONS = "notificationFilterSubscriptions"

    // Account preference
    const val NOTIFICATION_FILTER_SIGN_UPS = "notificationFilterSignUps"

    // Account preference
    const val NOTIFICATION_FILTER_UPDATES = "notificationFilterUpdates"

    // Account preference
    const val NOTIFICATION_FILTER_REPORTS = "notificationFilterReports"

    // Account preferences
    const val NOTIFICATION_FILTER_MENTIONS = "notificationFilterMentions"

    // Account preferences
    const val NOTIFICATION_FILTER_SEVERED_RELATIONSHIPS = "notificationFilterSeveredRelationships"

    // Account preferences
    const val NOTIFICATION_FILTER_MODERATION_WARNING = "notificationFilterModerationWarning"

    const val TAB_FILTER_HOME_REPLIES = "tabFilterHomeReplies_v2" // This was changed once to reset an unintentionally set default.
    const val TAB_FILTER_HOME_BOOSTS = "tabFilterHomeBoosts"
    const val TAB_SHOW_HOME_SELF_BOOSTS = "tabShowHomeSelfBoosts"

    /** UI text scaling factor, stored as float, 100 = 100% = no scaling */
    const val UI_TEXT_SCALE_RATIO = "uiTextScaleRatio"

    const val UPDATE_NOTIFICATION_FREQUENCY = "updateNotificationFrequency"
    const val UPDATE_NOTIFICATION_VERSIONCODE = "updateNotificationVersioncode"
    const val UPDATE_NOTIFICATION_LAST_NOTIFICATION_MS = "updateNotificationLastNotificationMs"

    const val LAB_REVERSE_TIMELINE = "labReverseTimeline"

    /**
     * True if the user's previous choice of UnifiedPush distributor should be
     * used by default.
     *
     * Default is `true`.
     */
    const val USE_PREVIOUS_UNIFIED_PUSH_DISTRIBUTOR = "usePreviousUnifiedPushDistributor"

    const val DOWNLOAD_LOCATION = "downloadLocation"
    const val TAB_TAP_BEHAVIOUR = "tabTapBehaviour"

    /**
     * True if the status' language should be checked before posting and the user
     * should be prompted to confirm if it doesn't match the set language.
     */
    const val CONFIRM_STATUS_LANGUAGE = "confirmStatusLanguage"

    /** Tab alignment. See [TabAlignment]. */
    const val TAB_ALIGNMENT = "tabAlignment"

    /** Tab contents. See [TabContents]. */
    const val TAB_CONTENTS = "tabContents"

    /** True if experimental support for rendering markdown is enabled. */
    const val LAB_RENDER_MARKDOWN = "labRenderMarkdown"

    /** Keys that are no longer used (e.g., the preference has been removed */
    object Deprecated {
        const val WELLBEING_LIMITED_NOTIFICATIONS = "wellbeingModeLimitedNotifications"
    }
}
