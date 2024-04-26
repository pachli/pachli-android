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

package app.pachli.components.filters

import android.app.Activity
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import app.pachli.R
import app.pachli.core.network.model.Filter
import app.pachli.core.ui.extensions.await

internal suspend fun Activity.showDeleteFilterDialog(filterTitle: String) = AlertDialog.Builder(this)
    .setMessage(getString(R.string.dialog_delete_filter_text, filterTitle))
    .setCancelable(true)
    .create()
    .await(R.string.dialog_delete_filter_positive_action, android.R.string.cancel)

/** Reasons why a filter might be invalid */
enum class FilterValidationError {
    /** Filter title is empty or blank */
    NO_TITLE,

    /** Filter has no keywords */
    NO_KEYWORDS,

    /** Filter has no contexts */
    NO_CONTEXT,
}

/**
 * @return Set of validation errors for this filter, empty set if there
 *   are no errors.
 */
fun Filter.validate() = buildSet {
    if (title.isBlank()) add(FilterValidationError.NO_TITLE)
    if (keywords.isEmpty()) add(FilterValidationError.NO_KEYWORDS)
    if (contexts.isEmpty()) add(FilterValidationError.NO_CONTEXT)
}

/**
 * @return String resource containing an error message for this
 *   validation error.
 */
@StringRes
fun FilterValidationError.stringResource() = when (this) {
    FilterValidationError.NO_TITLE -> R.string.error_filter_missing_title
    FilterValidationError.NO_KEYWORDS -> R.string.error_filter_missing_keyword
    FilterValidationError.NO_CONTEXT -> R.string.error_filter_missing_context
}
