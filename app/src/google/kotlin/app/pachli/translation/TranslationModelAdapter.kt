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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import app.pachli.R
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.util.formatNumber
import app.pachli.core.data.repository.Loadable
import app.pachli.core.ui.accessibility.PachliRecyclerViewAccessibilityDelegate
import app.pachli.databinding.ItemRemoteModelBinding
import app.pachli.databinding.ItemRemoteModelDownloadedBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

/**
 * Adapter that manages a collection of [TranslationModelViewData] displayed with
 * [TranslationModelViewHolder] (the specific subclass depends on whether the model
 * is downloaded or not).
 *
 * @param onDelete Called when the user wants to delete a translation model.
 * @param onDownload Called when the user wants to download a translation model.
 */
internal class TranslationModelAdapter(
    private val onDelete: (TranslationModelViewData) -> Unit,
    private val onDownload: (TranslationModelViewData) -> Unit,
) : ListAdapter<TranslationModelViewData, TranslationModelViewHolder>(RemoteModelViewDataDiffer) {
    override fun getItemViewType(position: Int) = when (currentList[position].state.get()) {
        is Loadable.Loaded<*> -> R.layout.item_remote_model_downloaded
        else -> R.layout.item_remote_model
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranslationModelViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when {
            viewType == R.layout.item_remote_model_downloaded -> {
                val binding = ItemRemoteModelDownloadedBinding.inflate(layoutInflater, parent, false)
                return DownloadedTranslationModelViewHolder(binding, onDelete)
            }

            viewType == R.layout.item_remote_model -> {
                val binding = ItemRemoteModelBinding.inflate(layoutInflater, parent, false)
                return DownloadableTranslationModelViewHolder(binding, onDownload)
            }

            else -> throw IllegalStateException("Unrecognised viewType")
        }
    }

    override fun onBindViewHolder(holder: TranslationModelViewHolder, position: Int, payloads: List<Any?>) {
        holder.bind(currentList[position])
    }

    override fun onBindViewHolder(holder: TranslationModelViewHolder, position: Int) {
        holder.bind(currentList[position])
    }
}

abstract class TranslationModelViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
    var viewData: TranslationModelViewData? = null

    abstract fun bind(viewData: TranslationModelViewData)
}

/**
 * Displays a single downloaded [TranslationModelViewData].
 *
 * - English is always shown, and cannot be deleted (it is required by MlKit).
 * - Downloaded models are shown with a "delete" icon. Tapping the icon calls
 * [onDelete].
 *
 * @param binding
 * @param onDelete Call when the user wants to delete a translation model.
 */
internal class DownloadedTranslationModelViewHolder(
    private val binding: ItemRemoteModelDownloadedBinding,
    onDelete: (TranslationModelViewData) -> Unit,
) : TranslationModelViewHolder(binding) {
    init {
        binding.actionButton.setOnClickListener {
            viewData?.let { viewData ->
                // Ignore clicks on "English", otherwise delete the model
                if (viewData.remoteModel.language == "en") return@setOnClickListener
                onDelete(viewData)
            }
        }
    }

    override fun bind(viewData: TranslationModelViewData) {
        this.viewData = viewData
        val displayLanguage = viewData.locale.displayLanguage

        binding.text1.text = displayLanguage

        if (viewData.remoteModel.language == "en") {
            binding.text1.contentDescription = displayLanguage
            binding.text2.hide()
            binding.actionButton.setImageResource(R.drawable.ic_check_circle)
            return
        }

        binding.text1.contentDescription = binding.root.context.getString(
            R.string.content_description_loaded_fmt,
            displayLanguage,
        )
        binding.actionButton.setImageResource(R.drawable.outline_delete_24)

        (viewData.state.get() as? Loadable.Loaded<ModelStats>)?.let { state ->
            if (state.data.sizeOnDisk == 0L) {
                binding.text2.hide()
            } else {
                val sizeOnDisk = "${formatNumber(state.data.sizeOnDisk)}M"
                binding.text2.text = sizeOnDisk
                binding.text2.show()
            }
        }
    }
}

