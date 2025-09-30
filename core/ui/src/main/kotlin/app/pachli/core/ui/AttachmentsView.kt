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

package app.pachli.core.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.model.Attachment
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.AttachmentDisplayReason
import app.pachli.core.ui.databinding.ItemMediaPreviewBinding
import app.pachli.core.ui.extensions.getFormattedDescription
import app.pachli.core.ui.extensions.iconResource
import com.bumptech.glide.RequestManager

/**
 * Function to call when the user clicks to view an attachment.
 *
 * @see [invoke].
 */
fun interface OnViewAttachment<T : IStatusViewData> {
    /**
     * Function to call when the user clicks to view an attachment.
     *
     * @param view View the user clicked on.
     * @param viewData
     * @param attachmentIndex Index of the attachment the user clicked on.
     */
    operator fun invoke(view: View?, viewData: T, attachmentIndex: Int)
}

/**
 * Function to call when the user wants to change how attachments are displayed.
 *
 * @see [invoke].
 */
fun interface OnAttachmentDisplayActionChange<T : IStatusViewData> {
    /**
     * Function to call when the user wants to change how attachments are
     * displayed.
     *
     * @param viewData
     * @param action The new [AttachmentDisplayAction].
     */
    operator fun invoke(viewData: T, action: AttachmentDisplayAction)
}

/**
 * Displays up to [MAX_ATTACHMENTS] attachments from a status, with controls to hide and
 * show the attachments.
 *
 * How to display the attachments depends on a number of factors. See [bind]
 * for details.
 */
class AttachmentsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {
    val binding = ItemMediaPreviewBinding.inflate(LayoutInflater.from(context), this)

