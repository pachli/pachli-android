/*
 * Copyright (c) 2026 Pachli Association
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

// Test cases from https://github.com/mastodon/mastodon/blob/main/spec/lib/hashtag_normalizer_spec.rb.

@RunWith(AndroidJUnit4::class)
class HashtagNormalizerTest {
    @Test
    fun `converts full-width Latin characters into basic Latin characters`() {
        assertThat("Ｓｙｎｔｈｗａｖｅ".normaliseHashtag()).isEqualTo("synthwave")
    }

    @Test
    fun `converts half-width Katakana into Kana characters`() {
        assertThat("ｼｰｻｲﾄﾞﾗｲﾅｰ".normaliseHashtag()).isEqualTo("シーサイドライナー")
    }

    @Test
    fun `converts modified Latin characters into basic Latin characters`() {
        assertThat("BLÅHAJ".normaliseHashtag()).isEqualTo("blahaj")
    }

    @Test
    fun `strips out invalid characters`() {
        assertThat("#foo".normaliseHashtag()).isEqualTo("foo")
    }

    @Test
    fun `keeps valid characters`() {
        assertThat("a·b".normaliseHashtag()).isEqualTo("a·b")
    }
}
