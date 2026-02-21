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

package app.pachli.core.ui.components

import android.util.TypedValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import app.pachli.core.designsystem.theme.AppTheme
import app.pachli.core.designsystem.theme.androidAttrTextColorPrimary
import app.pachli.core.designsystem.theme.androidAttrTextColorTertiary

@Composable
@ReadOnlyComposable
fun attrDimenResource(attrId: Int): Dp {
    val context = LocalContext.current
    val attrs = context.theme.obtainStyledAttributes(intArrayOf(attrId))
    val value = attrs.getDimension(0, 0f)
    with(LocalDensity.current) { return value.toDp() }
}

@Composable
@ReadOnlyComposable
fun colorAttribute(attrColor: Int) = colorResource(
    TypedValue().apply { LocalContext.current.theme.resolveAttribute(attrColor, this, true) }.resourceId,
)

/**
 * Displays a list item.
 *
 * Differs from [androidx.compose.material3.ListItem] in the following ways:
 *
 * - It is the responsibility of the parent to provide any padding at the start/end
 * of each item. For example, with:
 * ```kotlin
 * Column(
 *     modifier = Modifier.padding(
 *         start = attrDimenResource(android.R.attr.listPreferredItemPaddingStart),
 *         end = attrDimenResource(android.R.attr.listPreferredItemPaddingEnd),
 *     ),
 * ```
 *
 * - Each item is at least android.R.attr.listPreferredItemHeightSmall high.
 * - The `headlineContent` and `trailingContent` are vertically centre-aligned
 * with each other, instead of `trailingContent` being vertically aligned with
 * both `headlineContent` and `supportingContent`.
 * - Consistency with view styles is enforced with LocalTextStyle and
 * [headlineContent] uses [titleMedium][MaterialTheme.typography.titleMedium]
 * and [androidAttrTextColorPrimary].
 *
 *
 * @param headlineContent Main content to display.
 * @param modifier
 * @param supportingContent Optional content to display below [headlineContent].
 * @param trailingContent Optional content to display after [headlineContent].
 */
@Composable
fun PachliListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .defaultMinSize(
                minHeight = attrDimenResource(android.R.attr.listPreferredItemHeightSmall),
            ),
    ) {
        val (refHeadline, refSupporting, refTrailing) = createRefs()

        // android:attr/textAppearanceListItemSmall
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.androidAttrTextColorPrimary,
            ),
        ) {
            Box(
                modifier = Modifier
                    .constrainAs(refHeadline) {
                        start.linkTo(parent.start)
                        top.linkTo(trailingContent?.let { refTrailing.top } ?: parent.top)
                        end.linkTo(trailingContent?.let { refTrailing.start } ?: parent.end)
                        bottom.linkTo(trailingContent?.let { refTrailing.bottom } ?: supportingContent?.let { refSupporting.top } ?: parent.bottom)
                        width = Dimension.Companion.fillToConstraints
                    },
            ) {
                headlineContent.invoke()
            }
        }

        // style and color for ?android:attr/textAppearanceListItemSecondary
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.androidAttrTextColorTertiary,
            ),
        ) {
            supportingContent?.let {
                Box(
                    modifier = Modifier.constrainAs(refSupporting) {
                        start.linkTo(parent.start)
                        top.linkTo(refHeadline.bottom)
                        end.linkTo(refHeadline.end)
                        bottom.linkTo(parent.bottom)

                        width = Dimension.Companion.fillToConstraints
                    },
                ) {
                    it.invoke()
                }
            }
        }

        // tint = MaterialTheme.colorScheme.androidAttrTextColorTertiary is from
        // @style/AppImageButton
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.androidAttrTextColorTertiary) {
            trailingContent?.let {
                Box(
                    modifier = Modifier.constrainAs(refTrailing) {
                        start.linkTo(refHeadline.end)
                        top.linkTo(parent.top)
                        end.linkTo(parent.end)
                    },
                ) {
                    it.invoke()
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewPachliListItem() {
    AppTheme {
        Column(
            modifier = Modifier.padding(
                start = attrDimenResource(android.R.attr.listPreferredItemPaddingStart),
                end = attrDimenResource(android.R.attr.listPreferredItemPaddingEnd),
            ),
        ) {
            PachliListItem(headlineContent = { Text(text = "Headline only") })
            PachliListItem(
                headlineContent = { Text(text = "Headline and trailing") },
                trailingContent = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = "Localized description",
                        )
                    }
                },
            )
            PachliListItem(
                headlineContent = { Text(text = "Headline and trailing") },
                trailingContent = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = "Localized description",
                        )
                    }
                },
            )
            PachliListItem(
                headlineContent = { Text(text = "Headline and supporting") },
                supportingContent = { Text(text = "This is supporting text") },

            )
            PachliListItem(
                headlineContent = { Text(text = "Headline and supporting") },
                supportingContent = { Text(text = "This is supporting text") },

            )
            PachliListItem(
                headlineContent = { Text(text = "Headline and supporting") },
                supportingContent = { Text(text = "This is supporting text") },

            )
            PachliListItem(
                headlineContent = { Text(text = "Headline, supporting, and trailing") },
                supportingContent = { Text(text = "This is supporting text") },
                trailingContent = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Localized description",
                        )
                    }
                },
            )
            PachliListItem(
                headlineContent = { Text("Long text that runs over two lines at typical size") },
                supportingContent = { Text("Some basic text") },
                trailingContent = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Localized description",
                        )
                    }
                },
            )
            PachliListItem(
                headlineContent = { Text("Long text that runs over two lines at typical size") },
                trailingContent = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Localized description",
                        )
                    }
                },
            )
        }
    }
}
