package app.pachli.util

import android.content.Context
import app.pachli.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun Context.showUncaptionedMediaWarningDialog(onConfirm: () -> Unit) {
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.warning_missing_alt_text_title)
        .setMessage(R.string.warning_missing_alt_text_message)
        .setPositiveButton(R.string.action_reblog_anyway) { _, _ ->
            onConfirm()
        }
        .setCancelable(false)
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