/**
 * Displays a single downloadable [TranslationModelViewData].
 *
 * - Models are shown with a "download" icon. Tapping the icon calls
 * [onDownload].
 *
 * Download-in-progress models show a progress spinner and hide the action icon.
 *
 * Download errors are shown below the language.
 *
 * @param binding
 * @param onDownload Call when the user wants to download a translation model.
 */
internal class DownloadableTranslationModelViewHolder(
    private val binding: ItemRemoteModelBinding,
    onDownload: (TranslationModelViewData) -> Unit,
) : TranslationModelViewHolder(binding) {
    init {
        binding.actionButton.setOnClickListener {
            viewData?.let { viewData -> onDownload(viewData) }
        }
    }

    override fun bind(viewData: TranslationModelViewData) {
        this.viewData = viewData
        val displayLanguage = viewData.locale.displayLanguage

        binding.text1.text = displayLanguage

        val state = viewData.state.get()
        binding.progress.isVisible = state is Loadable.Loading
        binding.actionButton.isVisible = !binding.progress.isVisible

        when {
            state is Loadable.Loading -> {
                binding.text1.contentDescription = binding.root.context.getString(
                    R.string.content_description_loading_fmt,
                    displayLanguage,
                )
            }

            state == null -> {
                binding.text1.contentDescription = binding.root.context.getString(
                    R.string.content_description_available_fmt,
                    displayLanguage,
                )
            }
        }

        val error = viewData.state.getError()
        error?.let { binding.errorMsg.text = error.fmt(binding.errorMsg.context) }
        binding.errorMsg.isVisible = error != null
    }
}

object RemoteModelViewDataDiffer : DiffUtil.ItemCallback<TranslationModelViewData>() {
    override fun areItemsTheSame(oldItem: TranslationModelViewData, newItem: TranslationModelViewData): Boolean {
        return oldItem.remoteModel.language == newItem.remoteModel.language
    }

    override fun areContentsTheSame(oldItem: TranslationModelViewData, newItem: TranslationModelViewData): Boolean {
        return oldItem == newItem
    }
}

internal class RemoteModelAccessibilityDelegate(
    private val recyclerView: RecyclerView,
    private val onDelete: (TranslationModelViewData) -> Unit,
    private val onDownload: (TranslationModelViewData) -> Unit,
) : PachliRecyclerViewAccessibilityDelegate(recyclerView) {
    private val delegate = object : ItemDelegate(this) {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)

            val viewHolder = recyclerView.findContainingViewHolder(host)
                as? TranslationModelViewHolder ?: return

            viewHolder.viewData?.let { viewData ->
                if (viewData.remoteModel.language == "en") return

                viewData.state.onSuccess {
                    when (it) {
                        null -> info.addAction(
                            AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.action_download_remote_model,
                                context.getString(R.string.action_download_remote_model_fmt, viewData.locale.displayLanguage),
                            ),
                        )

                        Loadable.Loading -> Unit

                        is Loadable.Loaded<*> -> info.addAction(
                            AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.action_delete_remote_model,
                                context.getString(
                                    R.string.action_delete_remote_model_fmt,
                                    viewData.locale.displayLanguage,
                                ),
                            ),
                        )
                    }
                }.onFailure {
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_download_remote_model,
                            context.getString(
                                R.string.action_download_remote_model_fmt,
                                viewData.locale.displayLanguage,
                            ),
                        ),
                    )
                }
            }
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            val viewData = (
                recyclerView.findContainingViewHolder(host)
                    as? DownloadableTranslationModelViewHolder
                )?.viewData ?: return super.performAccessibilityAction(host, action, args)

            return when (action) {
                R.id.action_delete_remote_model -> {
                    interrupt()
                    onDelete(viewData)
                    true
                }

                R.id.action_download_remote_model -> {
                    interrupt()
                    onDownload(viewData)
                    true
                }

                else -> super.performAccessibilityAction(host, action, args)
            }
        }
    }

    override fun getItemDelegate() = delegate
}
