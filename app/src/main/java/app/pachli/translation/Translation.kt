/*
 * Copyright 2025 Pachli Association
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

package app.pachli.translation

import app.pachli.core.common.PachliError
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.model.Status
import app.pachli.core.model.translation.TranslatedStatus
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapEither

/**
 * Errors that can occur when translating a status.
 */
sealed interface TranslatorError : PachliError {
    @JvmInline
    value class ApiError(val error: app.pachli.core.network.retrofit.apiresult.ApiError) : TranslatorError, PachliError by error
}

interface TranslationService {
    /**
     * @return True if this [TranslationService] can translate [statusViewData].
     * Does not indicate if the translation will be successful, only that the
     * translation option should be visible in the UI.
     */
    fun canTranslate(statusViewData: IStatusViewData): Boolean

    /**
     * Translates some/all of the content of [statusViewData]. The precise content
     * translated depends on the implementation.
     */
    suspend fun translate(statusViewData: IStatusViewData): Result<TranslatedStatus, TranslatorError>
}

/**
 * [TranslationService] that translates using the translation API provided by
 * the user's server.
 */
class ServerTranslationService(
    private val mastodonApi: MastodonApi,
) : TranslationService {
    override fun canTranslate(statusViewData: IStatusViewData) = statusViewData.actionable.visibility != Status.Visibility.PRIVATE &&
        statusViewData.actionable.visibility != Status.Visibility.DIRECT

    override suspend fun translate(statusViewData: IStatusViewData) = mastodonApi.translate(statusViewData.actionableId).mapEither(
        { it.body.toModel() },
        { TranslatorError.ApiError(it) },
    )
}
