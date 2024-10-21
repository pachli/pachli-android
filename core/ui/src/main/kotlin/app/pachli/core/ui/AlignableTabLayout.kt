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

package app.pachli.core.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.text.TextUtilsCompat
import androidx.core.view.children
import app.pachli.core.ui.AlignableTabLayoutAlignment.END
import app.pachli.core.ui.AlignableTabLayoutAlignment.JUSTIFY_IF_POSSIBLE
import app.pachli.core.ui.AlignableTabLayoutAlignment.START
import com.google.android.material.tabs.TabLayout
import java.util.Locale

/** How to align tabs. */
enum class AlignableTabLayoutAlignment {
    /** Tabs align with start of writing direction. */
    START,

    /** Tabs expand to full width if possible. */
    JUSTIFY_IF_POSSIBLE,

    /** Tabs align with end of writing direction. */
    END,
}

/**
 * Specalised [TabLayout] that can align the tabs.
 *
 * Ignores [setTabMode] in favour of [alignment].
 *
 * [START] is equivalent to setting tabMode to [TabLayout.MODE_SCROLLABLE].
 *
 * [JUSTIFY_IF_POSSIBLE] uses [TabLayout.MODE_SCROLLABLE] if there is not
 * enough space to show all tabs, and [TabLayout.MODE_FIXED] if there is.
 * Effectively justifying the tabs.
 *
 * [END] is equivalent to [START], but adds additional left or right
 * padding (depending on the text direction) to push the start of the tabs
 * to the correct end of the layout.
 */
class AlignableTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.tabStyle,
) : TabLayout(context, attrs, defStyleAttr) {
    var alignment: AlignableTabLayoutAlignment = START
        set(value) {
            field = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        when (alignment) {
            START -> {
                tabMode = MODE_SCROLLABLE
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }

            JUSTIFY_IF_POSSIBLE -> {
                tabMode = MODE_SCROLLABLE
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)

                if (tabCount < 2) return

                val tabLayout = getChildAt(0) as ViewGroup
                val totalWidth = tabLayout.children.fold(0) { i, v -> i + v.measuredWidth }

                if (totalWidth <= measuredWidth) {
                    tabMode = MODE_FIXED
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }

            END -> {
                tabMode = MODE_SCROLLABLE
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)

                val tabLayout = getChildAt(0) as ViewGroup
                val totalWidth = tabLayout.children.fold(0) { i, v -> i + v.measuredWidth }

                if (totalWidth < measuredWidth) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

                    val isLeftToRight = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_LTR

                    val padding = measuredWidth - totalWidth
                    if (isLeftToRight) {
                        tabLayout.setPadding(padding, 0, 0, 0)
                    } else {
                        tabLayout.setPadding(0, 0, padding, 0)
                    }
                }
            }
        }
    }
}
