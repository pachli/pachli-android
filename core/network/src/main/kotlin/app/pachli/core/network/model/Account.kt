/* Copyright 2017 Andrew Dawson
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

package app.pachli.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * @property localUsername The username of the account, without the domain.
 * @property username The webfinger account URI. Equal to [localUsername]
 * for local users, or [localUsername]@domain for remote users.
 * @property note (HTML) The profile’s bio or description.
 */
@JsonClass(generateAdapter = true)
data class Account(
    val id: String,
    @Json(name = "username") val localUsername: String,
    @Json(name = "acct") val username: String,
    // should never be null per API definition, but some servers break the contract
    @Json(name = "display_name") val displayName: String?,
    // should never be null per API definition, but some servers break the contract
    @Json(name = "created_at") val createdAt: Date?,
    val note: String,
    val url: String,
    val avatar: String,
    // Pixelfed might omit `header`
    val header: String = "",
    val locked: Boolean = false,
    @Json(name = "last_status_at") val lastStatusAt: Date? = null,
    @Json(name = "followers_count") val followersCount: Int = 0,
    @Json(name = "following_count") val followingCount: Int = 0,
    @Json(name = "statuses_count") val statusesCount: Int = 0,
    val bot: Boolean = false,
    // nullable for backward compatibility
    val emojis: List<Emoji>? = emptyList(),
    // nullable for backward compatibility
    val fields: List<Field>? = emptyList(),
    val moved: Account? = null,
    val roles: List<Role>? = emptyList(),
) {
    fun asModel(): app.pachli.core.model.Account = app.pachli.core.model.Account(
        id = id,
        localUsername = localUsername,
        username = username,
        displayName = displayName,
        createdAt = createdAt,
        note = note,
        url = url,
        avatar = avatar,
        header = header,
        locked = locked,
        lastStatusAt = lastStatusAt,
        followersCount = followersCount,
        followingCount = followingCount,
        statusesCount = statusesCount,
        bot = bot,
        emojis = emojis?.asModel(),
        fields = fields?.asModel(),
        moved = moved?.asModel(),
        roles = roles?.asModel(),
        pronouns = fields?.pronouns(),
    )
}

@JsonClass(generateAdapter = true)
data class Field(
    val name: String,
    val value: String,
    @Json(name = "verified_at") val verifiedAt: Date?,
) {
    fun asModel() = app.pachli.core.model.Field(
        name = name,
        value = value,
        verifiedAt = verifiedAt,
    )
}

@JvmName("iterableFieldAsModel")
fun Iterable<Field>.asModel() = map { it.asModel() }

/**
 * Set of field names. Any field matching this name is considered to contain displayable
 * pronouns for [pronouns]
 */
// The code uses this as a set, but build it here from a Map<LanguageCode, List<String>>.
// This makes it easier to see which language code corresponds to which set of pronouns
// when maintaining this list. Only the final pronoun text is used, the language code
// is discarded.
//
// Using a string array, raw resource, or asset was considered here, they don't make
// it easy to keep the nested structure. This also doesn't play well with translation
// tools.
//
// Initial contents of this list were seeded from Fedilab in
// https://codeberg.org/tom79/Fedilab/src/branch/main/app/src/main/java/app/fedilab/android/mastodon/helper/PronounsHelper.java
// and Moshidon (e.g., https://github.com/LucasGGamerM/moshidon/blob/b6a4211af93e3ae0b5b4baca6acce30cec601f5c/mastodon/src/main/res/values-el-rGR/strings_sk.xml#L285)
private val pronounFieldNames = mapOf(
    "ar" to listOf("الضمائر"),
    "ast" to listOf("pronomes"),
    "ca" to listOf("pronom", "pronoms"),
    "de" to listOf("pronomen"),
    "el" to listOf("Αντωνυμίες"),
    "en" to listOf("pronoun", "pronouns"),
    "eo" to listOf("pronomoj"),
    "es" to listOf("pronombres"),
    "fa" to listOf("ضمایر"),
    "fi" to listOf("Pronominit"),
    "fr" to listOf("pronoms"),
    "ga" to listOf("Forainmneacha"),
    "gd" to listOf("riochdairean"),
    "gl" to listOf("pronomes"),
    "he" to listOf("כינויי גוף", "לשון פנייה", "כינויי גוף"),
    "id" to listOf("pronomina"),
    "in-rID" to listOf("Kata ganti"),
    "it" to listOf("pronomi"),
    "jp" to listOf("代名詞"),
    "ko" to listOf("인칭 대명사"),
    "lt" to listOf("Įvardžiai"),
    "lv" to listOf("vietniekv", "vietniekvārdi", "vietniekvārds"),
    "nl" to listOf("voornaamwoorden"),
    "nn" to listOf("pronomen"),
    "no" to listOf("personlig pronomen"),
    "pl" to listOf("zaimki"),
    "pt" to listOf("pronomes"),
    "pt-br" to listOf("pronome"),
    "ro" to listOf("pronume"),
    "ru" to listOf("местоим", "местоимения", "Произношение"),
    "tr" to listOf("Zamirler"),
    "uk" to listOf("Займенники"),
    "zh" to listOf("人称", "人稱", "称谓"),
    "zh-rCN" to listOf("人称代词"),
    "zh-rTW" to listOf("別名"),
).values.flatMap { pronouns -> pronouns.map { it.lowercase() } }.toSet()

/**
 * @return The account's pronouns, if one of the fields has a recognisable name.
 * Null if no field had a recognisable name.
 */
fun Iterable<Field>.pronouns() = firstOrNull { pronounFieldNames.contains(it.name.lowercase()) }?.value

@JsonClass(generateAdapter = true)
data class StringField(
    val name: String,
    val value: String,
) {
    fun asModel() = app.pachli.core.model.StringField(
        name = name,
        value = value,
    )
}

@JvmName("iterableStringFieldAsModel")
fun Iterable<StringField>.asModel() = map { it.asModel() }

/** [Mastodon Entities: Role](https://docs.joinmastodon.org/entities/Role) */
@JsonClass(generateAdapter = true)
data class Role(
    /** Displayable name of the role */
    val name: String,
    /** Colour to use for the role badge, may be the empty string */
    val color: String,
    // Default value is true, since the property may be missing and the observed
    // Mastodon behaviour when it is is to highlight the role. Also, this property
    // being missing breaks InstanceV2 parsing.
    // See https://github.com/mastodon/mastodon/issues/28327
    /** True if the badge should be displayed on the account profile */
    val highlighted: Boolean = true,
) {
    fun asModel() = app.pachli.core.model.Role(
        name = name,
        color = color,
        highlighted = highlighted,
    )
}

@JvmName("iterableRoleAsModel")
fun Iterable<Role>.asModel() = map { it.asModel() }
