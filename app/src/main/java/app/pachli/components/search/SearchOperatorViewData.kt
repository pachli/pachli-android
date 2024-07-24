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

import android.content.Context
import androidx.annotation.StringRes
import app.pachli.R
import app.pachli.components.search.SearchOperator.DateOperator
import app.pachli.components.search.SearchOperator.FromOperator
import app.pachli.components.search.SearchOperator.HasEmbedOperator
import app.pachli.components.search.SearchOperator.HasLinkOperator
import app.pachli.components.search.SearchOperator.HasMediaOperator
import app.pachli.components.search.SearchOperator.HasMediaOperator.HasMediaOption.HasMedia
import app.pachli.components.search.SearchOperator.HasMediaOperator.HasMediaOption.NoMedia
import app.pachli.components.search.SearchOperator.HasMediaOperator.HasMediaOption.SpecificMedia
import app.pachli.components.search.SearchOperator.HasMediaOperator.MediaKind
import app.pachli.components.search.SearchOperator.HasPollOperator
import app.pachli.components.search.SearchOperator.IsReplyOperator
import app.pachli.components.search.SearchOperator.IsSensitiveOperator
import app.pachli.components.search.SearchOperator.LanguageOperator
import app.pachli.components.search.SearchOperator.WhereOperator
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Wrapper for [SearchOperator] that includes additional information to show the
 * operator as a chip in the UI.
 */
sealed interface SearchOperatorViewData<out T : SearchOperator> {
    /** Underlying [SearchOperator]. */
    val operator: T

    /** @return The label to use on the chip for [operator]. */
    fun chipLabel(context: Context): String

    companion object {
        /** @return The correct [SearchOperatorViewData] for the [operator]. */
        fun from(operator: SearchOperator) = when (operator) {
            is HasMediaOperator -> HasMediaOperatorViewData(operator)
            is DateOperator -> DateOperatorViewData(operator)
            is FromOperator -> FromOperatorViewData(operator)
            is HasEmbedOperator -> HasEmbedOperatorViewData(operator)
            is LanguageOperator -> LanguageOperatorViewData(operator)
            is HasLinkOperator -> HasLinkOperatorViewData(operator)
            is HasPollOperator -> HasPollOperatorViewData(operator)
            is IsReplyOperator -> IsReplyOperatorViewData(operator)
            is IsSensitiveOperator -> IsSensitiveOperatorViewData(operator)
            is WhereOperator -> WhereOperatorViewData(operator)
        }
    }

    data class HasMediaOperatorViewData(override val operator: HasMediaOperator) : SearchOperatorViewData<HasMediaOperator> {
        override fun chipLabel(context: Context): String {
            fun MediaKind.label() = context.getString(this.stringResource)

            return when (val choice = operator.choice) {
                null -> context.getString(R.string.search_operator_attachment_all)
                NoMedia -> context.getString(R.string.search_operator_attachment_no_media_label)
                is HasMedia -> {
                    val exclude = choice.exclude
                    if (exclude.isEmpty()) {
                        context.getString(R.string.search_operator_attachment_has_media_label)
                    } else {
                        when (exclude.size) {
                            1 -> context.getString(
                                R.string.search_operator_attachment_has_media_except_1_label_fmt,
                                exclude[0].label(),
                            )

                            2 -> context.getString(
                                R.string.search_operator_attachment_has_media_except_2_label_fmt,
                                exclude[0].label(),
                                exclude[1].label(),
                            )

                            else -> context.getString(
                                R.string.search_operator_attachment_has_media_except_3_label_fmt,
                                exclude[0].label(),
                                exclude[1].label(),
                                exclude[2].label(),
                            )
                        }
                    }
                }

                is SpecificMedia -> {
                    val include = choice.include
                    val exclude = choice.exclude

                    when {
                        // With and except
                        include.isNotEmpty() && exclude.isNotEmpty() -> when {
                            // Include 2, exclude 1
                            include.size == 2 -> context.getString(
                                R.string.search_operator_attachment_specific_media_include_2_exclude_1_fmt,
                                include[0].label(),
                                include[1].label(),
                                exclude[0].label(),
                            )

                            // Include 1, exclude 2
                            include.size == 1 && exclude.size == 2 -> context.getString(
                                R.string.search_operator_attachment_specific_media_include_1_exclude_2_fmt,
                                include[0].label(),
                                exclude[0].label(),
                                exclude[1].label(),
                            )

                            // Include 1, exclude 1
                            else -> context.getString(
                                R.string.search_operator_attachment_specific_media_include_1_exclude_1_fmt,
                                include[0].label(),
                                exclude[0].label(),
                            )
                        }
                        // Include only
                        include.isNotEmpty() -> when (include.size) {
                            1 -> context.getString(
                                R.string.search_operator_attachment_specific_media_include_1_fmt,
                                include[0].label(),
                            )

                            2 -> context.getString(
                                R.string.search_operator_attachment_specific_media_include_2_fmt,
                                include[0].label(),
                                include[1].label(),
                            )

                            else -> context.getString(
                                R.string.search_operator_attachment_specific_media_include_3_fmt,
                                include[0].label(),
                                include[1].label(),
                                include[2].label(),
                            )
                        }
                        // exclude only
                        else -> when (exclude.size) {
                            1 -> context.getString(
                                R.string.search_operator_attachment_specific_media_exclude_1_fmt,
                                exclude[0].label(),
                            )

                            2 -> context.getString(
                                R.string.search_operator_attachment_specific_media_exclude_2_fmt,
                                exclude[0].label(),
                                exclude[1].label(),
                            )

                            else -> context.getString(
                                R.string.search_operator_attachment_specific_media_exclude_3_fmt,
                                exclude[0].label(),
                                exclude[1].label(),
                                exclude[2].label(),
                            )
                        }
                    }
                }
            }
        }

