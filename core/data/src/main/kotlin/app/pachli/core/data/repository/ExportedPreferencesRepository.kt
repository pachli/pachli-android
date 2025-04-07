/*
 * Copyright 2025 Pachli Association
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

package app.pachli.core.data.repository

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import app.pachli.core.common.PachliError
import app.pachli.core.data.R
import app.pachli.core.data.repository.Error.ExportError
import app.pachli.core.data.repository.Error.ImportError
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.model.FilterAction
import app.pachli.core.model.Timeline
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.preferences.SCHEMA_VERSION
import app.pachli.core.preferences.SharedPreferencesRepository
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import java.lang.reflect.Type
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.internal.toImmutableMap
import okio.buffer
import okio.sink
import okio.source

sealed interface Error : PachliError {
    sealed interface ExportError : Error {
        /** Generic exception occurred when exporting. */
        data class ExceptionError(val throwable: Throwable) : ExportError {
            override val resourceId: Int = app.pachli.core.network.R.string.error_generic_fmt
            override val formatArgs: Array<out Any> = arrayOf(throwable.localizedMessage ?: "")
            override val cause: PachliError? = null
        }
    }

    sealed interface ImportError : Error {
        /** Importing from JSON returned null. */
        data object NullOnImportError : ImportError {
            override val resourceId: Int = R.string.error_preference_importer_null_on_import
            override val formatArgs: Array<out Any>? = null
            override val cause: PachliError? = null
        }

        /** Imported preferences had a version we don't handle. */
        data class WrongVersionError(val version: Int) : ImportError {
            override val resourceId: Int = R.string.error_preference_importer_wrong_version_fmt
            override val formatArgs: Array<out Any>? = arrayOf(version)
            override val cause: PachliError? = null
        }

        /** Imported shared preferences had no schema version. */
        data object MissingSchemaVersionError : ImportError {
            override val resourceId: Int = R.string.error_preference_importer_missing_schema_version
            override val formatArgs: Array<out Any>? = null
            override val cause: PachliError? = null
        }

        /** Generic exception occurred when importing. */
        data class ExceptionError(val throwable: Throwable) : ImportError {
            override val resourceId: Int = app.pachli.core.network.R.string.error_generic_fmt
            override val formatArgs: Array<out Any> = arrayOf(throwable.localizedMessage ?: "")
            override val cause: PachliError? = null
        }
    }
}

/**
 * Wrapper for exported preferences.
 *
 * The schema for exported preferences may change over time. This wrapper
 * describes the format used for this file, and 1-N containers for exported
 * data in the v1..N schema. Not all containers may be populated.
 *
 * @param version Version identifier for the schema in use.
 * @param v1 Container for preferences exported if [version] == 1.
 */
@JsonClass(generateAdapter = true)
internal data class ExportedPreferences(
    val version: Int = 1,
    val v1: V1,
) {
    /**
     * V1 format for exported preferences.
     *
     * @param sharedPreferences The user's shared preferences.
     * @param accounts List of the user's [RedactedAccount].
     */
    @JsonClass(generateAdapter = true)
    data class V1(
        val sharedPreferences: Map<String, Any?>,
        val accounts: List<RedactedAccount>,
    )
}

/**
 * Manages exports and imports of the user's preferences.
 *
 * This includes:
 *
 * - Shared preferences
 * - Redacted information from per-account preferences
 *
 * Preferences are serialised to JSON as an [ExportedPreferences].
 */
