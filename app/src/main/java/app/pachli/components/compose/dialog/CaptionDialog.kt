/* Copyright 2019 Tusky Contributors
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

package app.pachli.components.compose.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import app.pachli.R
import app.pachli.databinding.DialogImageDescriptionBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

// https://github.com/tootsuite/mastodon/blob/c6904c0d3766a2ea8a81ab025c127169ecb51373/app/models/media_attachment.rb#L32
private const val MEDIA_DESCRIPTION_CHARACTER_LIMIT = 1500

class CaptionDialog : DialogFragment() {
    private lateinit var listener: Listener
    private lateinit var input: EditText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val binding = DialogImageDescriptionBinding.inflate(layoutInflater)

        input = binding.imageDescriptionText
        val imageView = binding.imageDescriptionView
        imageView.maxZoom = 6f

        input.hint = resources.getQuantityString(
            R.plurals.hint_describe_for_visually_impaired,
            MEDIA_DESCRIPTION_CHARACTER_LIMIT,
            MEDIA_DESCRIPTION_CHARACTER_LIMIT,
        )
        input.filters = arrayOf(InputFilter.LengthFilter(MEDIA_DESCRIPTION_CHARACTER_LIMIT))
        input.setText(arguments?.getString(ARG_EXISTING_DESCRIPTION))

        val localId = arguments?.getInt(ARG_LOCAL_ID) ?: error("Missing localId")
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                listener.onUpdateDescription(localId, input.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        isCancelable = true
        val window = dialog.window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val previewUri = BundleCompat.getParcelable(requireArguments(), ARG_PREVIEW_URI, Uri::class.java) ?: error("Preview Uri is null")
        // Load the image and manually set it into the ImageView because it doesn't have a fixed size.
        Glide.with(this)
            .load(previewUri)
            .downsample(DownsampleStrategy.CENTER_INSIDE)
            .into(
                object : CustomTarget<Drawable>(4096, 4096) {
                    override fun onLoadCleared(placeholder: Drawable?) {
                        imageView.setImageDrawable(placeholder)
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?,
                    ) {
                        imageView.setImageDrawable(resource)
                    }
                },
            )

        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_DESCRIPTION, input.text.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        savedInstanceState?.getString(KEY_DESCRIPTION)?.let {
            input.setText(it)
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener ?: error("Activity is not ComposeCaptionDialog.Listener")
    }

    interface Listener {
        fun onUpdateDescription(localId: Int, description: String)
    }

    companion object {
        private const val KEY_DESCRIPTION = "app.pachli.KEY_DESCRIPTION"
        private const val ARG_EXISTING_DESCRIPTION = "app.pachli.ARG_EXISTING_DESCRIPTION"
        private const val ARG_PREVIEW_URI = "app.pachli.ARG_PREVIEW_URI"
        private const val ARG_LOCAL_ID = "app.pachli.ARG_LOCAL_ID"

        fun newInstance(
            localId: Int,
            existingDescription: String?,
            previewUri: Uri,
        ) = CaptionDialog().apply {
            arguments = bundleOf(
                ARG_LOCAL_ID to localId,
                ARG_EXISTING_DESCRIPTION to existingDescription,
                ARG_PREVIEW_URI to previewUri,
            )
        }
    }
}
