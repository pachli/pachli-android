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

package app.pachli.core.model.collection

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

/**
 * The original reason
 */
// The `type` property here, and having to pass it when defining the subclasses
// is to work around https://github.com/ZacSweers/MoshiX/issues/775.
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed class CollectionDisplayReason(val type: String) {
    /** The collection is marked sensitive. */
    @TypeLabel(label = "sensitive")
    data object Sensitive : CollectionDisplayReason("sensitive")

    /** The user hid the collection using the UI. */
    @TypeLabel("userAction")
    data object UserAction : CollectionDisplayReason("userAction")
}

/**
 * How a collection should be displayed.
 *
 * See [Show], [Hide].
 */
@JsonClass(generateAdapter = true, generator = "sealed:type")
sealed interface CollectionDisplayAction {
    /** Show the collection. */
    @TypeLabel("show")
    @JsonClass(generateAdapter = true)
    data class Show(val originalAction: Hide? = null) : CollectionDisplayAction

    /**
     * The collection details should be hidden and replaced with the
     * collection's name, a warning, and a mechanism to dismiss the
     * warning.
     */
    @TypeLabel("hide")
    @JsonClass(generateAdapter = true)
    data class Hide(val reason: CollectionDisplayReason) : CollectionDisplayAction
}

fun CollectionDisplayAction?.make(
    sensitive: Boolean,
    showSensitive: Boolean,
): CollectionDisplayAction {
    this?.let { return it }

    if (sensitive && !showSensitive) {
        return CollectionDisplayAction.Hide(reason = CollectionDisplayReason.Sensitive)
    }

    if (sensitive) {
        return CollectionDisplayAction.Show(
            originalAction = CollectionDisplayAction.Hide(
                reason = CollectionDisplayReason.Sensitive,
            ),
        )
    }

    return CollectionDisplayAction.Show()
}
