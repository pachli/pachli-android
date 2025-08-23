/*
 * Copyright 2025 Pachli Association
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

package app.pachli.core.ui.emoji

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.os.BundleCompat
import app.pachli.core.model.Emoji
import app.pachli.core.ui.R
import app.pachli.core.ui.SuspendDialogResult
import dagger.hilt.android.AndroidEntryPoint

/**
 * Displays a dialog showing [EmojiPickerView], allowing the user to see and
 * search for emojis. Clicking an emoji returns it in [result].
 */
@AndroidEntryPoint
class ChooseEmojiDialogFragment : AppCompatDialogFragment(), SuspendDialogResult<Emoji?> {
    /**
     * The [Emoji] the user chose.
     *
     * If the user pressed Back this is null.
     */
    override var result: Emoji? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val rootView = layoutInflater.inflate(R.layout.fragment_choose_emoji, null)
        val emojiPickerView = rootView.findViewById<EmojiPickerView>(R.id.emojiPickerView)!!

        val args = requireArguments()
        emojiPickerView.animate = args.getBoolean(ARG_ANIMATE_EMOJIS)
        emojiPickerView.onSelectEmoji = {
            result = it
            dismiss()
        }
        emojiPickerView.emojis = BundleCompat.getParcelableArrayList(args, ARG_EMOJIS, Emoji::class.java).orEmpty()

        return AlertDialog.Builder(requireActivity())
            .setView(rootView)
            .create()
    }

    companion object {
        private const val ARG_EMOJIS = "app.pachli.ARG_EMOJIS"
        private const val ARG_ANIMATE_EMOJIS = "app.pachli.ARG_ANIMATE_EMOJIS"

        /**
         * Creates the dialog.
         *
         * @param emojis List of [Emoji] to display.
         * @param animate True if the emoji's should be animated.
         */
        fun newInstance(emojis: List<Emoji>, animate: Boolean) = ChooseEmojiDialogFragment().apply {
            arguments = Bundle().apply {
                putParcelableArrayList(ARG_EMOJIS, ArrayList(emojis))
                putBoolean(ARG_ANIMATE_EMOJIS, animate)
            }
        }
    }
}