    /** Views for displaying the description of the attachment at that index. */
    private val mediaDescriptionViews: Array<TextView> = arrayOf(
        binding.statusMediaLabel0,
        binding.statusMediaLabel1,
        binding.statusMediaLabel2,
        binding.statusMediaLabel3,
    )

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        // If the touchDelegate is set (see bind()) then intercept touch events
        // so they are forwarded to the delegate.
        return if (touchDelegate != null) true else super.onInterceptTouchEvent(ev)
    }

    /**
     * Display up to [MAX_ATTACHMENTS] attachments.
     *
     * If [showPreviews] is false, or none of the attachments are
     * [previewable][Attachment.isPreviewable] then a vertical list of attachment
     * descriptions is shown.
     *
     * If one or more of attachments is previewable they are shown in an
     * [AttachmentGridView].
     *
     * @param glide
     * @param viewData
     * @param attachments
     * @param showPreviews True if preview images should be shown. If false the
     * descriptions are shown.
     * @param useBlurHash True if hidden/unloaded attachments should be displayed
     * with their blur hash.
     * @param onViewAttachment Called when the user wants to view an attachment.
     * @param onAttachmentDisplayActionChange Called when the user wants to change
     * how the attachments should be dispayled.
     */
    fun <T : IStatusViewData> bind(
        glide: RequestManager,
        viewData: T,
        attachments: List<Attachment>,
        showPreviews: Boolean,
        useBlurHash: Boolean,
        onViewAttachment: OnViewAttachment<T>,
        onAttachmentDisplayActionChange: OnAttachmentDisplayActionChange<T>,
    ) {
        val previewableAttachments = attachments.filter { it.isPreviewable() }

        // Disable all previews if the user has disabled media previews or there
        // are no previewable attachments. Display attachment descriptions instead.
        if (!showPreviews || previewableAttachments.isEmpty()) {
            setMediaLabels(viewData, attachments, onViewAttachment, onAttachmentDisplayActionChange)
            binding.previewGrid.hide()
            hideSensitiveMediaWarning()
            return
        }

        // Previewable attachments exist. Hide all the description fields.
        mediaDescriptionViews.forEach { it.hide() }

        val displayAction = viewData.attachmentDisplayAction
        binding.previewGrid.bind(
            glide,
            previewableAttachments,
            displayAction,
            useBlurHash,
            attachmentClickListener = { view, index ->
                onViewAttachment(view, viewData, index)
            },
            attachmentLongClickListener = { _, index ->
                val attachment = attachments[index]
                val description = attachment.getFormattedDescription(context)
                Toast.makeText(context, description, Toast.LENGTH_LONG).show()
            },
        )
        binding.previewGrid.show()

        binding.hideAttachmentsButton.visible(displayAction is AttachmentDisplayAction.Show)
        binding.hideAttachmentsButton.setOnClickListener { v: View ->
            // The user clicked to hide the attachment. Either they are:
            //
            // a. Re-hiding an attachment that was hidden that they decided to show, or
            // b. Hiding media that wasn't originally hidden.
            //
            // If (a) then the new decision is `Show.originalDecision`. If (b) then
            // then the new decision is UserAction.
            val newAction = (viewData.attachmentDisplayAction as? AttachmentDisplayAction.Show)?.originalAction
                ?: AttachmentDisplayAction.Hide(AttachmentDisplayReason.UserAction)

            onAttachmentDisplayActionChange(viewData, newAction)
        }
        binding.displayActionLabel.setOnClickListener { v: View ->
            // The user is clicking through the warning to show the attachment.
            touchDelegate = null
            onAttachmentDisplayActionChange(
                viewData,
                AttachmentDisplayAction.Show(viewData.attachmentDisplayAction as? AttachmentDisplayAction.Hide),
            )
        }

        when (displayAction) {
            is AttachmentDisplayAction.Show -> binding.displayActionLabel.hide()

            is AttachmentDisplayAction.Hide -> {
                binding.displayActionLabel.text = displayAction.reason.getFormattedDescription(context)
                binding.displayActionLabel.show()
                // Extend the touchable area of displayActionLabel to cover this view,
                // so tapping anywhere on the view will call the click listener.
                post {
                    touchDelegate = TouchDelegate(
                        Rect(0, 0, width, height),
                        binding.displayActionLabel,
                    )
                }
            }
        }
    }

    /**
     * Sets the labels (icon and text) to display for the attachments, assuming
     * that none of them are previewable, or the user has chosen not to show
     * previews.
     */
    private fun <T : IStatusViewData> setMediaLabels(
        viewData: T,
        attachments: List<Attachment>,
        onViewAttachment: OnViewAttachment<T>,
        onAttachmentDisplayActionChange: OnAttachmentDisplayActionChange<T>,
    ) {
        val displayAction = viewData.attachmentDisplayAction

        mediaDescriptionViews.forEachIndexed { index, mediaLabel ->
            if (index < attachments.size) {
                val attachment = attachments[index]
                mediaLabel.show()

                val mediaDescription = when (displayAction) {
                    is AttachmentDisplayAction.Show -> attachment.getFormattedDescription(context)
                    is AttachmentDisplayAction.Hide -> displayAction.reason.getFormattedDescription(context)
                }

                mediaDescriptionViews[index].text = mediaDescription

                // Set the icon next to the label.
                val drawableId = attachment.iconResource()
                mediaLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(drawableId, 0, 0, 0)
                setAttachmentClickListener(
                    viewData,
                    mediaLabel,
                    index,
                    attachment,
                    false,
                    onViewAttachment,
                    onAttachmentDisplayActionChange,
                )
            } else {
                mediaLabel.hide()
            }
        }
    }

    private fun <T : IStatusViewData> setAttachmentClickListener(
        viewData: T,
        view: View,
        index: Int,
        attachment: Attachment,
        animateTransition: Boolean,
        onViewAttachment: OnViewAttachment<T>,
        onAttachmentDisplayActionChange: OnAttachmentDisplayActionChange<T>,
    ) {
        view.setOnClickListener { v: View? ->
            if (binding.displayActionLabel.isVisible) {
                onAttachmentDisplayActionChange(
                    viewData,
                    AttachmentDisplayAction.Show(originalAction = viewData.attachmentDisplayAction as? AttachmentDisplayAction.Hide),
                )
            } else {
                onViewAttachment(if (animateTransition) v else null, viewData, index)
            }
        }
        view.setOnLongClickListener {
            val description = attachment.getFormattedDescription(view.context)
            Toast.makeText(view.context, description, Toast.LENGTH_LONG).show()
            true
        }
    }

    /** Hides [sensitiveMediaWarning] and [sensitiveMediaShow]. */
    private fun hideSensitiveMediaWarning() {
        binding.displayActionLabel.hide()
        binding.hideAttachmentsButton.hide()
    }
}
