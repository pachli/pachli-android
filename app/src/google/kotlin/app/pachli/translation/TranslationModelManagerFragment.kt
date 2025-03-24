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

package app.pachli.translation

import android.app.Dialog
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import app.pachli.R
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.data.repository.Loadable
import app.pachli.core.preferences.SharedPreferencesRepository
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.databinding.FragmentModelManagerBinding
import app.pachli.translation.ConfirmDeleteLanguageDialogFragment.Companion.newInstance
import app.pachli.translation.ConfirmDownloadLanguageDialogFragment.Companion.newInstance
import com.github.michaelbull.result.get
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Displays a list of downloaded and available translation models, and UI controls to
 * allow the user to:
 *
 * - Delete a downloaded translation model.
 * - Pre-download a translation model.
 */
@AndroidEntryPoint
class TranslationModelManagerFragment : Fragment(R.layout.fragment_model_manager) {
    @Inject
    lateinit var sharedPreferencesRepository: SharedPreferencesRepository

    private val viewModel: TranslationModelManagerViewModel by activityViewModels()

    private val binding by viewBinding(FragmentModelManagerBinding::bind)

    private val connectivityManager by lazy { requireContext().getSystemService<ConnectivityManager>()!! }

    /** Adapter for a list of translation models already downloaded. */
    private val downloadedModelAdapter by lazy {
        TranslationModelAdapter(
            onDelete = ::confirmDeleteModel,
            onDownload = ::confirmDownloadLanguage,
        )
    }

    /** Adapter for a list of translation models that can be downloaded. */
    private val remoteModelAdapter by lazy {
        TranslationModelAdapter(
            onDelete = ::confirmDeleteModel,
            onDownload = ::confirmDownloadLanguage,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.applyDefaultWindowInsets()

        with(binding.recyclerView) {
            layoutManager = LinearLayoutManager(context)

            adapter = ConcatAdapter(
                HeadingAdapter(R.string.translation_model_manager_fragment_downloaded_heading),
                downloadedModelAdapter,
                HeadingAdapter(R.string.translation_model_manager_fragment_remote_heading),
                remoteModelAdapter,
            )
            setHasFixedSize(true)
            setAccessibilityDelegateCompat(
                RemoteModelAccessibilityDelegate(
                    this,
                    ::confirmDeleteModel,
                    ::confirmDownloadLanguage,
                ),
            )
        }

        bind()
    }

    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    // Split the list of models in two, depending on whether the model has
                    // been downloaded. Send each part to the correct adapter.
                    viewModel.flowViewData.collectLatest { models ->
                        val (loaded, remote) = models.partition { it.state.get() is Loadable.Loaded }
                        downloadedModelAdapter.submitList(loaded)
                        remoteModelAdapter.submitList(remote)
                    }
                }
            }
        }
    }

    /**
     * Downloads the [viewData.remoteModel.language][com.google.mlkit.nl.translate.TranslateRemoteModel.language].
     *
     * If the user allows downloads with mobile data, or the user is connected to a Wi-Fi
     * network, then the download proceeds immediately.
     *
     * Otherwise, shows [ConfirmDownloadLanguageDialogFragment] for the user to confirm
     * the download can proceed with mobile data.
     */
    private fun confirmDownloadLanguage(viewData: TranslationModelViewData) {
        if (!sharedPreferencesRepository.translationDownloadRequireWiFi) {
            viewModel.downloadLanguage(viewData.remoteModel.language)
            return
        }

        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let { capabilities ->
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                viewModel.downloadLanguage(viewData.remoteModel.language)
                return
            }
        }

        val dialog = ConfirmDownloadLanguageDialogFragment.newInstance(
            viewData.remoteModel.language,
            viewData.locale.displayLanguage,
        )
        dialog.show(parentFragmentManager, "confirmDownload")
    }

    /** Shows [ConfirmDeleteLanguageDialogFragment]. */
    private fun confirmDeleteModel(viewData: TranslationModelViewData) {
        val dialog = ConfirmDeleteLanguageDialogFragment.newInstance(
            viewData.remoteModel.language,
            viewData.locale.displayLanguage,
        )
        dialog.show(parentFragmentManager, "confirmDelete")
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.translation_model_manager_fragment_title)
    }
}

/** See [newInstance]. */
class ConfirmDownloadLanguageDialogFragment : DialogFragment() {
    private val viewModel: TranslationModelManagerViewModel by activityViewModels()

    private val language by lazy { requireArguments().getString(KEY_LANGUAGE)!! }
    private val displayLanguage by lazy { requireArguments().getString(KEY_DISPLAY_LANGUAGE)!! }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        return AlertDialog.Builder(activity).apply {
            setTitle(getString(R.string.fragment_confirm_download_language_dialog_title_fmt, displayLanguage))
            setMessage(R.string.fragment_confirm_download_language_dialog_msg)
            setPositiveButton(android.R.string.ok) { dialog, id -> viewModel.downloadLanguage(language) }
            setNegativeButton(android.R.string.cancel) { _, _ -> Unit }
        }.create()
    }

    companion object {
        private const val KEY_LANGUAGE = "app.pachli.translation.KEY_LANGUAGE"
        private const val KEY_DISPLAY_LANGUAGE = "app.pachli.translation.KEY_DISPLAY_LANGUAGE"

        /**
         * Displays a dialog for the user to confirm download a language model.
         *
         * @param language ISO code of the language to download.
         * @param displayLanguage Name of the language, in the user's locale.
         */
        fun newInstance(language: String, displayLanguage: String): ConfirmDownloadLanguageDialogFragment {
            return ConfirmDownloadLanguageDialogFragment().apply {
                arguments = Bundle(2).apply {
                    putString(KEY_LANGUAGE, language)
                    putString(KEY_DISPLAY_LANGUAGE, displayLanguage)
                }
            }
        }
    }
}

/** See [newInstance]. */
class ConfirmDeleteLanguageDialogFragment : DialogFragment() {
    private val viewModel: TranslationModelManagerViewModel by activityViewModels()

    private val language by lazy { requireArguments().getString(KEY_LANGUAGE)!! }
    private val displayLanguage by lazy { requireArguments().getString(KEY_DISPLAY_LANGUAGE)!! }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        return AlertDialog.Builder(activity).apply {
            setTitle(getString(R.string.fragment_confirm_delete_language_dialog_title_fmt, displayLanguage))
            setMessage(R.string.fragment_confirm_delete_language_dialog_msg)
            setPositiveButton(android.R.string.ok) { dialog, id -> viewModel.deleteLanguage(language) }
            setNegativeButton(android.R.string.cancel) { _, _ -> Unit }
        }.create()
    }

    companion object {
        private const val KEY_LANGUAGE = "app.pachli.translation.KEY_LANGUAGE"
        private const val KEY_DISPLAY_LANGUAGE = "app.pachli.translation.KEY_DISPLAY_LANGUAGE"

        /**
         * Displays a dialog for the user to confirm deletion of a language model.
         *
         * @param language ISO code of the language to delete.
         * @param displayLanguage Name of the language, in the user's locale.
         */
        fun newInstance(language: String, displayLanguage: String): ConfirmDeleteLanguageDialogFragment {
            return ConfirmDeleteLanguageDialogFragment().apply {
                arguments = Bundle(2).apply {
                    putString(KEY_LANGUAGE, language)
                    putString(KEY_DISPLAY_LANGUAGE, displayLanguage)
                }
            }
        }
    }
}