@OptIn(ExperimentalStdlibApi::class)
class ExportedPreferencesRepository @Inject constructor(
    private val contentResolver: ContentResolver,
    private val sharedPreferencesRepository: SharedPreferencesRepository,
    private val accountManager: AccountManager,
) {
    private val moshi = Moshi.Builder()
        .add(TaggedNumberAdapter.Factory())
        .build()

    /**
     * Exports the user's preferences to [uri] using [ExportedPreferences.V1].
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        return@withContext runSuspendCatching {
            val redactedAccounts = accountManager.accounts.map { it.toRedactedAccount() }

            val data = ExportedPreferences(
                version = 1,
                v1 = ExportedPreferences.V1(
                    sharedPreferences = sharedPreferencesRepository.all.toImmutableMap(),
                    accounts = redactedAccounts,
                ),
            )

            contentResolver.openOutputStream(uri, "w")?.use {
                it.sink().buffer().use { it.writeUtf8(moshi.adapter<ExportedPreferences>().indent("  ").toJson(data)) }
            }
        }.mapError { ExportError.ExceptionError(it) }
    }

    /**
     * Imports the user's preferences from [uri].
     *
     * Updates any existing accounts with the imported data, then clears and sets the user's
     * shared preferences from the imported data.
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        return@withContext runSuspendCatching {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val importedPrefs = moshi.adapter<ExportedPreferences>().fromJson(inputStream.source().buffer()) ?: return@withContext Err(ImportError.NullOnImportError)

                if (importedPrefs.version != 1) return@withContext Err(ImportError.WrongVersionError(importedPrefs.version))

                // Safe to assume v1 from here.
                val importedSharedPreferences = importedPrefs.v1.sharedPreferences
                val schemaVersion = importedSharedPreferences[PrefKeys.SCHEMA_VERSION] as? Int ?: return@withContext Err(ImportError.MissingSchemaVersionError)

                // Figure out which accounts to update. The user might have logged
                // in to accounts in a different order, so we can't assume the
                // pachliAccountId will be the same. Instead, match accounts by the
                // "identifier" property. A local account and a redacted account with
                // the same identifier are the same.
                val existingAccounts = accountManager.accounts.associateBy { it.identifier }

                // Update any existing accounts from the exported data.
                importedPrefs.v1.accounts.forEach { redactedAccount ->
                    existingAccounts[redactedAccount.identifier]?.let { existingAccount ->
                        accountManager.updateFromRedactedAccount(existingAccount.id, redactedAccount)
                    }
                }

                // Update shared preferences.
                sharedPreferencesRepository.edit {
                    clear()
                    importedSharedPreferences.forEach { (k, v) ->
                        when (v) {
                            is Boolean -> putBoolean(k, v)
                            is Float -> putFloat(k, v)
                            is Int -> putInt(k, v)
                            is Long -> putLong(k, v)
                            is String -> putString(k, v)
                        }
                    }
                }

                // Might have restored shared preferences from an earlier schema version, so
                // upgrade the schema to be sure.
                if (schemaVersion != SCHEMA_VERSION) {
                    sharedPreferencesRepository.upgradeSharedPreferences(schemaVersion, SCHEMA_VERSION)
                }
            }
        }.mapError { ImportError.ExceptionError(it) }
    }
}

/**
 * Moshi adapter that serialises Int and Long as strings, tagged with the original
 * type.
 *
 * - `1` is serialised as `"<I>:1"`
 * - `1L` is serialised as `"<L>:1"`
 *
 * SharedPreferences can store Ints and Longs, and we need to distinguish between
 * them because there are separate `putInt` and `putLong` methods to call when
 * restoring the exported data.
 *
 * This is a problem for JSON, which has a single Number type and does not
 * distinguish between Int, Long, Float, etc. It can also lose precision.
 *
 * To solve this, this adapter writes all Ints and Longs as strings, prefixed with
 * either `<I>:` or `<L>:` for Int and Long respectively. On deserislisation this
 * is parsed to determine the correct numeric type to return.
 *
 * Relying on the "size" of the string doesn't work for several reasons:
 *
 * 1. Mastodon IDs may be strings of numbers, we need to distinguish between
 * those and keep them as strings.
 * 2. The data might include a Long that fits in an Int, but should still be
 * deserialised as a Long.
 *
 * Writing the type tag as a suffix, e.g., a plain `1I` for ints and `1L` for Long
 * doesn't work. The data might include IDs from non-Mastodon servers, e.g.,
 * GoToSocial, that use other ID formats that string-sort consistently with
 * Mastodon but could end in either `I` or `L`.
 *
 * The adapter adapts `Any` so it can work on the Map returned from
 * [android.content.SharedPreferences.getAll], which is a `Map<String, Any>`.
 */
@VisibleForTesting
class TaggedNumberAdapter private constructor(private val delegate: JsonAdapter<Any?>) : JsonAdapter<Any>() {
    @FromJson
    override fun fromJson(reader: JsonReader): Any? {
        reader.peek() ?: return reader.nextNull()

        val value = reader.readJsonValue()
        if (value !is String) return delegate.fromJsonValue(value)

        return when {
            value.startsWith("<I>:") -> value.drop(4).toIntOrNull()
            value.startsWith("<L>:") -> value.drop(4).toLongOrNull()
            else -> delegate.fromJsonValue(value)
        }
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: Any?) {
        if (value == null) {
            writer.nullValue()
        } else {
            val string = when (value) {
                is Int -> "<I>:$value"
                is Long -> "<L>:$value"
                else -> return delegate.toJson(writer, value)
            }
            writer.jsonValue(string)
        }
    }

    class Factory : JsonAdapter.Factory {
        override fun create(type: Type, annotations: Set<Annotation?>, moshi: Moshi): JsonAdapter<*>? {
            val delegate = moshi.nextAdapter<Any?>(this, type, annotations)

            return TaggedNumberAdapter(delegate)
        }
    }
}

/**
 * Redacted version of [AccountEntity] that can be saved to local storage without
 * exposing sensitive data or needlessly replicating data that is stored on the
 * server.
 *
 * Removed fields are:
 *
 * - id - might not be the same when imported
 * - accessToken - sensitive
 * - clientId - sensitive
 * - clientSecret - sensitive
 * - username - server-side
 * - displayName - server-side
 * - isActive - might not be the same when imported
 * - profilePictureUrl - server-side
 * - profileHeaderPictureUrl - server-side
 * - defaultPostPrivacy - server-side
 * - defaultPostLanguage - server-side
 * - defaultMediaSensitivity - server-side
 * - emojis - server-side
 * - oauthScopes - might not be the same when imported
 * - unifiedPushUrl - might not be the same when imported
 * - pushPubKey - might not be the same when imported
 * - pushPrivKey - sensitive
 * - pushAuth - sensitive
 * - pushServerKey - sensitive
 * - locked - server-side
 */
@JsonClass(generateAdapter = true)
data class RedactedAccount(
    val domain: String,
    val accountId: String,
    val notificationsEnabled: Boolean,
    val notificationsMentioned: Boolean,
    val notificationsFollowed: Boolean,
    val notificationsFollowRequested: Boolean,
    val notificationsReblogged: Boolean,
    val notificationsFavorited: Boolean,
    val notificationsPolls: Boolean,
    val notificationsSubscriptions: Boolean,
    val notificationsSignUps: Boolean,
    val notificationsUpdates: Boolean,
    val notificationsReports: Boolean,
    val notificationsSeveredRelationships: Boolean,
    val notificationSound: Boolean,
    val notificationVibration: Boolean,
    val notificationLight: Boolean,
    val alwaysShowSensitiveMedia: Boolean,
    val alwaysOpenSpoiler: Boolean,
    val mediaPreviewEnabled: Boolean,
    val notificationMarkerId: String,
    val tabPreferences: List<Timeline>,
    val notificationsFilter: String,
    val notificationAccountFilterNotFollowed: FilterAction,
    val notificationAccountFilterYounger30d: FilterAction,
    val notificationAccountFilterLimitedByServer: FilterAction,
    val conversationAccountFilterNotFollowed: FilterAction,
    val conversationAccountFilterYounger30d: FilterAction,
    val conversationAccountFilterLimitedByServer: FilterAction,
) {
    val identifier: String
        get() = "$domain:$accountId"
}

/** Converts [AccountEntity] to [RedactedAccount]. */
fun AccountEntity.toRedactedAccount() = RedactedAccount(
    domain = this.domain,
    accountId = this.accountId,
    notificationsEnabled = this.notificationsEnabled,
    notificationsMentioned = this.notificationsMentioned,
    notificationsFollowed = this.notificationsFollowed,
    notificationsFollowRequested = this.notificationsFollowRequested,
    notificationsReblogged = this.notificationsReblogged,
    notificationsFavorited = this.notificationsFavorited,
    notificationsPolls = this.notificationsPolls,
    notificationsSubscriptions = this.notificationsSubscriptions,
    notificationsSignUps = this.notificationsSignUps,
    notificationsUpdates = this.notificationsUpdates,
    notificationsReports = this.notificationsReports,
    notificationsSeveredRelationships = this.notificationsSeveredRelationships,
    notificationSound = this.notificationSound,
    notificationVibration = this.notificationVibration,
    notificationLight = this.notificationLight,
    alwaysShowSensitiveMedia = this.alwaysShowSensitiveMedia,
    alwaysOpenSpoiler = this.alwaysOpenSpoiler,
    mediaPreviewEnabled = this.mediaPreviewEnabled,
    notificationMarkerId = this.notificationMarkerId,
    tabPreferences = this.tabPreferences,
    notificationsFilter = this.notificationsFilter,
    notificationAccountFilterNotFollowed = this.notificationAccountFilterNotFollowed,
    notificationAccountFilterYounger30d = this.notificationAccountFilterYounger30d,
    notificationAccountFilterLimitedByServer = this.notificationAccountFilterLimitedByServer,
    conversationAccountFilterNotFollowed = this.conversationAccountFilterNotFollowed,
    conversationAccountFilterYounger30d = this.conversationAccountFilterYounger30d,
    conversationAccountFilterLimitedByServer = this.conversationAccountFilterLimitedByServer,
)
