package app.pachli.settings

import androidx.preference.PreferenceDataStore
import app.pachli.appstore.EventHub
import app.pachli.appstore.PreferenceChangedEvent
import app.pachli.db.AccountEntity
import app.pachli.db.AccountManager
import app.pachli.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class AccountPreferenceDataStore @Inject constructor(
    private val accountManager: AccountManager,
    private val eventHub: EventHub,
    @ApplicationScope private val externalScope: CoroutineScope,
) : PreferenceDataStore() {
    /**
     *  Flow of key/values that have been updated in the preferences.
     */
    val changes = MutableSharedFlow<Pair<String, Boolean>>()

    private val account: AccountEntity = accountManager.activeAccount!!

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return when (key) {
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> account.alwaysShowSensitiveMedia
            PrefKeys.ALWAYS_OPEN_SPOILER -> account.alwaysOpenSpoiler
            PrefKeys.MEDIA_PREVIEW_ENABLED -> account.mediaPreviewEnabled
            else -> defValue
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        when (key) {
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> account.alwaysShowSensitiveMedia = value
            PrefKeys.ALWAYS_OPEN_SPOILER -> account.alwaysOpenSpoiler = value
            PrefKeys.MEDIA_PREVIEW_ENABLED -> account.mediaPreviewEnabled = value
        }

        accountManager.saveAccount(account)

        externalScope.launch {
            changes.emit(Pair(key, value))
        }

        externalScope.launch {
            eventHub.dispatch(PreferenceChangedEvent(key))
        }
    }
}
