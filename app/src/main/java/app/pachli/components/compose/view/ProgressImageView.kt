/*
 * Copyright 2023 Pachli Association
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
package app.pachli.components.compose.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import androidx.annotation.OptIn
import androidx.appcompat.content.res.AppCompatResources
import app.pachli.R
import app.pachli.components.compose.MediaUploaderError
import app.pachli.components.compose.UploadState
import app.pachli.core.designsystem.R as DR
import app.pachli.core.ui.makeIcon
import app.pachli.view.MediaPreviewImageView
import at.connyduck.sparkbutton.helpers.Utils
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.color.MaterialColors
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial

class ProgressImageView
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MediaPreviewImageView(context, attrs, defStyleAttr) {
    private var result: Result<UploadState, MediaUploaderError> = Ok(UploadState.Uploading(0))
    private val progressRect = RectF()
    private val biggerRect = RectF()
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@ProgressImageView, androidx.appcompat.R.attr.colorPrimary)
        strokeWidth = Utils.dpToPx(context, 4).toFloat()
        style = Paint.Style.STROKE
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val markBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(DR.color.tusky_grey_10)
    }
    private val captionDrawable = AppCompatResources.getDrawable(
        context,
        R.drawable.spellcheck,
    )!!.apply {
        setTint(Color.WHITE)
    }
    private val circleRadius = Utils.dpToPx(context, 14)
    private val circleMargin = Utils.dpToPx(context, 14)
    private val uploadErrorRadius = Utils.dpToPx(context, 24)

    private val uploadErrorDrawable = makeIcon(context, GoogleMaterial.Icon.gmd_error, 48).apply {
        setTint(Color.WHITE)
    }

    @OptIn(ExperimentalBadgeUtils::class)
    fun setResult(result: Result<UploadState, MediaUploaderError>) {
        this.result = result
        invalidate()
    }

    fun setChecked(checked: Boolean) {
        markBgPaint.color = if (checked) {
            MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
        } else {
            context.getColor(DR.color.tusky_grey_10)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        result.onSuccess { value ->
            val percentage = when (value) {
                is UploadState.Uploading -> value.percentage
                else -> -1
            }
            onDrawSuccess(canvas, percentage)
        }.onFailure { error ->
            onDrawError(canvas)
        }
    }

    private fun onDrawSuccess(canvas: Canvas, progress: Int) {
        clearColorFilter()
        if (progress != -1) {
            setColorFilter(Color.rgb(123, 123, 123), PorterDuff.Mode.MULTIPLY)
        }

        val angle = progress / 100f * 360 - 90
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        progressRect[halfWidth * 0.75f, halfHeight * 0.75f, halfWidth * 1.25f] = halfHeight * 1.25f
        biggerRect.set(progressRect)
        val margin = 8
        biggerRect[progressRect.left - margin, progressRect.top - margin, progressRect.right + margin] =
            progressRect.bottom + margin
        canvas.saveLayer(biggerRect, null)
        if (progress != -1) {
            canvas.drawOval(progressRect, circlePaint)
            canvas.drawArc(biggerRect, angle, 360 - angle - 90, true, clearPaint)
        }
        canvas.restore()
        val circleY = height - circleMargin - circleRadius / 2
        val circleX = width - circleMargin - circleRadius / 2
        canvas.drawCircle(circleX.toFloat(), circleY.toFloat(), circleRadius.toFloat(), markBgPaint)
        captionDrawable.setBounds(
            width - circleMargin - circleRadius,
            height - circleMargin - circleRadius,
            width - circleMargin,
            height - circleMargin,
        )
        captionDrawable.draw(canvas)
    }

    private fun onDrawError(canvas: Canvas) {
        setColorFilter(
            MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError),
            PorterDuff.Mode.DARKEN,
        )
        uploadErrorDrawable.setBounds(
            (width / 2) - uploadErrorRadius,
            (height / 2) - uploadErrorRadius,
            (width / 2) + uploadErrorRadius,
            (height / 2) + uploadErrorRadius,
        )
        uploadErrorDrawable.draw(canvas)
    }
}
