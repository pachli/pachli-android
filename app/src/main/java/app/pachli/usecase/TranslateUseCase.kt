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

package app.pachli.usecase

import app.pachli.entity.Translation
import app.pachli.network.MastodonApi
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.fold
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import javax.inject.Inject

sealed interface TranslateUseCaseError {
    val throwable: Throwable

    data class Unknown(override val throwable: Throwable) : TranslateUseCaseError
}

class TranslateUseCase @Inject constructor(
    private val mastodonApi: MastodonApi,
) {
    suspend operator fun invoke(statusId: String): Result<Translation, TranslateUseCaseError> {
        return mastodonApi.translate(statusId).okOrElse { TranslateUseCaseError.Unknown(it) }
    }
}

inline fun <T, E> NetworkResult<T>.okOrElse(onFailure: (exception: Throwable) -> E): Result<T, E> {
    return fold(onSuccess = { Ok(it) }, onFailure = { Err(onFailure(it))} )
}
