/* Copyright 2017 Andrew Dawson
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

package app.pachli.pager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import app.pachli.TabViewData
import app.pachli.core.activity.CustomFragmentStateAdapter

class MainPagerAdapter(var tabs: List<TabViewData>, activity: FragmentActivity) : CustomFragmentStateAdapter(activity) {

    override fun createFragment(position: Int): Fragment {
        val tab = tabs[position]
        return tab.fragment()
    }

    override fun getItemCount() = tabs.size
}
