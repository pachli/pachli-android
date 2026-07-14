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

package app.pachli.core.data

import app.pachli.core.model.ICollection
import app.pachli.core.model.TimelineCollection
import app.pachli.core.model.collection.CollectionDisplayAction

/**
 * @property timelineCollection
 * @property displayAction How to display the collection.
 * @property isMember True if [timelineCollection] contains the current user.
 */
data class CollectionCardViewData(
    val timelineCollection: TimelineCollection,
    val displayAction: CollectionDisplayAction,
    val isMember: Boolean,
) : ICollection by timelineCollection
