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

package app.pachli.core.ui.extensions

import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_NEGATIVE
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Wait for the alert dialog buttons to be clicked, return the ID of the clicked button,
 * [AlertDialog.BUTTON_POSITIVE], [AlertDialog.BUTTON_NEGATIVE], or
 * [AlertDialog.BUTTON_NEUTRAL].
 *
 * @param positiveText Text to show on the positive button
 * @param negativeText Optional text to show on the negative button
 * @param neutralText Optional text to show on the neutral button
 */
suspend fun AlertDialog.await(
    positiveText: String,
    negativeText: String? = null,
    neutralText: String? = null,
) = suspendCancellableCoroutine { cont ->
    val listener = DialogInterface.OnClickListener { _, which ->
        cont.resume(which) { dismiss() }
    }

    setButton(AlertDialog.BUTTON_POSITIVE, positiveText, listener)
    negativeText?.let { setButton(AlertDialog.BUTTON_NEGATIVE, it, listener) }
    neutralText?.let { setButton(AlertDialog.BUTTON_NEUTRAL, it, listener) }

    setOnCancelListener { cont.cancel() }
    cont.invokeOnCancellation { dismiss() }
    show()
}

/**
 * @see [AlertDialog.await]
 */
suspend fun AlertDialog.await(
    @StringRes positiveTextResource: Int,
    @StringRes negativeTextResource: Int? = null,
    @StringRes neutralTextResource: Int? = null,
) = await(
    context.getString(positiveTextResource),
    negativeTextResource?.let { context.getString(it) },
    neutralTextResource?.let { context.getString(it) },
)

/**
 * Result from [AlertDialog.Builder.awaitSingleChoiceItem].
 *
 * @param button ID of the button that was pressed, [AlertDialog.BUTTON_POSITIVE],
 * [AlertDialog.BUTTON_NEGATIVE], or [AlertDialog.BUTTON_NEUTRAL].
 * @param index Index of the selected item when the button was pressed.
 */
data class SingleChoiceItemResult(val button: Int, val index: Int)

/**
 * Shows an [AlertDialog] displaying [items] with the item at
 * [initialIndex] selected.
 *
 * @param items List of items to display.
 * @param initialIndex Index of the item that should be selected when
 * the dialog is shown. Use `-1` to leave all items unselected.
 * @param positiveTextResource String resource to use as positive button
 * text
 * @param negativeTextResource Optional string resource to use as negative
 * button text. If null the button is not shown.
 * @param neutralTextResource Optional string resource to use as neutral
 * button tet. If null the button is not shown.
 * @return [SingleChoiceItemResult] with the button that was pressed and
 * the index of the selected item in [items] when the button was pressed.
 */
suspend inline fun <reified T : CharSequence> AlertDialog.Builder.awaitSingleChoiceItem(
    items: List<T>,
    initialIndex: Int,
    @StringRes positiveTextResource: Int,
    @StringRes negativeTextResource: Int? = null,
    @StringRes neutralTextResource: Int? = null,
) = suspendCancellableCoroutine { cont ->
    var selectedIndex = initialIndex

    val itemListener = DialogInterface.OnClickListener { _, which ->
        selectedIndex = which
    }

    val buttonListener = DialogInterface.OnClickListener { _, which ->
        cont.resume(SingleChoiceItemResult(which, selectedIndex)) { }
    }

    setSingleChoiceItems(items.toTypedArray(), selectedIndex, itemListener)
    setPositiveButton(positiveTextResource, buttonListener)
    negativeTextResource?.let { setNegativeButton(it, buttonListener) }
    neutralTextResource?.let { setNeutralButton(it, buttonListener) }
    setOnCancelListener { cont.resume(SingleChoiceItemResult(BUTTON_NEGATIVE, selectedIndex)) {} }
    setOnDismissListener { if (!cont.isCompleted) cont.resume(SingleChoiceItemResult(BUTTON_NEGATIVE, selectedIndex)) {} }
    val dialog = create()

    cont.invokeOnCancellation { dialog.dismiss() }
    dialog.show()
}

/**
 * Shows a [MaterialDatePicker] and returns the result after the user makes
 * their selection.
 *
 * @param fragmentManager The FragmentManager this fragment will be added to
 * @param tag Optional tag name for the fragment, to later retrieve the fragment with
 * [FragmentManager.findFragmentByTag(String)][androidx.fragment.app.FragmentManager.findFragmentById]
 * @return Object of type [S] if the dialog's positive button is clicked,
 * otherwise null.
 */
suspend fun <S> MaterialDatePicker<S>.await(
    fragmentManager: androidx.fragment.app.FragmentManager,
    tag: String?,
) = suspendCancellableCoroutine { cont ->
    val listener = MaterialPickerOnPositiveButtonClickListener<S> { selection ->
        cont.resume(selection) { dismiss() }
    }

    addOnPositiveButtonClickListener(listener)
    addOnNegativeButtonClickListener { cont.resume(null) { dismiss() } }
    addOnCancelListener { cont.resume(null) { dismiss() } }
    addOnDismissListener { if (!cont.isCompleted) cont.resume(null) { dismiss() } }
    cont.invokeOnCancellation { dismiss() }

    show(fragmentManager, tag)
}
