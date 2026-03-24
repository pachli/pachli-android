/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.core.ui.components

import android.os.Build
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.pachli.core.designsystem.theme.ThemePreviews
import app.pachli.core.designsystem.theme.pachliColors
import com.composables.core.DoNothing
import com.composeunstyled.LocalModalWindow
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledDialog
import com.composeunstyled.UnstyledDialogPanel
import com.composeunstyled.UnstyledScrim
import com.composeunstyled.rememberDialogState

/** Test tag for the dialog's positive button. */
const val DIALOG_BUTTON_POSITIVE = "DIALOG_BUTTON_POSITIVE"

/** Test tag for the dialog's negative button. */
const val DIALOG_BUTTON_NEGATIVE = "DIALOG_BUTTON_NEGATIVE"

/**
 * Displays a generic alert dialog.
 *
 * @param dialogTitle Text to use as the dialog's title.
 * @param dialogText Text to use as the dialog's body text.
 * @param modifier
 * @param isVisible If true the dialog is displayed.
 * @param onDismissRequest Called if the dialog is dismissed.
 * @param onConfirm Called if the user confirms.
 */
@Composable
fun PachliAlertDialog(
    dialogTitle: String,
    dialogText: String,
    modifier: Modifier = Modifier,
    isVisible: Boolean = false,
    onDismissRequest: () -> Unit = DoNothing,
    onConfirm: () -> Unit = DoNothing,
) {
    val dialogState = rememberDialogState(initiallyVisible = LocalInspectionMode.current)
    SideEffect { dialogState.visible = isVisible }

    /** The actual title to display. */
    var title by rememberSaveable { mutableStateOf(dialogTitle) }

    /** The actual text to display. */
    var text by rememberSaveable { mutableStateOf(dialogText) }

    // If the dialog is becoming visible then save the title and text to display.
    // These will be used while the dialog is hidden, to ensure the content does
    // not disappear during any animations when the dialog is dismissed.
    if (isVisible && !dialogState.visible) {
        title = dialogTitle
        text = dialogText
    }

    UnstyledDialog(
        state = dialogState,
        onDismiss = onDismissRequest,
    ) {
        UnstyledScrim(scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))

        AdjustSystemBarColors(dialogState.visible)

        UnstyledDialogPanel(
            modifier = modifier
                .displayCutoutPadding()
                .systemBarsPadding()
                .widthIn(min = 280.dp, max = 560.dp)
                .padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 0.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(pachliColors.backgroundFloating),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column {
                Column(Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp)) {
                    Text(
                        text = title,
                        style = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.semantics {
                            heading()
                            paneTitle = title
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        text = text,
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(start = 12.dp, top = 0.dp, end = 12.dp, bottom = 0.dp),
                ) {
                    // TODO: Style provider for consistent buttons? DialogButton fun?
                    UnstyledButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .testTag(DIALOG_BUTTON_NEGATIVE)
                            .requiredSizeIn(minHeight = 48.dp, minWidth = 64.dp)
                            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            style = TextStyle(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            ),
                            text = stringResource(android.R.string.cancel),
                        )
                    }
                    UnstyledButton(
                        onClick = onConfirm,
                        modifier = Modifier
                            .testTag(DIALOG_BUTTON_POSITIVE)
                            .requiredSizeIn(minHeight = 48.dp, minWidth = 64.dp)
                            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            style = TextStyle(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            ),
                            text = stringResource(android.R.string.ok),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Adjust's the system bar colours to dark variant (i.e., light colours on a dark
 * background) on entering the composition, and resets them when leaving the
 * composition.
 *
 * Useful when a scrim is in use.
 *
 * @param visible True if the system bar colours should be changed.
 */
@Composable
internal fun AdjustSystemBarColors(visible: Boolean) {
    val window = LocalModalWindow.current

    DisposableEffect(visible) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val originalStatusBarColor = insetsController.isAppearanceLightStatusBars
        val originalNavigationBarColor = insetsController.isAppearanceLightNavigationBars

        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        if (visible) {
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
        onDispose {
            insetsController.isAppearanceLightStatusBars = originalStatusBarColor
            insetsController.isAppearanceLightNavigationBars = originalNavigationBarColor
        }
    }
}

@ThemePreviews
@Composable
internal fun PreviewPachliAlertDialog() {
    PreviewPachliTheme {
        PachliAlertDialog(
            dialogTitle = "An alert dialog",
            dialogText = "The alert dialog message",
            isVisible = true,
            onDismissRequest = {},
        ) {}
    }
}
