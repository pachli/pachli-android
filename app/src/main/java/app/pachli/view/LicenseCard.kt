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

package app.pachli.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.content.res.use
import app.pachli.core.activity.openLink
import app.pachli.core.common.extensions.hide
import app.pachli.core.designsystem.R as DR
import app.pachli.databinding.CardLicenseBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class LicenseCard
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialCardView(context, attrs, defStyleAttr) {

    init {
        val binding = CardLicenseBinding.inflate(LayoutInflater.from(context), this)

        setCardBackgroundColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.BLACK))

        val (name, license, link) = context.theme.obtainStyledAttributes(
            attrs,
            DR.styleable.LicenseCard,
            0,
            0,
        ).use { a ->
            Triple(
                a.getString(DR.styleable.LicenseCard_name),
                a.getString(DR.styleable.LicenseCard_license),
                a.getString(DR.styleable.LicenseCard_link),
            )
        }

        binding.licenseCardName.text = name
        binding.licenseCardLicense.text = license
        if (link.isNullOrBlank()) {
            binding.licenseCardLink.hide()
        } else {
            binding.licenseCardLink.text = link
            setOnClickListener { context.openLink(link) }
        }
    }
}
