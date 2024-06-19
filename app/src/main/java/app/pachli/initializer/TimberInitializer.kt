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

package app.pachli.initializer

import android.content.Context
import androidx.startup.Initializer
import app.pachli.BuildConfig
import app.pachli.core.activity.LogEntryTree
import app.pachli.core.activity.TreeRing
import app.pachli.di.InitializerEntryPoint
import javax.inject.Inject
import timber.log.Timber

/** Initialise [Timber]. */
class TimberInitializer : Initializer<Unit> {
    @Inject
    lateinit var logEntryTree: LogEntryTree

    override fun create(context: Context) {
        InitializerEntryPoint.resolve(context).inject(this)

        when {
            BuildConfig.DEBUG -> Timber.plant(Timber.DebugTree())
            BuildConfig.FLAVOR_color == "orange" -> Timber.plant(TreeRing)
        }
        Timber.plant(logEntryTree)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(DependencyGraphInitializer::class.java)
    }
}
