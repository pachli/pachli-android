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

package app.pachli.core.ui.accessibility

import android.content.Context
import android.text.Spannable
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.URLSpan
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.text.getSpans
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate

/** Base class for Pachli-specific [RecyclerViewAccessibilityDelegate]s. */
abstract class PachliRecyclerViewAccessibilityDelegate(
    recyclerView: RecyclerView,
) : RecyclerViewAccessibilityDelegate(recyclerView) {
    protected val context: Context = recyclerView.context

    private val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
        as AccessibilityManager

    /**
     * Shows a dialog with [title] displaying a list of [items].
     *
     * Each row in the list shows the item and a "Copy" button to make it easier
     * for assistive technologies to copy the item.
     *
     * Focus is set to the list after showing the dialog.
     */
    fun showA11yDialogWithCopyButton(@StringRes title: Int, items: List<CharSequence>, listener: ArrayAdapterWithCopyButton.OnClickListener) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setAdapter(ArrayAdapterWithCopyButton(context, items, listener), null)
            .show()
            .let { forceFocus(it.listView) }
    }

    /** Interrupts the accessibility service and sets focus to [view]. */
    protected fun forceFocus(view: View) {
        interrupt()
        view.post {
            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
        }
    }

    /** Requests feedback interruption from all accessibility services. */
    protected fun interrupt() = a11yManager.interrupt()

    companion object {
        /** @return The text enclosed by [span]. */
        @JvmStatic
        protected fun Spanned.subSequence(span: CharacterStyle) =
            subSequence(getSpanStart(span), getSpanEnd(span))

        /** @return Links, excluding any links that are hashtags or @-mentions. */
        @JvmStatic
        protected fun Spanned.getLinks(): List<URLSpan> {
            if (this !is Spannable) return emptyList()

            return getSpans<URLSpan>(0, length)
                .mapNotNull { span ->
                    val text = subSequence(span)
                    if (text.isHashtag() || text.isMention()) null else span
                }
        }

        /** @return The text of the linked hashtags (without the leading '#'). */
        @JvmStatic
        protected fun Spanned.getHashtags(): List<CharSequence> = getSpans<URLSpan>(0, length)
            .map { span -> subSequence(span).toString() }
            .filter { it.isHashtag() }
            .map { it.removePrefix("\u2068").removePrefix("#") }

        /**
         * @return True if this is a hashtag (starts with `#` or `#` preceded by
         * the directional isolate added by [StringUtils.unicodeWrap]).
         */
        @JvmStatic
        protected fun CharSequence.isHashtag() = startsWith("#") ||
            startsWith("\u2068#")

        /**
         * @return True if this is a mention (starts with `@` or `@` preceded by
         * the directional isolate added by [StringUtils.unicodeWrap]).
         */
        @JvmStatic
        protected fun CharSequence.isMention() = startsWith("@") ||
            startsWith("\u2068@")
    }
}
