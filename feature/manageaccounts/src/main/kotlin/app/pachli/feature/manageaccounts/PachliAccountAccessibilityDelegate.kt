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

package app.pachli.feature.manageaccounts

import android.os.Bundle
import android.view.View
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.ui.accessibility.PachliRecyclerViewAccessibilityDelegate

/**
 * Accessibility delegate for items in [PachliAccountAdapter].
 *
 * Each item shows actions to:
 *
 * - Log out the selected account.
 * - (if account is not active) Switch to this account.
 */
internal class PachliAccountAccessibilityDelegate(
    private val recyclerView: RecyclerView,
    private val accept: onUiAction,
) : PachliRecyclerViewAccessibilityDelegate(recyclerView) {

    override fun getItemDelegate() = delegate

    private val delegate = object : ItemDelegate(this) {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)

            val viewHolder = recyclerView.findContainingViewHolder(host) as? PachliAccountViewHolder ?: return

            info.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    R.id.action_logout_account,
                    context.getString(R.string.action_logout_account_fmt, viewHolder.account.entity.fullName),
                ),
            )
            if (!viewHolder.account.entity.isActive) {
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.action_switch_account,
                        context.getString(R.string.action_switch_account_fmt, viewHolder.account.entity.fullName),
                    ),
                )
            }
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            val viewHolder = recyclerView.findContainingViewHolder(host) as? PachliAccountViewHolder ?: return false
            val account = viewHolder.account

            return when (action) {
                R.id.action_logout_account -> {
                    interrupt()
                    accept(UiAction.Logout(account))
                    true
                }

                R.id.action_switch_account -> {
                    interrupt()
                    accept(UiAction.Switch(account))
                    true
                }

                else -> super.performAccessibilityAction(host, action, args)
            }
        }
    }
}
