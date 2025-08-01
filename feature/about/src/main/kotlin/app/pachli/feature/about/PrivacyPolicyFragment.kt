/*
 * Copyright 2023 Pachli Association
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

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.ui.extensions.InsetType
import app.pachli.core.ui.extensions.applyWindowInsets
import app.pachli.feature.about.databinding.FragmentPrivacyPolicyBinding
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import java.io.IOException

@AndroidEntryPoint
class PrivacyPolicyFragment : Fragment(R.layout.fragment_privacy_policy) {
    private val binding by viewBinding(FragmentPrivacyPolicyBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.policy.applyWindowInsets(bottom = InsetType.MARGIN)

        val context = requireContext()

        val markwon = Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .build()

        try {
            context.assets.open("PRIVACY.md").use {
                val buffer = ByteArray(it.available())
                it.read(buffer)
                markwon.setMarkdown(binding.policy, String(buffer))
            }
        } catch (e: IOException) {
            // Should never happen, but belt-and-braces just in case.
            markwon.setMarkdown(
                binding.policy,
                "[Pachli Privacy Policy](https://pachli.app/privacy/)",
            )
        }
    }

    companion object {
        fun newInstance() = PrivacyPolicyFragment()
    }
}
