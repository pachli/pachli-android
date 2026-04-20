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
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import app.pachli.core.activity.OpenUrlUseCase
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.versionName
import app.pachli.core.preferences.LinksToUnderline
import app.pachli.core.ui.ClipboardUseCase
import app.pachli.core.ui.MaybeUnderlineURLSpan
import app.pachli.core.ui.extensions.InsetType
import app.pachli.core.ui.extensions.applyWindowInsets
import app.pachli.feature.about.databinding.FragmentAboutBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AboutFragment : Fragment(R.layout.fragment_about) {
    @Inject
    lateinit var clipboard: ClipboardUseCase

    @Inject
    lateinit var openUrl: OpenUrlUseCase

    private val viewModel: AboutFragmentViewModel by viewModels()

    private val binding by viewBinding(FragmentAboutBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.applyWindowInsets(bottom = InsetType.PADDING)

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

        val underlineUrls = viewModel.linksToUnderline.contains(LinksToUnderline.LINKS)

        binding.aboutNivenlyFoundation.convertUrlSpanToMaybeUnderlineUrlSpan(underlineUrls, openUrl::invoke)
        binding.aboutLicenseInfoTextView.addLinks(Linkify.WEB_URLS)
        binding.aboutLicenseInfoTextView.convertUrlSpanToMaybeUnderlineUrlSpan(underlineUrls, openUrl::invoke)
        binding.aboutWebsiteInfoTextView.addLinks(Linkify.WEB_URLS)
        binding.aboutWebsiteInfoTextView.convertUrlSpanToMaybeUnderlineUrlSpan(underlineUrls, openUrl::invoke)
        binding.aboutBugsFeaturesInfoTextView.addLinks(Linkify.WEB_URLS)
        binding.aboutBugsFeaturesInfoTextView.convertUrlSpanToMaybeUnderlineUrlSpan(underlineUrls, openUrl::invoke)

        binding.appProfileButton.setOnClickListener {
            openUrl(BuildConfig.SUPPORT_ACCOUNT_URL)
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

/**
 * Calls [Linkify.addLinks] on [text][TextView.text], applying [mask][Linkify.addLinks].
 *
 * @param mask Mask to define which kinds of links will be searched.
 */
internal fun TextView.addLinks(mask: Int) {
    SpannableStringBuilder(text).apply {
        Linkify.addLinks(this, mask)
        text = this
    }
}

/**
 * Converts [URLSpan] in [text][TextView.text] to [MaybeUnderlineURLSpan].
 *
 * @param underlineUrls True if the URLs should be underlined.
 * @param openUrl Function to call when the URL is clicked.
 */
internal fun TextView.convertUrlSpanToMaybeUnderlineUrlSpan(underlineUrls: Boolean, openUrl: (String) -> Unit) {
    SpannableStringBuilder(text).apply {
        getSpans(0, this.length, URLSpan::class.java).forEach { span ->
            val start = getSpanStart(span)
            val end = getSpanEnd(span)
            val flags = getSpanFlags(span)
            val replacement = MaybeUnderlineURLSpan(underlineUrls, span.url, openUrl)
            removeSpan(span)
            setSpan(replacement, start, end, flags)
        }

        text = this
    }
    linksClickable = true
    movementMethod = LinkMovementMethod.getInstance()
}
