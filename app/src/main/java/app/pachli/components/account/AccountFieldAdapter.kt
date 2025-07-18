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

package app.pachli.components.account

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.model.Emoji
import app.pachli.core.model.Field
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.ui.BindingHolder
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.emojify
import app.pachli.core.ui.setClickableText
import app.pachli.databinding.ItemAccountFieldBinding
import com.bumptech.glide.RequestManager

class AccountFieldAdapter(
    private val glide: RequestManager,
    private val linkListener: LinkListener,
    private val animateEmojis: Boolean,
) : RecyclerView.Adapter<BindingHolder<ItemAccountFieldBinding>>() {

    var emojis: List<Emoji> = emptyList()
    var fields: List<Field> = emptyList()

    override fun getItemCount() = fields.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemAccountFieldBinding> {
        val binding = ItemAccountFieldBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemAccountFieldBinding>, position: Int) {
        val field = fields[position]
        val nameTextView = holder.binding.accountFieldName
        val valueTextView = holder.binding.accountFieldValue

        val emojifiedName = field.name.emojify(glide, emojis, nameTextView, animateEmojis)
        nameTextView.text = emojifiedName

        val emojifiedValue = field.value.parseAsMastodonHtml().emojify(glide, emojis, valueTextView, animateEmojis)
        setClickableText(valueTextView, emojifiedValue, emptyList(), null, linkListener)

        if (field.verifiedAt != null) {
            valueTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_check_circle, 0)
        } else {
            valueTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        }
    }
}
