/*
 * Copyright 2024 Pachli Association
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

package app.pachli.components.filters

import app.pachli.core.data.repository.ContentFilterEdit
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.FilterKeyword
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentContentFilterViewDataTest {
    private val originalContentFilter = ContentFilter(
        id = "1",
        title = "original filter",
        contexts = setOf(FilterContext.HOME),
        expiresAt = null,
        filterAction = FilterAction.WARN,
        keywords = listOf(
            FilterKeyword(id = "1", keyword = "first", wholeWord = false),
            FilterKeyword(id = "2", keyword = "second", wholeWord = true),
            FilterKeyword(id = "3", keyword = "three", wholeWord = true),
            FilterKeyword(id = "4", keyword = "four", wholeWord = true),
        ),
    )

    private val originalContentFilterViewData = ContentFilterViewData.from(originalContentFilter)

    @Test
    fun `diff title only affects title`() {
        val newTitle = "new title"

        val update = originalContentFilterViewData
            .copy(title = newTitle)
            .diff(originalContentFilter)

        val expectedUpdate = ContentFilterEdit(id = originalContentFilter.id, title = newTitle)

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `diff contexts only affects contexts`() {
        val newContexts = setOf(FilterContext.HOME, FilterContext.CONVERSATIONS)

        val update = originalContentFilterViewData
            .copy(contexts = newContexts)
            .diff(originalContentFilter)

        val expectedUpdate = ContentFilterEdit(id = originalContentFilter.id, contexts = newContexts)

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `diff expiresIn only affects expiresIn`() {
        val newExpiresIn = 300

        val update = originalContentFilterViewData
            .copy(expiresIn = newExpiresIn)
            .diff(originalContentFilter)

        val expectedUpdate = ContentFilterEdit(id = originalContentFilter.id, expiresIn = newExpiresIn)

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `diff action only affects action`() {
        val newFilterAction = FilterAction.HIDE

        val update = originalContentFilterViewData
            .copy(filterAction = newFilterAction)
            .diff(originalContentFilter)

        val expectedUpdate = ContentFilterEdit(id = originalContentFilter.id, filterAction = newFilterAction)

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `adding a keyword updates keywordsToAdd`() {
        val newKeyword = FilterKeyword(id = "", keyword = "new keyword", wholeWord = false)

        val update = originalContentFilterViewData
            .copy(keywords = originalContentFilterViewData.keywords + newKeyword)
            .diff(originalContentFilter)

        val expectedUpdate = ContentFilterEdit(id = originalContentFilter.id, keywordsToAdd = listOf(newKeyword))

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `deleting a keyword updates keywordsToDelete`() {
        val (keywordsToDelete, updatedKeywords) = originalContentFilterViewData.keywords.partition {
            it.id == "2"
        }

        val update = originalContentFilterViewData
            .copy(keywords = updatedKeywords)
            .diff(originalContentFilter)

        val expectedUpdate = ContentFilterEdit(id = originalContentFilter.id, keywordsToDelete = keywordsToDelete)

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `modifying a keyword updates keywordsToModify`() {
        val modifiedKeyword = originalContentFilter.keywords[1].copy(keyword = "modified keyword")

        val newKeywords = originalContentFilter.keywords.map {
            if (it.id == modifiedKeyword.id) modifiedKeyword else it
        }

        val update = originalContentFilterViewData
            .copy(keywords = newKeywords)
            .diff(originalContentFilter)

        // The fact the keywords are in a different order now should have no effect.
        // Only the change to the key
        val expectedUpdate = ContentFilterEdit(id = originalContentFilter.id, keywordsToModify = listOf(modifiedKeyword))

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `adding, modifying, and deleting keywords together works`() {
        // Add a new keyword, delete keyword with id == "2", and modify the keyword with
        // id == "3".
        val keywordToAdd = FilterKeyword(id = "", keyword = "new keyword", wholeWord = false)
        val keywordToDelete = originalContentFilter.keywords.find { it.id == "2" }!!
        val modifiedKeyword = originalContentFilter.keywords.find { it.id == "3" }?.copy(keyword = "modified keyword")!!

        val newKeywords = originalContentFilter.keywords
            .filterNot { it.id == keywordToDelete.id }
            .map { if (it.id == modifiedKeyword.id) modifiedKeyword else it }
            .plus(keywordToAdd)

        val update = originalContentFilterViewData
            .copy(keywords = newKeywords)
            .diff(originalContentFilter)

        val expectedUpdate = ContentFilterEdit(
            id = originalContentFilter.id,
            keywordsToAdd = listOf(keywordToAdd),
            keywordsToDelete = listOf(keywordToDelete),
            keywordsToModify = listOf(modifiedKeyword),
        )

        assertThat(update).isEqualTo(expectedUpdate)
    }
}
