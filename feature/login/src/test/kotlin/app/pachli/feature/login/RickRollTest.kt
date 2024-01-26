/*
 * Copyright 2023 Pachli Association
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

package app.pachli.feature.login

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class RickRollTest {
    private lateinit var activity: Activity

    @Before
    fun setupActivity() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        activity = controller.get()
    }

    @Test
    fun testShouldRickRoll() {
        listOf("gab.Com", "social.gab.ai", "whatever.GAB.com").forEach {
                rollableDomain ->
            assertTrue(shouldRickRoll(activity, rollableDomain))
        }

        listOf("chaos.social", "notgab.com").forEach {
                notRollableDomain ->
            assertFalse(shouldRickRoll(activity, notRollableDomain))
        }
    }
}
