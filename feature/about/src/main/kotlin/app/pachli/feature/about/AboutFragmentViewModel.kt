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

package app.pachli.feature.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.InstanceInfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AboutFragmentViewModel @Inject constructor(
    private val application: Application,
    private val accountManager: AccountManager,
    private val instanceInfoRepository: InstanceInfoRepository,
) : AndroidViewModel(application) {
    private val _accountInfo = MutableSharedFlow<String?>()
    val accountInfo = _accountInfo.asSharedFlow()

    init {
        viewModelScope.launch {
            instanceInfoRepository.instanceInfo.collect { instanceInfo ->
                val account = accountManager.activeAccount
                if (account == null) {
                    _accountInfo.emit("")
                    return@collect
                }

                _accountInfo.emit(
                    application.getString(
                        R.string.about_account_info,
                        account.username,
                        account.domain,
                        instanceInfo.version,
                    ),
                )
            }
        }
    }
}
