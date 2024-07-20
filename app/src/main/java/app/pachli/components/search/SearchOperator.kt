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

package app.pachli.components.search

import app.pachli.BuildConfig
import app.pachli.util.modernLanguageCode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Mastodon search operators. */
sealed interface SearchOperator {
    /**
     * The user's choice from the set of possible choices the operator supports.
     *
     * If null the user has not made a specific choice, and the operator's default
     * should be used.
     */
    val choice: Any?

    /**
     * @return Text to include in the search query if [choice] is non-null.
     */
    fun query(): String?

    /**
     * The `has:{media,image,video,audio}` operator.
     *
     * Mastodon does not let you create posts that have attached media and a poll
     * or other attachment. But it will store and return statuses sent from other
     * systems that do not have this restriction.
     *
     * @see HasEmbedOperator
     * @see HasPollOperator
     */
    data class HasMediaOperator(override val choice: HasMediaOption? = null) : SearchOperator {
        enum class MediaKind(val q: String) {
            IMAGE("image"),
            VIDEO("video"),
            AUDIO("audio"),
        }

        /** The specific `has:{media,image,video,audio}` operator in use. */
        sealed interface HasMediaOption {
            /** Exclude posts that have any media attached.
             *
             * Equivalent to `-has:media`.
             */
            data object NoMedia : HasMediaOption

            /**
             * Include only posts that have any media attached, except posts that
             * have media types in [exclude].
             *
             * Equivalent to `has:media`, with zero or more additional `-has:[exclude]`
             * after.
             */
            data class HasMedia(val exclude: List<MediaKind> = emptyList()) : HasMediaOption

            /**
             * Include only posts that have [include] media attached, excluding posts
             * that have [exclude] attached media.
             */
            data class SpecificMedia(
                val include: List<MediaKind> = emptyList(),
                val exclude: List<MediaKind> = emptyList(),
            ) : HasMediaOption {
                init {
                    // Check
                    // - with and without can't both be empty
                    // - with and without should not contain any shared elements
                    if (BuildConfig.DEBUG) {
                        assert(!(include.isEmpty() && exclude.isEmpty()))
                        assert(include.intersect(exclude.toSet()).isEmpty())
                    }
                }
            }
        }

        override fun query(): String? {
            choice ?: return null

            return when (choice) {
                HasMediaOption.NoMedia -> ("-has:media")
                is HasMediaOption.HasMedia -> buildList {
                    add("has:media")
                    choice.exclude.forEach { add("-has:${it.q}") }
                }.joinToString(" ")

                is HasMediaOption.SpecificMedia -> buildList {
                    choice.include.forEach { add("has:${it.q}") }
                    choice.exclude.forEach { add("-has:${it.q}") }
                }.joinToString(" ")
            }
        }
    }

    /** The date-range operator. Creates `after:... before:...`. */
    class DateOperator(override val choice: DateRange? = null) : SearchOperator {
        /**
         * The date range to search.
         *
         * @param startDate Earliest date to search (inclusive)
         * @param endDate Latest date to search (inclusive)
         */
        data class DateRange(val startDate: LocalDate, val endDate: LocalDate) {
            // This class treats the date range as **inclusive** of the start and
            // end dates, Mastodon's search treats the dates as exclusive, so the
            // range must be expanded by one day in each direction when creating
            // the search string.
            fun fmt() = "after:${formatter.format(startDate.minusDays(1))} before:${formatter.format(endDate.plusDays(1))}"

            companion object {
                private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            }
        }

        override fun query() = choice?.fmt()
    }

    /** The `from:...` operator. */
    class FromOperator(override val choice: FromKind? = null) : SearchOperator {
        /** The specific `from:...` operator in use. */
        sealed interface FromKind {
            val ignore: Boolean
            val q: String

            /** `from:me`, or `-from:me` if [ignore] is true. */
            data class FromMe(override val ignore: Boolean) : FromKind {
                override val q: String
                    get() = if (ignore) "-from:me" else "from:me"
            }

            /**
             * `from:<account>` or `-from:<account>` if [ignore] is true.
             *
             * @param account The account name. Any leading `@` will be removed.
             */
            data class FromAccount(val account: String, override val ignore: Boolean) : FromKind {
                override val q: String
                    get() = if (ignore) "-from:${account.removePrefix("@")}" else "from:${account.removePrefix("@")}"
            }
        }

        override fun query() = choice?.q
    }

    /**
     * The `has:embed` operator.
     *
     * @see HasMediaOperator
     * @see HasPollOperator
     */
    data class HasEmbedOperator(override val choice: EmbedKind? = null) : SearchOperator {
        enum class EmbedKind(val q: String) {
            EMBED_ONLY("has:embed"),
            NO_EMBED("-has:embed"),
        }

        override fun query() = choice?.q
    }

    /**
     * The `language:...` operator.
     *
     * @param choice Restrict results to posts written in [Locale.modernLanguageCode].
     */
    class LanguageOperator(override val choice: Locale? = null) : SearchOperator {
        override fun query() = choice?.let { "language:${it.modernLanguageCode}" }
    }

    /** The `has:link` operator. */
    data class HasLinkOperator(override val choice: LinkKind? = null) : SearchOperator {
        enum class LinkKind(val q: String) {
            LINKS_ONLY("has:link"),
            NO_LINKS("-has:link"),
        }

        override fun query() = choice?.q
    }

    /**
     * The `has:poll` operator.
     *
     * @see HasEmbedOperator
     * @see HasMediaOperator
     */
    data class HasPollOperator(override val choice: PollKind? = null) : SearchOperator {
        enum class PollKind(val q: String) {
            POLLS_ONLY("has:poll"),
            NO_POLLS("-has:poll"),
        }

        override fun query() = choice?.q
    }

    /** The `is:reply` operator. */
    class IsReplyOperator(override val choice: ReplyKind? = null) : SearchOperator {
        enum class ReplyKind(val q: String) {
            REPLIES_ONLY("is:reply"),
            NO_REPLIES("-is:reply"),
        }

        override fun query() = choice?.q
    }

    /** The `is:sensitive` operator. */
    class IsSensitiveOperator(override val choice: SensitiveKind? = null) : SearchOperator {
        // (choice) {
        enum class SensitiveKind(val q: String) {
            SENSITIVE_ONLY("is:sensitive"),
            NO_SENSITIVE("-is:sensitive"),
        }

        override fun query() = choice?.q
    }

    /** The `in:...` operator. */
    class WhereOperator(override val choice: WhereLocation? = null) : SearchOperator {
        enum class WhereLocation(val q: String) {
            LIBRARY("in:library"),
            PUBLIC("in:public"),
        }

        override fun query() = choice?.q
    }
}
