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

package app.pachli.core.activity

import app.pachli.core.activity.ViewUrlActivity.Companion.looksLikeMastodonUrl
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class UrlMatchingTests(private val url: String, private val expectedResult: Boolean) {
    companion object {
        @Parameterized.Parameters(name = "match_{0}")
        @JvmStatic
        fun data(): Iterable<Any> {
            return listOf(
                arrayOf("https://mastodon.foo.bar/@User", true),
                arrayOf("http://mastodon.foo.bar/@abc123", true),
                arrayOf("https://mastodon.foo.bar/@user/345667890345678", true),
                arrayOf("https://mastodon.foo.bar/@user/3", true),
                arrayOf("https://mastodon.foo.bar/users/User/statuses/43456787654678", true),
                arrayOf("https://pleroma.foo.bar/users/meh3223", true),
                arrayOf("https://pleroma.foo.bar/users/meh3223_bruh", true),
                arrayOf("https://pleroma.foo.bar/users/2345", true),
                arrayOf("https://pleroma.foo.bar/notice/9", true),
                arrayOf("https://pleroma.foo.bar/notice/9345678", true),
                arrayOf("https://pleroma.foo.bar/notice/wat", true),
                arrayOf("https://pleroma.foo.bar/notice/9qTHT2ANWUdXzENqC0", true),
                arrayOf("https://pleroma.foo.bar/objects/abcdef-123-abcd-9876543", true),
                arrayOf("https://misskey.foo.bar/notes/mew", true),
                arrayOf("https://misskey.foo.bar/notes/1421564653", true),
                arrayOf("https://misskey.foo.bar/notes/qwer615985ddf", true),
                arrayOf("https://friendica.foo.bar/profile/user", true),
                arrayOf("https://friendica.foo.bar/profile/uSeR", true),
                arrayOf("https://friendica.foo.bar/profile/user_user", true),
                arrayOf("https://friendica.foo.bar/profile/123", true),
                arrayOf("https://friendica.foo.bar/display/abcdef-123-abcd-9876543", true),
                arrayOf("https://google.com/", false),
                arrayOf("https://mastodon.foo.bar/@User?foo=bar", false),
                arrayOf("https://mastodon.foo.bar/@User#foo", false),
                arrayOf("http://mastodon.foo.bar/@", false),
                arrayOf("http://mastodon.foo.bar/@/345678", false),
                arrayOf("https://mastodon.foo.bar/@user/345667890345678/", false),
                arrayOf("https://mastodon.foo.bar/@user/3abce", false),
                arrayOf("https://pleroma.foo.bar/users/", false),
                arrayOf("https://pleroma.foo.bar/users/meow/", false),
                arrayOf("https://pleroma.foo.bar/users/@meow", false),
                arrayOf("https://pleroma.foo.bar/user/2345", false),
                arrayOf("https://pleroma.foo.bar/notices/123456", false),
                arrayOf("https://pleroma.foo.bar/notice/@neverhappen/", false),
                arrayOf("https://pleroma.foo.bar/object/abcdef-123-abcd-9876543", false),
                arrayOf("https://pleroma.foo.bar/objects/xabcdef-123-abcd-9876543", false),
                arrayOf("https://pleroma.foo.bar/objects/xabcdef-123-abcd-9876543/", false),
                arrayOf("https://pleroma.foo.bar/objects/xabcdef-123-abcd_9876543", false),
                arrayOf("https://friendica.foo.bar/display/xabcdef-123-abcd-9876543", false),
                arrayOf("https://friendica.foo.bar/display/xabcdef-123-abcd-9876543/", false),
                arrayOf("https://friendica.foo.bar/display/xabcdef-123-abcd_9876543", false),
                arrayOf("https://friendica.foo.bar/profile/@mew", false),
                arrayOf("https://friendica.foo.bar/profile/@mew/", false),
                arrayOf("https://misskey.foo.bar/notes/@nyan", false),
                arrayOf("https://misskey.foo.bar/notes/NYAN123", false),
                arrayOf("https://misskey.foo.bar/notes/meow123/", false),
                arrayOf("https://pixelfed.social/p/connyduck/391263492998670833", true),
                arrayOf("https://pixelfed.social/connyduck", true),
                arrayOf("https://gts.foo.bar/@goblin/statuses/01GH9XANCJ0TA8Y95VE9H3Y0Q2", true),
                arrayOf("https://gts.foo.bar/@goblin", true),
                arrayOf("https://foo.microblog.pub/o/5b64045effd24f48a27d7059f6cb38f5", true),
            )
        }
    }

    @Test
    fun test() {
        Assert.assertEquals(expectedResult, looksLikeMastodonUrl(url))
    }
}
