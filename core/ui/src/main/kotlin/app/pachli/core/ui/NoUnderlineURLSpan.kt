/*
 * Copyright 2021 Tusky contributors
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

package app.pachli.core.ui

import android.text.TextPaint
import android.text.style.URLSpan
import android.view.View
import app.pachli.core.activity.openLink

open class NoUnderlineURLSpan(val url: String) : URLSpan(url) {

    // This should not be necessary. But if you don't do this the [StatusLengthTest] tests
    // fail. Without this, accessing the `url` property, or calling `getUrl()` (which should
    // automatically call through to [UrlSpan.getURL]) returns null.
    override fun getURL(): String {
        return url
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = false
    }

    override fun onClick(view: View) {
        view.context.openLink(url)
    }
}

/**
 * Mentions of other users ("@user@example.org")
 */
open class MentionSpan(url: String) : NoUnderlineURLSpan(url)

/**
 * Hashtags (`#foo`)
 *
 * @param hashtag Text of the tag, without the leading `#`.
 * @param url URL for the tag.
 * @param listener Listener for clicks on the tag.
 */
class HashtagSpan(val hashtag: String, url: String, val listener: LinkListener) : NoUnderlineURLSpan(url) {
    override fun onClick(view: View) = listener.onViewTag(hashtag)
}
