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

package app.pachli.core.ui

import android.content.Context
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.pachli.core.common.PachliError
import app.pachli.core.common.extensions.visible
import app.pachli.core.ui.databinding.ViewBackgroundMessageBinding
import app.pachli.core.ui.extensions.getDrawableRes
import app.pachli.core.ui.extensions.getErrorString
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import java.util.regex.Pattern

/** Kinds of message that can be shown */
sealed interface BackgroundMessage {
    @get:DrawableRes val drawableRes: Int

    @get:StringRes val stringRes: Int

    /**
     * Generic "Nothing here" message and image, for use when a collection has
     * no items.
     *
     * @param stringRes Alternative string resource to use as the message
     */
    data class Empty(override val stringRes: Int = R.string.message_empty) : BackgroundMessage {
        override val drawableRes: Int = R.drawable.elephant_friend_empty
    }

    /**
     * Generic "An error occurred" message and image
     *
     * @param stringRes Alternative string resource to use as the message
     */
    data class GenericError(override val stringRes: Int = R.string.error_generic) : BackgroundMessage {
        override val drawableRes: Int = R.drawable.errorphant_error
    }

    /**
     * Generic "A network error occurred" message and image
     *
     * @param stringRes Alternative string resource to use as the message
     */
    data class Network(override val stringRes: Int = R.string.error_network) : BackgroundMessage {
        override val drawableRes: Int = R.drawable.errorphant_offline
    }
}

/**
 * This view is used for screens with content which may be empty or might have failed to download.
 */
class BackgroundMessageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewBackgroundMessageBinding.inflate(LayoutInflater.from(context), this)

    init {
        gravity = Gravity.CENTER_HORIZONTAL
        orientation = VERTICAL

        if (isInEditMode) {
            setup(BackgroundMessage.Network())
        }
    }

    fun setup(throwable: Throwable, listener: ((v: View) -> Unit)? = null) {
        setup(throwable.getDrawableRes(), throwable.getErrorString(context), listener)
    }

    fun setup(error: PachliError, listener: ((v: View) -> Unit)? = null) {
        setup(error.getDrawableRes(), error.fmt(context), listener)
    }

    fun setup(message: BackgroundMessage, listener: ((v: View) -> Unit)? = null) {
        setup(message.drawableRes, message.stringRes, listener)
    }

    private fun setup(
        @DrawableRes imageRes: Int,
        @StringRes messageRes: Int,
        clickListener: ((v: View) -> Unit)? = null,
    ) = setup(imageRes, context.getString(messageRes), clickListener)

    /**
     * Setup image, message and button.
     * If [clickListener] is `null` then the button will be hidden.
     */
    private fun setup(
        @DrawableRes imageRes: Int,
        message: String,
        clickListener: ((v: View) -> Unit)? = null,
    ) {
        binding.messageTextView.text = message
        binding.messageTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.imageView.setImageResource(imageRes)
        binding.button.setOnClickListener(clickListener)
        binding.button.visible(clickListener != null)
    }

    fun showHelp(@StringRes helpRes: Int) {
        val size: Int = binding.helpText.textSize.toInt() + 2
        val color = binding.helpText.currentTextColor
        val text = context.getText(helpRes)
        val textWithDrawables = addDrawables(text, color, size, context)

        binding.helpText.setText(textWithDrawables, TextView.BufferType.SPANNABLE)

        binding.helpText.visible(true)
    }

    /**
     * Replaces text of the form [iconics name] with their spanned counterparts (ImageSpan).
     */
    private fun addDrawables(text: CharSequence, color: Int, size: Int, context: Context): Spannable {
        val alignment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) DynamicDrawableSpan.ALIGN_CENTER else DynamicDrawableSpan.ALIGN_BASELINE

        val builder = SpannableStringBuilder(text)

        val pattern = Pattern.compile("\\[iconics ([0-9a-z_]+)\\]")
        val matcher = pattern.matcher(builder)
        while (matcher.find()) {
            val resourceName = matcher.group(1)
                ?: continue

            val drawable = IconicsDrawable(context, GoogleMaterial.getIcon(resourceName))
            drawable.setBounds(0, 0, size, size)
            drawable.setTint(color)

            builder.setSpan(ImageSpan(drawable, alignment), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return builder
    }
}
