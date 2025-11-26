/* Copyright 2018 Conny Duck
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

package app.pachli.components.compose.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RadioGroup
import app.pachli.R
import app.pachli.core.model.Status
import app.pachli.databinding.ViewComposeOptionsBinding

class ComposeOptionsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RadioGroup(context, attrs) {

    var listener: ComposeVisibilityListener? = null

    val binding = ViewComposeOptionsBinding.inflate(LayoutInflater.from(context), this)

    init {
        setOnCheckedChangeListener { _, checkedId ->
            val visibility = when (checkedId) {
                R.id.publicRadioButton -> Status.Visibility.PUBLIC
                R.id.unlistedRadioButton -> Status.Visibility.UNLISTED
                R.id.privateRadioButton -> Status.Visibility.PRIVATE
                R.id.directRadioButton -> Status.Visibility.DIRECT
                else -> Status.Visibility.PUBLIC
            }
            listener?.onVisibilityChanged(visibility)
        }
    }

    fun disableVisibility(visibility: Status.Visibility) {
        when (visibility) {
            Status.Visibility.UNKNOWN -> Unit
            Status.Visibility.PUBLIC -> binding.publicRadioButton.isEnabled = false
            Status.Visibility.UNLISTED -> binding.unlistedRadioButton.isEnabled = false
            Status.Visibility.PRIVATE -> binding.privateRadioButton.isEnabled = false
            Status.Visibility.DIRECT -> binding.directRadioButton.isEnabled = false
        }
    }

    fun setStatusVisibility(visibility: Status.Visibility) {
        when (visibility) {
            Status.Visibility.PUBLIC -> binding.publicRadioButton
            Status.Visibility.UNLISTED -> binding.unlistedRadioButton
            Status.Visibility.PRIVATE -> binding.privateRadioButton
            Status.Visibility.DIRECT -> binding.directRadioButton
            else -> binding.directRadioButton
        }.isChecked = true
    }
}

interface ComposeVisibilityListener {
    fun onVisibilityChanged(visibility: Status.Visibility)
}
