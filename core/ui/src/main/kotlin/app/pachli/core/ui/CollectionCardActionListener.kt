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

import app.pachli.core.data.CollectionCardViewData
import app.pachli.core.model.ICollection
import app.pachli.core.model.collection.CollectionDisplayAction

/** Actions the user can take on a [CollectionCardView]. */
interface CollectionCardActionListener :
    OnOpenCollection,
    OnRemoveUserFromCollection,
    OnCollectionDisplayActionChange,
    OnViewTag

fun interface OnOpenCollection {
    fun onOpenCollection(collection: ICollection)
}
fun interface OnRemoveUserFromCollection {
    fun onRemoveUserFromCollection(collection: ICollection)
}

fun interface OnCollectionDisplayActionChange {
    /**
     * Function to call when the user wants to change how a collection
     * is displayed.
     *
     * @param viewData
     * @param action The new [CollectionDisplayAction]
     */
    fun onCollectionDisplayActionChange(viewData: CollectionCardViewData, action: CollectionDisplayAction)
}
