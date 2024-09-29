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

package app.pachli.core.domain.lists

import app.pachli.core.data.repository.AccountManager
import javax.inject.Inject

class GetListsUseCase @Inject constructor(
    private val accountManager: AccountManager,
) {
    /**
     * @return A [Flow] of [List<MastodonList>][app.pachli.core.data.repository.MastodonList]
     * for [accountId]. Any changes to the lists (new lists, updates, deletions) emit a new
     * value in to the flow.
     *
     * Collecting this flow **does not** trigger a remote fetch. See [RefreshListsUseCase].
     */
    operator fun invoke(accountId: Long) = accountManager.getLists(accountId)
}
