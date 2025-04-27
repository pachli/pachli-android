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

package app.pachli.core.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.pachli.core.ui.AlertDialogFragment.Companion.newInstance
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Provides [SuspendDialogResult.result] to return from the [DialogFragment].
 *
 * [DialogFragment]s that implement this should set [result] when the user
 * dismisses the dialog.
 */
interface SuspendDialogResult<T> {
    /** The user's choice from the dialog. */
    var result: T

    /**
     * Shows the [DialogFragment] using [fragmentManager] and [tag].
     *
     * Suspends until the dialog is destroyed.
     *
     * @param fragmentManager The fragment manager used to display the [DialogFragment].
     * @param tag Optional tag to pass to the fragment manager. Default value is the
     * receiver's canonical class name.
     * @return Whatever the [DialogFragment] stored in [SuspendDialogResult.result].
     */
    suspend fun await(
        fragmentManager: FragmentManager,
        tag: String? = this::class.java.canonicalName,
    ) = suspendCancellableCoroutine<T> {
        if (this !is DialogFragment) {
            throw IllegalArgumentException("${SuspendDialogResult::class} only works on ${DialogFragment::class}")
        }

        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                lifecycle.removeObserver(this)
                it.resume(result)
            }
        }

        lifecycle.addObserver(observer)
        show(fragmentManager, tag)
    }
}

/** @see [AlertDialogFragment.Companion]. */
class AlertDialogFragment : AppCompatDialogFragment(), SuspendDialogResult<Int> {
    /**
     * The button the user clicked.
     *
     * If the user pressed Back this is [AlertDialog.BUTTON_NEGATIVE].
     */
    override var result: Int = AlertDialog.BUTTON_NEGATIVE

    private val title by lazy { requireArguments().getString(ARG_TITLE) }
    private val message by lazy { requireArguments().getString(ARG_MESSAGE) }
    private val positiveText by lazy { requireArguments().getString(ARG_POSITIVE_TEXT) }
    private val negativeText by lazy { requireArguments().getString(ARG_NEGATIVE_TEXT) }
    private val neutralText by lazy { requireArguments().getString(ARG_NEUTRAL_TEXT) }

    val listener = DialogInterface.OnClickListener { _, which ->
        result = which
        dismiss()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity()).apply {
            title?.let { setTitle(it) }
            message?.let { setMessage(it) }
            positiveText?.let { setPositiveButton(it, listener) }
            negativeText?.let { setNegativeButton(it, listener) }
            neutralText?.let { setNeutralButton(it, listener) }
        }

        return builder.create()
    }

    /**
     * Shows a suspendable [AlertDialog] wrapped in an [AppCompatDialogFragment].
     *
     * Show the dialog using [SuspendDialogResult.await], which returns one of
     * [AlertDialog.BUTTON_POSITIVE], [AlertDialog.BUTTON_NEGATIVE], or
     * [AlertDialog.BUTTON_NEUTRAL], depending on which button the user pressed.
     *
     * Pressing Back returns [AlertDialog.BUTTON_NEGATIVE].
     *
     * Do not create this fragment directly, use [newInstance].
     */
    companion object {
        private const val ARG_TITLE = "app.pachli.ARG_TITLE"
        private const val ARG_MESSAGE = "app.pachli.ARG_MESSAGE"
        private const val ARG_POSITIVE_TEXT = "app.pachli.ARG_POSITIVE_TEXT"
        private const val ARG_NEGATIVE_TEXT = "app.pachli.ARG_NEGATIVE_TEXT"
        private const val ARG_NEUTRAL_TEXT = "app.pachli.ARG_NEUTRAL_TEXT"

        /**
         * Creates [AlertDialogFragment].
         *
         * @param title Text to show as the dialog's title.
         * @param message Text to show as the dialog's main message.
         * @param positiveText Text to show as the dialog's primary button. If
         * null the button is not shown.
         * @param negativeText Text to show as the dialog's negative button. If
         * null the button is not shown.
         * @param neutralText Text to show as the dialog's neutral button. If
         * null the button is not shown.
         * @return [AlertDialogFragment].
         */
        fun newInstance(
            title: CharSequence?,
            message: CharSequence?,
            positiveText: CharSequence?,
            negativeText: CharSequence? = null,
            neutralText: CharSequence? = null,
        ) = AlertDialogFragment().apply {
            arguments = Bundle().apply {
                putCharSequence(ARG_TITLE, title)
                putCharSequence(ARG_MESSAGE, message)
                putCharSequence(ARG_POSITIVE_TEXT, positiveText)
                putCharSequence(ARG_NEGATIVE_TEXT, negativeText)
                putCharSequence(ARG_NEUTRAL_TEXT, neutralText)
            }
        }
    }
}
