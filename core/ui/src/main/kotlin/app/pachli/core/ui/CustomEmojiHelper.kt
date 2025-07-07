/*
 * Copyright 2020 Tusky Contributors
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

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan
import android.util.Size
import android.view.View
import android.widget.TextView
import androidx.core.graphics.withSave
import androidx.core.text.toSpannable
import app.pachli.core.model.Emoji
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition

/**
 * replaces emoji shortcodes in a text with EmojiSpans
 * @receiver the text containing custom emojis
 * @param emojis a list of the custom emojis
 * @param view a reference to the a view the emojis will be shown in (should be the TextView, but parents of the TextView are also acceptable)
 * @return the text with the shortcodes replaced by EmojiSpans
 */
fun CharSequence.emojify(glide: RequestManager, emojis: List<Emoji>?, view: View, animate: Boolean): CharSequence {
    return view.updateEmojiTargets {
        emojify(glide, emojis, animate)
    }
}

class EmojiTargetScope<T : View>(val view: T) {
    private val _targets = mutableListOf<Target<Drawable>>()
    val targets: List<Target<Drawable>>
        get() = _targets

    fun CharSequence.emojify(glide: RequestManager, emojis: List<Emoji>?, animate: Boolean): CharSequence {
        if (emojis.isNullOrEmpty()) return this

        val spannable = toSpannable()

        emojis.forEach { (shortcode, url, staticUrl) ->
            val pattern = ":$shortcode:"
            var start = indexOf(pattern)

            while (start != -1) {
                val end = start + pattern.length
                val span = EmojiSpan(view)

                spannable.setSpan(span, start, end, 0)
                val target = span.createGlideTarget(view, animate)
                glide.asDrawable().load(if (animate) url else staticUrl).into(target)
                _targets.add(target)

                start = indexOf(pattern, end)
            }
        }

        return spannable
    }
}

inline fun <T : View, R> T.updateEmojiTargets(body: EmojiTargetScope<T>.() -> R): R {
    clearEmojiTargets()
    val scope = EmojiTargetScope(this)
    val result = body(scope)
    setEmojiTargets(scope.targets)
    return result
}

@Suppress("UNCHECKED_CAST")
fun View.clearEmojiTargets() {
    getTag(R.id.custom_emoji_targets_tag)?.let { tag ->
        val targets = tag as List<Target<Drawable>>
        val requestManager = Glide.with(this)
        targets.forEach { requestManager.clear(it) }
        setTag(R.id.custom_emoji_targets_tag, null)
    }
}

fun View.setEmojiTargets(targets: List<Target<Drawable>>) {
    setTag(R.id.custom_emoji_targets_tag, targets.takeIf { it.isNotEmpty() })
}

class EmojiSpan(view: View) : ReplacementSpan() {
    private val defaultDimen = view.context.resources.getDimension(R.dimen.fallback_emoji_size).toInt()

    private val emojiSize = if (view is TextView) {
        getScaledSize(view.paint)
    } else {
        // sometimes it is not possible to determine the TextView the emoji will be shown in,
        // e.g. because it is passed to a library, so we fallback to a size that should be large
        // enough in most cases
        Size(defaultDimen, defaultDimen)
    }

    var imageDrawable: Drawable? = null

    /** Scale the emoji up/down from the calculated size. */
    var scaleFactor = 1.0f

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            // Update FontMetricsInt, otherwise span does not get drawn when
            // it covers the whole text.
            val metrics = paint.fontMetricsInt
            fm.top = metrics.top
            fm.ascent = metrics.ascent
            fm.descent = metrics.descent
            fm.bottom = metrics.bottom
        }

        return getScaledSize(paint).width
    }

    /**
     * @return The size of the emoji drawable, scaled to fit within the
     * [textSize][Paint.getTextSize] (height) of [paint], including [scaleFactor].
     */
    fun getScaledSize(paint: Paint): Size {
        return imageDrawable?.let { drawable ->
            val maxHeight = paint.textSize * 1.2
            val drawableWidth = drawable.intrinsicWidth
            val drawableHeight = drawable.intrinsicHeight
            val ratio = maxHeight / drawableHeight
            val scaledWidth = drawableWidth * ratio * scaleFactor
            val scaledHeight = drawableHeight * ratio * scaleFactor
            Size(scaledWidth.toInt(), scaledHeight.toInt())
        } ?: Size(defaultDimen, defaultDimen)
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        imageDrawable?.let { drawable ->
            canvas.withSave {
                val size = getScaledSize(paint)
                drawable.setBounds(0, 0, size.width, size.height)

                // Vertically center the emoji in the line
                val drawableSpace = (bottom - top).toDouble()
                val transY = top + (drawableSpace / 2 - size.height / 2)

                translate(x, transY.toFloat())
                drawable.draw(this)
            }
        }
    }

    fun createGlideTarget(view: View, animate: Boolean): Target<Drawable> {
        return object : CustomTarget<Drawable>(emojiSize.width, emojiSize.height) {
            override fun onStart() {
                (imageDrawable as? Animatable)?.start()
            }

            override fun onStop() {
                (imageDrawable as? Animatable)?.stop()
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                // Nothing to do
            }

            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                if (animate && resource is Animatable) {
                    resource.callback = object : Drawable.Callback {
                        override fun invalidateDrawable(who: Drawable) {
                            view.invalidate()
                        }

                        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                            view.postDelayed(what, `when`)
                        }

                        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                            view.removeCallbacks(what)
                        }
                    }
                    resource.start()
                }

                imageDrawable = resource
                view.invalidate()
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                imageDrawable?.let { currentDrawable ->
                    if (currentDrawable is Animatable) {
                        currentDrawable.stop()
                        currentDrawable.callback = null
                    }
                }
                imageDrawable = null
                view.invalidate()
            }
        }
    }
}
