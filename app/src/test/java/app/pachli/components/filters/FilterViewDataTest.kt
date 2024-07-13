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

import app.pachli.core.data.model.Filter
import app.pachli.core.data.repository.FilterEdit
import app.pachli.core.network.model.Filter.Action
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FilterViewDataTest {
    private val originalFilter = Filter(
        id = "1",
        title = "original filter",
        contexts = setOf(FilterContext.HOME),
        expiresAt = null,
        action = Action.WARN,
        keywords = listOf(
            FilterKeyword(id = "1", keyword = "first", wholeWord = false),
            FilterKeyword(id = "2", keyword = "second", wholeWord = true),
            FilterKeyword(id = "3", keyword = "three", wholeWord = true),
            FilterKeyword(id = "4", keyword = "four", wholeWord = true),
        ),
    )

    private val originalFilterViewData = FilterViewData.from(originalFilter)

    @Test
    fun `diff title only affects title`() {
        val newTitle = "new title"

        val update = originalFilterViewData
            .copy(title = newTitle)
            .diff(originalFilter)

        val expectedUpdate = FilterEdit(id = originalFilter.id, title = newTitle)

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `diff contexts only affects contexts`() {
        val newContexts = setOf(FilterContext.HOME, FilterContext.THREAD)

        val update = originalFilterViewData
            .copy(contexts = newContexts)
            .diff(originalFilter)

        val expectedUpdate = FilterEdit(id = originalFilter.id, contexts = newContexts)

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `diff expiresIn only affects expiresIn`() {
        val newExpiresIn = 300

        val update = originalFilterViewData
            .copy(expiresIn = newExpiresIn)
            .diff(originalFilter)

        val expectedUpdate = FilterEdit(id = originalFilter.id, expiresIn = newExpiresIn)

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `diff action only affects action`() {
        val newAction = Action.HIDE

        val update = originalFilterViewData
            .copy(action = newAction)
            .diff(originalFilter)

        val expectedUpdate = FilterEdit(id = originalFilter.id, action = newAction)

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `adding a keyword updates keywordsToAdd`() {
        val newKeyword = FilterKeyword(id = "", keyword = "new keyword", wholeWord = false)

        val update = originalFilterViewData
            .copy(keywords = originalFilterViewData.keywords + newKeyword)
            .diff(originalFilter)

        val expectedUpdate = FilterEdit(id = originalFilter.id, keywordsToAdd = listOf(newKeyword))

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `deleting a keyword updates keywordsToDelete`() {
        val (keywordsToDelete, updatedKeywords) = originalFilterViewData.keywords.partition {
            it.id == "2"
        }

        val update = originalFilterViewData
            .copy(keywords = updatedKeywords)
            .diff(originalFilter)

        val expectedUpdate = FilterEdit(id = originalFilter.id, keywordsToDelete = keywordsToDelete)

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `modifying a keyword updates keywordsToModify`() {
        val modifiedKeyword = originalFilter.keywords[1].copy(keyword = "modified keyword")

        val newKeywords = originalFilter.keywords.map {
            if (it.id == modifiedKeyword.id) modifiedKeyword else it
        }

        val update = originalFilterViewData
            .copy(keywords = newKeywords)
            .diff(originalFilter)

        // The fact the keywords are in a different order now should have no effect.
        // Only the change to the key
        val expectedUpdate = FilterEdit(id = originalFilter.id, keywordsToModify = listOf(modifiedKeyword))

        assertThat(update).isEqualTo(expectedUpdate)
    }

    @Test
    fun `adding, modifying, and deleting keywords together works`() {
        // Add a new keyword, delete keyword with id == "2", and modify the keyword with
        // id == "3".
        val keywordToAdd = FilterKeyword(id = "", keyword = "new keyword", wholeWord = false)
        val keywordToDelete = originalFilter.keywords.find { it.id == "2" }!!
        val modifiedKeyword = originalFilter.keywords.find { it.id == "3" }?.copy(keyword = "modified keyword")!!

        val newKeywords = originalFilter.keywords
            .filterNot { it.id == keywordToDelete.id }
            .map { if (it.id == modifiedKeyword.id) modifiedKeyword else it }
            .plus(keywordToAdd)

        val update = originalFilterViewData
            .copy(keywords = newKeywords)
            .diff(originalFilter)

        val expectedUpdate = FilterEdit(
            id = originalFilter.id,
            keywordsToAdd = listOf(keywordToAdd),
            keywordsToDelete = listOf(keywordToDelete),
            keywordsToModify = listOf(modifiedKeyword),
        )

        assertThat(update).isEqualTo(expectedUpdate)
    }
}
