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

package app.pachli.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.Px
import androidx.core.graphics.drawable.toDrawable
import app.pachli.core.common.util.BlurHashDecoder
import app.pachli.core.designsystem.R as DR
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition

private val centerCropTransformation = CenterCrop()

fun loadAvatar(
    glide: RequestManager,
    url: String?,
    imageView: ImageView,
    @Px radius: Int,
    animate: Boolean,
    transforms: List<Transformation<Bitmap>>? = null,
) {
    if (url.isNullOrBlank()) {
        glide.load(DR.drawable.avatar_default)
            .into(imageView)
    } else {
        val multiTransformation = MultiTransformation(
            buildList {
                add(centerCropTransformation)
                add(RoundedCorners(radius))
                transforms?.let { this.addAll(it) }
            },
        )

        if (animate) {
            glide.load(url)
                .transform(multiTransformation)
                .placeholder(DR.drawable.avatar_default)
                .into(imageView)
        } else {
            glide.asBitmap()
                .load(url)
                .transform(multiTransformation)
                .placeholder(DR.drawable.avatar_default)
                .into(imageView)
        }
    }
}

/**
 * Loads [url] into [textView] as a compound drawable in the start
 * position.
 *
 * @param glide
 * @param url URL of image to load.
 * @param textView [TextView] to load into.
 * @param dimen Width and height to set on the drawable.
 * @param radius Corner radius to apply to the image.
 * @param animate If true, and the image is animatable, animate it.
 * @param transforms Additional optional transformations to apply to
 * the image.
 */
fun loadAvatar(
    glide: RequestManager,
    url: String?,
    textView: TextView,
    @Px dimen: Int,
    @Px radius: Int,
    animate: Boolean,
    transforms: List<Transformation<Bitmap>>? = null,
) {
    if (url.isNullOrBlank()) {
        glide.load(DR.drawable.avatar_default)
            .into(textView, dimen, dimen, animate)
    } else {
        val multiTransformation = MultiTransformation(
            buildList {
                add(centerCropTransformation)
                add(RoundedCorners(radius))
                transforms?.let { this.addAll(it) }
            },
        )

        if (animate) {
            glide
                .load(url)
                .transform(multiTransformation)
                .placeholder(DR.drawable.avatar_default)
                .into(
                    textView,
                    dimen,
                    dimen,
                    true,
                )
        } else {
            glide
                .asBitmap()
                .load(url)
                .transform(multiTransformation)
                .placeholder(DR.drawable.avatar_default)
                .into(
                    textView,
                    dimen,
                    dimen,
                )
        }
    }
}

/**
 * Sets [textView] as the target to load the resource. The resource will be set
 * as the `start` compound drawable.
 *
 * Delegates to either [BitmapTextViewTarget] or [DrawableTextViewTarget]
 * as appropriate.
 *
 * @param textView TextView to load into.
 * @param width Intended width of the compound drawable.
 * @param height Height of the compound drawable.
 * @param animate True if the resource should be animated (if it supports
 * animation)
 */
inline fun <reified T> RequestBuilder<T>.into(
    textView: TextView,
    width: Int,
    height: Int,
    animate: Boolean = false,
): com.bumptech.glide.request.target.Target<T> {
    if (T::class == Bitmap::class) {
        return into(BitmapTextViewTarget(textView, width, height) as com.bumptech.glide.request.target.Target<T>)
    }

    if (T::class == Drawable::class) {
        return into(DrawableTextViewTarget(textView, width, height, animate) as Target<T>)
    }
    throw RuntimeException("Unexpected class, ${T::class} passed to `into`")
}

/**
 * [Target][com.bumptech.glide.request.target.Target] that loads drawables into the
 * `start` position of [textView], sized to [width] and [height].
 *
 * @property animate If true, and the resource is animatable, the animation will be
 * started.
 */
class DrawableTextViewTarget(
    private val textView: TextView,
    private val width: Int,
    private val height: Int,
    private val animate: Boolean,
) : CustomTarget<Drawable>(width, height) {
    private var drawable: Drawable? = null

    override fun onStop() {
        (drawable as? Animatable)?.stop()
    }

    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
        drawable = resource

        if (animate && resource is Animatable) {
            // Glide does not apply transformations (e.g., centerCrop) to animated
            // drawables (https://github.com/bumptech/glide/issues/4942), so the
            // drawable will not be resized. Work around this by explicitly
            // setting the bounds before starting the animation.
            resource.bounds = Rect(0, 0, width, height)
            resource.start()
        }

        if (resource !is Animatable) {
            // Non-animatable drawables must be set with intrinsic bounds, otherwise they
            // don't appear.
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(resource, null, null, null)
        } else {
            // Animatable drawables must be set without intrinsic bounds, otherwise they
            // are positioned incorrectly.
            textView.setCompoundDrawablesRelative(resource, null, null, null)
        }
    }

    override fun onLoadCleared(placeholder: Drawable?) {
        (drawable as? Animatable)?.stop()
        drawable = null
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(placeholder, null, null, null)
    }
}

/**
 * [Target][com.bumptech.glide.request.target.Target] that loads bitmaps into the
 * `start` position of [textView], sized to [width] and [height].
 */
class BitmapTextViewTarget(private val textView: TextView, width: Int, height: Int) : CustomTarget<Bitmap>(width, height) {
    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
        val drawable = resource.toDrawable(textView.resources)
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
    }

    override fun onLoadCleared(placeholder: Drawable?) {
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(placeholder, null, null, null)
    }
}

fun decodeBlurHash(context: Context, blurhash: String, width: Int = 32, height: Int = 32): BitmapDrawable? {
    return BlurHashDecoder.decode(blurhash, width, height, 1f)?.toDrawable(context.resources)
}
