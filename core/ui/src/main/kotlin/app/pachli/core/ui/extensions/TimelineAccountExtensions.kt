/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.ui.extensions

import android.content.Context
import app.pachli.core.model.TimelineAccount
import app.pachli.core.ui.R

/**
 * Returns a content description for this account.
 *
 * The description looks like (content in parentheses is optional)
 *
 * "${account.name}"
 * ","
 * "Handle, @${account.username}"
 * ";"
 * ("with role: 1-n roles")
 *
 * When reading account names any embedded "." in the name is converted to " dot "
 * to make it explicit (otherwise TalkBack inserts a short pause, which is no use
 * for domains). See [nameContentDescription].
 */
fun TimelineAccount.contentDescription(context: Context): String {
    val roleString = if (roles.isNotEmpty()) {
        StringBuilder().apply {
            append(context.resources.getQuantityString(R.plurals.description_post_roles, roles.size))
            append(roles.joinToString(", ") { it.contentDescription(context) })
        }.toString()
    } else {
        ""
    }

    return context.getString(
        R.string.account_contentdescription_fmt,
        // Some names have dots in them
        nameContentDescription(context),
        handleContentDescription(context),
        roleString,
    )
}

/**
 * @return A content description for the account's name. Dots in the name are
 * replaced with R.string.dot_in_name so TalkBack reads them correctly.
 *
 * For example, for the account "@staff@mastodon.social" the display name is
 * "Mastodon.social Staff", and this ensures this is read as "Mastodon dot social
 * Staff", not "Mastodon <pause> Social staff".
 */
fun TimelineAccount.nameContentDescription(context: Context) = nameContentDescription(context, name)

internal fun nameContentDescription(context: Context, name: String): String {
    val dotReplacement = context.getString(R.string.dot_in_name)
    return name.replace(".", " $dotReplacement ")
}

/**
 * Map of domain suffixes that TalkBack doesn't read correctly to
 * better ways to pronounce the content.
 *
 * Work around for https://issuetracker.google.com/issues/447792953.
 */
val talkbackTlds = mapOf(".xyz" to ".x y z")

/** Regex that matches and groups each domain suffix in [talkbackTlds]. */
val rxTalkbackTlds = "(${talkbackTlds.keys.joinToString("|")}$)".toRegex()

/**
 * @return A content description for the account's handle. If the handle
 * contains a TLD with a key in [talkbackTlds] it is replaced with the
 * value at that key for better TalkBack pronounciation.
 */
fun TimelineAccount.handleContentDescription(context: Context) = handleContentDescription(context, username)

internal fun handleContentDescription(context: Context, handle: String): String {
    // Create a pronounceable version of the handle by replacing problematic TLDs.
    val correctedHandle = rxTalkbackTlds.replace(handle) { m ->
        m.groupValues.getOrNull(1)?.let { talkbackTlds[it] as CharSequence } ?: ""
    }

    return context.getString(R.string.handle_contentdescription_fmt, correctedHandle)
}