        @get:StringRes
        val MediaKind.stringResource: Int
            get() = when (this) {
                MediaKind.IMAGE -> R.string.search_operator_attachment_kind_image_label
                MediaKind.VIDEO -> R.string.search_operator_attachment_kind_video_label
                MediaKind.AUDIO -> R.string.search_operator_attachment_kind_audio_label
            }
    }

    data class DateOperatorViewData(override val operator: DateOperator) : SearchOperatorViewData<DateOperator> {
        private val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

        override fun chipLabel(context: Context) = when (val choice = operator.choice) {
            null -> context.getString(R.string.search_operator_date_all)
            DateOperator.DateChoice.Today -> context.getString(R.string.search_operator_date_dialog_today)
            DateOperator.DateChoice.Last7Days -> context.getString(R.string.search_operator_date_dialog_last_7_days)
            DateOperator.DateChoice.Last30Days -> context.getString(R.string.search_operator_date_dialog_last_30_days)
            DateOperator.DateChoice.Last6Months -> context.getString(R.string.search_operator_date_dialog_last_6_months)
            is DateOperator.DateChoice.DateRange -> {
                if (choice.startDate == choice.endDate) {
                    context.getString(
                        R.string.search_operator_date_checked_same_day,
                        formatter.format(choice.startDate),
                    )
                } else {
                    context.getString(
                        R.string.search_operator_date_checked,
                        formatter.format(choice.startDate),
                        formatter.format(choice.endDate),
                    )
                }
            }
        }
    }

    data class HasEmbedOperatorViewData(override val operator: HasEmbedOperator) : SearchOperatorViewData<HasEmbedOperator> {
        override fun chipLabel(context: Context) = context.getString(
            when (operator.choice) {
                null -> R.string.search_operator_embed_all
                HasEmbedOperator.EmbedKind.EMBED_ONLY -> R.string.search_operator_embed_only
                HasEmbedOperator.EmbedKind.NO_EMBED -> R.string.search_operator_embed_no_embeds
            },
        )
    }

    data class FromOperatorViewData(override val operator: FromOperator) : SearchOperatorViewData<FromOperator> {
        override fun chipLabel(context: Context) = when (val choice = operator.choice) {
            null -> context.getString(R.string.search_operator_from_all)
            is FromOperator.FromKind.FromMe -> context.getString(
                if (choice.ignore) R.string.search_operator_from_ignore_me else R.string.search_operator_from_me,
            )

            is FromOperator.FromKind.FromAccount -> context.getString(
                if (choice.ignore) {
                    R.string.search_operator_from_ignore_account_fmt
                } else {
                    R.string.search_operator_from_account_fmt
                },
                choice.account,
            )
        }
    }

    data class LanguageOperatorViewData(override val operator: LanguageOperator) : SearchOperatorViewData<LanguageOperator> {
        override fun chipLabel(context: Context) = when (operator.choice) {
            null -> context.getString(R.string.search_operator_language_all)
            else -> context.getString(
                R.string.search_operator_language_checked_fmt,
                operator.choice.displayLanguage,
            )
        }
    }

    data class HasLinkOperatorViewData(override val operator: HasLinkOperator) : SearchOperatorViewData<HasLinkOperator> {
        override fun chipLabel(context: Context) = context.getString(
            when (operator.choice) {
                null -> R.string.search_operator_link_all
                HasLinkOperator.LinkKind.LINKS_ONLY -> R.string.search_operator_link_only
                HasLinkOperator.LinkKind.NO_LINKS -> R.string.search_operator_no_link
            },
        )
    }

    data class HasPollOperatorViewData(override val operator: HasPollOperator) : SearchOperatorViewData<HasPollOperator> {
        override fun chipLabel(context: Context) = context.getString(
            when (operator.choice) {
                null -> R.string.search_operator_poll_all
                HasPollOperator.PollKind.POLLS_ONLY -> R.string.search_operator_poll_only
                HasPollOperator.PollKind.NO_POLLS -> R.string.search_operator_poll_no_polls
            },
        )
    }

    data class IsReplyOperatorViewData(override val operator: IsReplyOperator) : SearchOperatorViewData<IsReplyOperator> {
        override fun chipLabel(context: Context) = when (operator.choice) {
            null -> context.getString(R.string.search_operator_replies_all)
            IsReplyOperator.ReplyKind.REPLIES_ONLY -> context.getString(R.string.search_operator_replies_replies_only)
            IsReplyOperator.ReplyKind.NO_REPLIES -> context.getString(R.string.search_operator_replies_no_replies)
        }
    }

    data class IsSensitiveOperatorViewData(override val operator: IsSensitiveOperator) : SearchOperatorViewData<IsSensitiveOperator> {
        override fun chipLabel(context: Context) = when (operator.choice) {
            null -> context.getString(R.string.search_operator_sensitive_all)
            IsSensitiveOperator.SensitiveKind.SENSITIVE_ONLY -> context.getString(R.string.search_operator_sensitive_sensitive_only)
            IsSensitiveOperator.SensitiveKind.NO_SENSITIVE -> context.getString(R.string.search_operator_sensitive_no_sensitive)
        }
    }

    data class WhereOperatorViewData(override val operator: WhereOperator) : SearchOperatorViewData<WhereOperator> {
        override fun chipLabel(context: Context) = when (operator.choice) {
            null -> context.getString(R.string.search_operator_where_all)
            WhereOperator.WhereLocation.LIBRARY -> context.getString(R.string.search_operator_where_library)
            WhereOperator.WhereLocation.PUBLIC -> context.getString(R.string.search_operator_where_public)
        }
    }
}
