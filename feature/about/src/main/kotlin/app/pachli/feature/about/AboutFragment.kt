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

import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import app.pachli.core.activity.openLink
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.versionName
import app.pachli.core.ui.ClipboardUseCase
import app.pachli.core.ui.NoUnderlineURLSpan
import app.pachli.feature.about.databinding.FragmentAboutBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AboutFragment : Fragment(R.layout.fragment_about) {
    @Inject
    lateinit var clipboard: ClipboardUseCase

    private val viewModel: AboutFragmentViewModel by viewModels()

    private val binding by viewBinding(FragmentAboutBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val version = getString(
            R.string.about_app_version,
            getString(
                R.string.app_name,
            ),
            versionName(requireContext()),
        )

        binding.versionTextView.text = version

        val deviceInfo = getString(
            R.string.about_device_info,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
        )
        binding.deviceInfo.text = deviceInfo

        lifecycleScope.launch {
            viewModel.accountInfo.collect { accountInfo ->
                if (accountInfo == null) {
                    binding.accountInfoTitle.hide()
                    binding.accountInfo.hide()
                    binding.copyDeviceInfo.hide()
                    return@collect
                }

                binding.accountInfo.text = accountInfo
                binding.accountInfoTitle.show()
                binding.accountInfo.show()
                binding.copyDeviceInfo.show()
            }
        }

        if (BuildConfig.CUSTOM_INSTANCE.isBlank()) {
            binding.aboutPoweredBy.hide()
        }

        binding.aboutLicenseInfoTextView.setClickableTextWithoutUnderlines(R.string.about_pachli_license)
        binding.aboutWebsiteInfoTextView.setClickableTextWithoutUnderlines(R.string.about_project_site)
        binding.aboutBugsFeaturesInfoTextView.setClickableTextWithoutUnderlines(R.string.about_bug_feature_request_site)

        binding.appProfileButton.setOnClickListener {
            requireActivity().openLink(BuildConfig.SUPPORT_ACCOUNT_URL)
        }

        binding.copyDeviceInfo.setOnClickListener {
            val text = "$version\n\nDevice:\n\n$deviceInfo\n\nAccount:\n\n${binding.accountInfo.text}"
            clipboard.copyTextTo(text, R.string.about_copied, "Pachli version information")
        }
    }

    companion object {
        fun newInstance() = AboutFragment()
    }
}

internal fun TextView.setClickableTextWithoutUnderlines(@StringRes textId: Int) {
    val text = SpannableString(context.getText(textId))

    Linkify.addLinks(text, Linkify.WEB_URLS)

    val builder = SpannableStringBuilder(text)
    val urlSpans = text.getSpans(0, text.length, URLSpan::class.java)
    for (span in urlSpans) {
        val start = builder.getSpanStart(span)
        val end = builder.getSpanEnd(span)
        val flags = builder.getSpanFlags(span)

        val customSpan = NoUnderlineURLSpan(span.url)

        builder.removeSpan(span)
        builder.setSpan(customSpan, start, end, flags)
    }

    setText(builder)
    linksClickable = true
    movementMethod = LinkMovementMethod.getInstance()
}
