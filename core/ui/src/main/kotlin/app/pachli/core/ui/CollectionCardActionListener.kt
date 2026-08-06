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

import app.pachli.core.model.ICollection
import app.pachli.core.model.collection.CollectionCardViewData
import app.pachli.core.model.collection.CollectionDisplayAction

/**
 * Actions the user can take on a [CollectionCardView].
 *
 * See
 *
 * - [OnViewCollection.onViewCollection]
 * - [OnRevokeUserFromCollection.onRevokeUserFromCollection]
 * - [OnCollectionDisplayActionChange.onCollectionDisplayActionChange]
 * - [OnViewTag.onViewTag]
 */
interface CollectionCardActionListener :
    OnViewCollection,
    OnRevokeUserFromCollection,
    OnCollectionDisplayActionChange,
    OnViewTag

/** See [onViewCollection]. */
fun interface OnViewCollection {
    /**
     * Function to call when the user wants to view a collection.
     *
     * @param collection [ICollection] to view.
     */
    fun onViewCollection(collection: ICollection)
}

/** See [onRevokeUserFromCollection]. */
fun interface OnRevokeUserFromCollection {
    /**
     * Function to call when the user wants to revoke their
     * membership of a collection.
     *
     * @param collection [ICollection] to revoke the user from.
     */
    fun onRevokeUserFromCollection(collection: ICollection)
}

/** See [onCollectionDisplayActionChange]. */
fun interface OnCollectionDisplayActionChange {
    /**
     * Function to call when the user wants to change how a collection
     * is displayed.
     *
     * @param viewData
     * @param collectionDisplayAction The new [CollectionDisplayAction]
     */
    fun onCollectionDisplayActionChange(viewData: CollectionCardViewData, collectionDisplayAction: CollectionDisplayAction)
}
