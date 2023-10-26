/* Copyright 2022 Tusky Contributors
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

package app.pachli.components.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.network.MastodonApi
import at.connyduck.calladapter.networkresult.fold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginWebViewViewModel @Inject constructor(
    private val api: MastodonApi,
) : ViewModel() {

    val instanceRules: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())

    private var domain: String? = null

    fun init(domain: String) {
        if (this.domain == null) {
            this.domain = domain
            viewModelScope.launch {
                api.getInstance(domain).fold({ instance ->
                    instanceRules.value = instance.rules?.map { rule -> rule.text }.orEmpty()
                }, { throwable ->
                    Log.w("LoginWebViewViewModel", "failed to load instance info", throwable)
                },)
            }
        }
    }
}
