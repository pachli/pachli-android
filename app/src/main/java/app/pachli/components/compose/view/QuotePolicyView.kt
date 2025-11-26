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

package app.pachli.components.compose.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RadioGroup
import app.pachli.R
import app.pachli.core.model.AccountSource.QuotePolicy
import app.pachli.databinding.ViewQuotePolicyBinding

class QuotePolicyView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RadioGroup(context, attrs) {

    var listener: QuotePolicyListener? = null

    val binding = ViewQuotePolicyBinding.inflate(LayoutInflater.from(context), this)

    init {
        setOnCheckedChangeListener { _, checkedId ->
            val quotePolicy = when (checkedId) {
                R.id.publicRadioButton -> QuotePolicy.PUBLIC
                R.id.followersRadioButton -> QuotePolicy.FOLLOWERS
                R.id.nobodyRadioButton -> QuotePolicy.NOBODY
                else -> QuotePolicy.NOBODY
            }
            listener?.onQuotePolicyChanged(quotePolicy)
        }
    }

    fun setQuotePolicy(quotePolicy: QuotePolicy) {
        when (quotePolicy) {
            QuotePolicy.PUBLIC -> binding.publicRadioButton
            QuotePolicy.FOLLOWERS -> binding.followersRadioButton
            QuotePolicy.NOBODY -> binding.nobodyRadioButton
        }.isChecked = true
    }
}

interface QuotePolicyListener {
    fun onQuotePolicyChanged(quotePolicy: QuotePolicy)
}
