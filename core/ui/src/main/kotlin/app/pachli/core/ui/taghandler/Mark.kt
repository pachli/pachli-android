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

package app.pachli.core.ui.taghandler

/** Spans used to mark the start of HTML tags. */
interface Mark {
    /** Marks the opening tag location of a `code` element. */
    object Code : Mark

    /** Marks the opening tag location of a `blockquote` element. */
    // `class` instead of `object`, as these can be nested, as repeatedly
    // setting an `object` span just moves it around.
    class BlockQuote : Mark

    /** Marks the opening tag location of a `pre` element. */
    object Pre : Mark

    /** Marks the opening tag location of an `li` element. */
    interface ListItem : Mark {
        /**
         * Marks the opening tag location of an `li` element in a `ol` element.
         *
         * @property number The list item's 1-based position in the list.
         */
        class OrderedListItem(val number: Int) : ListItem

        /** Marks the opening tag location of an `li` element in a `ul` element. */
        class UnorderedListItem : ListItem
    }
}
