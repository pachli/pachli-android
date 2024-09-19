/*
 * Copyright 2023 Tusky Contributors
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

package app.pachli

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pachli.components.filters.EditContentFilterViewModel.Companion.getSecondsForDurationIndex
import app.pachli.core.model.ContentFilter
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.FilterAction
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterV1
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.PollOption
import app.pachli.core.network.model.Status
import app.pachli.network.ContentFilterModel
import java.time.Instant
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class ContentFilterV1Test {

    private lateinit var contentFilterModel: ContentFilterModel

    @Before
    fun setup() {
        val contentFilters = listOf(
            FilterV1(
                id = "123",
                phrase = "badWord",
                contexts = setOf(FilterContext.HOME),
                expiresAt = null,
                irreversible = false,
                wholeWord = false,
            ),
            FilterV1(
                id = "123",
                phrase = "badWholeWord",
                contexts = setOf(FilterContext.HOME, FilterContext.PUBLIC),
                expiresAt = null,
                irreversible = false,
                wholeWord = true,
            ),
            FilterV1(
                id = "123",
                phrase = "@twitter.com",
                contexts = setOf(FilterContext.HOME),
                expiresAt = null,
                irreversible = false,
                wholeWord = true,
            ),
            FilterV1(
                id = "123",
                phrase = "#hashtag",
                contexts = setOf(FilterContext.HOME),
                expiresAt = null,
                irreversible = false,
                wholeWord = true,
            ),
            FilterV1(
                id = "123",
                phrase = "expired",
                contexts = setOf(FilterContext.HOME),
                expiresAt = Date.from(Instant.now().minusSeconds(10)),
                irreversible = false,
                wholeWord = true,
            ),
            FilterV1(
                id = "123",
                phrase = "unexpired",
                contexts = setOf(FilterContext.HOME),
                expiresAt = Date.from(Instant.now().plusSeconds(3600)),
                irreversible = false,
                wholeWord = true,
            ),
            FilterV1(
                id = "123",
                phrase = "href",
                contexts = setOf(FilterContext.HOME),
                expiresAt = null,
                irreversible = false,
                wholeWord = false,
            ),
        ).map { ContentFilter.from(it) }

        contentFilterModel = ContentFilterModel(FilterContext.HOME, contentFilters)
    }

    @Test
    fun shouldNotFilter() {
        assertEquals(
            FilterAction.NONE,
            contentFilterModel.filterActionFor(
                mockStatus(content = "should not be filtered"),
            ),
        )
    }

    @Test
    fun shouldFilter_whenContentMatchesBadWord() {
        assertEquals(
            FilterAction.HIDE,
            contentFilterModel.filterActionFor(
                mockStatus(content = "one two badWord three"),
            ),
        )
    }

    @Test
    fun shouldFilter_whenContentMatchesBadWordPart() {
        assertEquals(
            FilterAction.HIDE,
            contentFilterModel.filterActionFor(
                mockStatus(content = "one two badWordPart three"),
            ),
        )
    }

    @Test
    fun shouldFilter_whenContentMatchesBadWholeWord() {
        assertEquals(
            FilterAction.HIDE,
            contentFilterModel.filterActionFor(
                mockStatus(content = "one two badWholeWord three"),
            ),
        )
    }

    @Test
    fun shouldNotFilter_whenContentDoesNotMatchWholeWord() {
        assertEquals(
            FilterAction.NONE,
            contentFilterModel.filterActionFor(
                mockStatus(content = "one two badWholeWordTest three"),
            ),
        )
    }

    @Test
    fun shouldFilter_whenSpoilerTextDoesMatch() {
        assertEquals(
            FilterAction.HIDE,
            contentFilterModel.filterActionFor(
                mockStatus(
                    content = "should not be filtered",
                    spoilerText = "badWord should be filtered",
                ),
            ),
        )
    }

    @Test
    fun shouldFilter_whenPollTextDoesMatch() {
        assertEquals(
            FilterAction.HIDE,
            contentFilterModel.filterActionFor(
                mockStatus(
                    content = "should not be filtered",
                    spoilerText = "should not be filtered",
                    pollOptions = listOf("should not be filtered", "badWord"),
                ),
            ),
        )
    }

    @Test
    fun shouldFilter_whenMediaDescriptionDoesMatch() {
        assertEquals(
            FilterAction.HIDE,
            contentFilterModel.filterActionFor(
                mockStatus(
                    content = "should not be filtered",
                    spoilerText = "should not be filtered",
                    attachmentsDescriptions = listOf("should not be filtered", "badWord"),
                ),
            ),
        )
    }

    @Test
    fun shouldFilterPartialWord_whenWholeWordFilterContainsNonAlphanumericCharacters() {
        assertEquals(
            FilterAction.HIDE,
            contentFilterModel.filterActionFor(
                mockStatus(content = "one two someone@twitter.com three"),
            ),
        )
    }

    @Test
    fun shouldFilterHashtags() {
        assertEquals(
            FilterAction.HIDE,
            contentFilterModel.filterActionFor(
                mockStatus(content = "#hashtag one two three"),
            ),
        )
    }

    @Test
    fun shouldFilterHashtags_whenContentIsMarkedUp() {
        assertEquals(
            FilterAction.HIDE,
            contentFilterModel.filterActionFor(
                mockStatus(content = "<p><a href=\"https://foo.bar/tags/hashtag\" class=\"mention hashtag\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">#<span>hashtag</span></a>one two three</p>"),
            ),
        )
    }

    @Test
    fun shouldNotFilterHtmlAttributes() {
        assertEquals(
            FilterAction.NONE,
            contentFilterModel.filterActionFor(
                mockStatus(content = "<p><a href=\"https://foo.bar/\">https://foo.bar/</a> one two three</p>"),
            ),
        )
    }

    @Test
    fun shouldNotFilter_whenFilterIsExpired() {
        assertEquals(
            FilterAction.NONE,
            contentFilterModel.filterActionFor(
                mockStatus(content = "content matching expired filter should not be filtered"),
            ),
        )
    }

    @Test
    fun shouldFilter_whenFilterIsUnexpired() {
        assertEquals(
            FilterAction.HIDE,
            contentFilterModel.filterActionFor(
                mockStatus(content = "content matching unexpired filter should be filtered"),
            ),
        )
    }

    @Test
    fun unchangedExpiration_shouldBeNegative_whenFilterIsExpired() {
        val expiredBySeconds = 3600
        val expiredDate = Date.from(Instant.now().minusSeconds(expiredBySeconds.toLong()))
        val updatedDuration = getSecondsForDurationIndex(-1, null, expiredDate)
        assert(updatedDuration != null && updatedDuration.toInt() <= -expiredBySeconds)
    }

    @Test
    fun unchangedExpiration_shouldBePositive_whenFilterIsUnexpired() {
        val expiresInSeconds = 3600
        val expiredDate = Date.from(Instant.now().plusSeconds(expiresInSeconds.toLong()))
        val updatedDuration = getSecondsForDurationIndex(-1, null, expiredDate)
        assert(updatedDuration != null && updatedDuration.toInt() > (expiresInSeconds - 60))
    }

    companion object {
        fun mockStatus(
            content: String = "",
            spoilerText: String = "",
            pollOptions: List<String>? = null,
            attachmentsDescriptions: List<String>? = null,
        ): Status {
            return Status(
                id = "123",
                url = "https://mastodon.social/@Tusky/100571663297225812",
                account = mock(),
                inReplyToId = null,
                inReplyToAccountId = null,
                reblog = null,
                content = content,
                createdAt = Date(),
                editedAt = null,
                emojis = emptyList(),
                reblogsCount = 0,
                favouritesCount = 0,
                repliesCount = 0,
                reblogged = false,
                favourited = false,
                bookmarked = false,
                sensitive = false,
                spoilerText = spoilerText,
                visibility = Status.Visibility.PUBLIC,
                attachments = if (attachmentsDescriptions != null) {
                    ArrayList(
                        attachmentsDescriptions.map {
                            Attachment(
                                id = "1234",
                                url = "",
                                previewUrl = null,
                                meta = null,
                                type = Attachment.Type.IMAGE,
                                description = it,
                                blurhash = null,
                            )
                        },
                    )
                } else {
                    arrayListOf()
                },
                mentions = listOf(),
                tags = listOf(),
                application = null,
                pinned = false,
                muted = false,
                poll = if (pollOptions != null) {
                    Poll(
                        id = "1234",
                        expiresAt = null,
                        expired = false,
                        multiple = false,
                        votesCount = 0,
                        votersCount = 0,
                        options = pollOptions.map {
                            PollOption(it, 0)
                        },
                        voted = false,
                        ownVotes = null,
                    )
                } else {
                    null
                },
                card = null,
                language = null,
                filtered = null,
            )
        }
    }
}
