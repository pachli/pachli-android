/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.components.preference.accountfilters

import android.os.Bundle
import app.pachli.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccountConversationFiltersPreferenceDialogFragment :
    BaseAccountFiltersPreferencesDialogFragment(
        AccountFilterTimeline.CONVERSATIONS,
        R.string.pref_title_account_conversation_filters,
        R.string.pref_account_conversation_filters_subtitle,
    ) {

    companion object {
        fun newInstance(
            pachliAccountId: Long,
        ): AccountConversationFiltersPreferenceDialogFragment {
            val fragment = AccountConversationFiltersPreferenceDialogFragment()
            fragment.arguments = Bundle(1).apply {
                putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
            }
            return fragment
        }
    }
}
