/* Copyright 2023 Andi McClure
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

package app.pachli.db

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.pachli.R
import app.pachli.core.accounts.AccountManager
import app.pachli.core.database.dao.DraftDao
import app.pachli.core.navigation.DraftsActivityIntent
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This class manages an alert popup when a post has failed and been saved to drafts.
 * It must be separately registered in each lifetime in which it is to appear,
 * and it only appears if the post failure belongs to the current user.
 */

@Singleton
class DraftsAlert @Inject constructor(private val draftDao: DraftDao) {
    @Inject
    lateinit var accountManager: AccountManager

    fun <T> observeInContext(context: T, showAlert: Boolean) where T : Context, T : LifecycleOwner {
        accountManager.activeAccount?.let { activeAccount ->
            val coroutineScope = context.lifecycleScope

            // Assume a single MainActivity, AccountActivity or DraftsActivity never sees more then one user id in its lifetime.
            val activeAccountId = activeAccount.id

            // This LiveData will be automatically disposed when the activity is destroyed.
            val draftsNeedUserAlert = draftDao.draftsNeedUserAlert(activeAccountId)

            // observe ensures that this gets called at the most appropriate moment wrt the context lifecycle—
            // at init, at next onResume, or immediately if the context is resumed already.
            if (showAlert) {
                draftsNeedUserAlert.observe(context) { count ->
                    Timber.d("User id $activeAccountId changed: Notification-worthy draft count $count")
                    if (count > 0) {
                        AlertDialog.Builder(context)
                            .setTitle(R.string.action_post_failed)
                            .setMessage(
                                context.resources.getQuantityString(R.plurals.action_post_failed_detail, count),
                            )
                            .setPositiveButton(R.string.action_post_failed_show_drafts) { _: DialogInterface?, _: Int ->
                                clearDraftsAlert(coroutineScope, activeAccountId) // User looked at drafts

                                val intent = DraftsActivityIntent(context)
                                context.startActivity(intent)
                            }
                            .setNegativeButton(R.string.action_post_failed_do_nothing) { _: DialogInterface?, _: Int ->
                                clearDraftsAlert(coroutineScope, activeAccountId) // User doesn't care
                            }
                            .show()
                    }
                }
            } else {
                draftsNeedUserAlert.observe(context) {
                    Timber.d("User id $activeAccountId: Clean out notification-worthy drafts")
                    clearDraftsAlert(coroutineScope, activeAccountId)
                }
            }
        } ?: run {
            Timber.w("Attempted to observe drafts, but there is no active account")
        }
    }

    /**
     * Clear drafts alert for specified user
     */
    private fun clearDraftsAlert(coroutineScope: LifecycleCoroutineScope, id: Long) {
        coroutineScope.launch {
            draftDao.draftsClearNeedUserAlert(id)
        }
    }
}
