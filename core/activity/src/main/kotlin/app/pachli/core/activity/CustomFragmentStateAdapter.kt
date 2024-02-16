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

package app.pachli.core.activity

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * A [FragmentStateAdapter] that provides [getFragment].
 *
 * Useful if the hosting activity or fragment needs to call methods on
 * one or more of the child fragments (e.g., to trigger a refresh).
 */
abstract class CustomFragmentStateAdapter(
    open val fragmentManager: FragmentManager,
    open val lifecycle: Lifecycle,
) : FragmentStateAdapter(fragmentManager, lifecycle) {
    constructor(activity: FragmentActivity) :
        this(activity.supportFragmentManager, activity.lifecycle)

    constructor(fragment: Fragment) :
        this(fragment.childFragmentManager, fragment.lifecycle)

    fun getFragment(position: Int): Fragment? =
        fragmentManager.findFragmentByTag("f" + getItemId(position))
}
