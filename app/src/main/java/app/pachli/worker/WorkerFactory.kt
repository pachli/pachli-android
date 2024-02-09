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

package app.pachli.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import timber.log.Timber

/**
 * Workers implement this and are added to the map in [app.pachli.di.WorkerModule]
 * so they can be created by [WorkerFactory.createWorker].
 */
interface ChildWorkerFactory {
    /** Create a new instance of the given worker. */
    fun createWorker(appContext: Context, params: WorkerParameters): ListenableWorker
}

/**
 * Creates workers, delegating to each worker's [ChildWorkerFactory.createWorker] to do the
 * creation.
 *
 * @see [app.pachli.worker.NotificationWorker]
 */
@Singleton
class WorkerFactory @Inject constructor(
    private val workerFactories: Map<Class<out ListenableWorker>, @JvmSuppressWildcards Provider<ChildWorkerFactory>>,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        val key = try {
            Class.forName(workerClassName)
        } catch (e: ClassNotFoundException) {
            // Class might be missing if it was renamed / moved to a different package, as
            // periodic work requests from before the rename might still exist. Catch and
            // return null, which should stop future requests.
            Timber.d(e, "Invalid class: %s", workerClassName)
            null
        }
        workerFactories[key]?.let {
            return it.get().createWorker(appContext, workerParameters)
        }
        return null
    }
}
