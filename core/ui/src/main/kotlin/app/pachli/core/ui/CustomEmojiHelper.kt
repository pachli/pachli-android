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
import android.text.Spannable
import android.text.style.ReplacementSpan
import android.util.Size
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.graphics.withSave
import androidx.core.text.toSpannable
import app.pachli.core.model.Emoji
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
    return toSpannable().emojify(glide, emojis, view, animate)
}

fun Spannable.emojify(glide: RequestManager, emojis: List<Emoji>?, view: View, animate: Boolean): CharSequence {
    return view.updateEmojiTargets(glide) {
        emojify(glide, emojis, animate)
    }
}

class EmojiTargetScope<T : View>(val view: T) {
    private val _targets = mutableListOf<Target<Drawable>>()
    val targets: List<Target<Drawable>>
        get() = _targets

    fun CharSequence.emojify(glide: RequestManager, emojis: List<Emoji>?, animate: Boolean): CharSequence {
        if (emojis.isNullOrEmpty()) return this

        return toSpannable().apply { emojify(glide, emojis, animate) }
    }

    fun Spannable.emojify(glide: RequestManager, emojis: List<Emoji>?, animate: Boolean): CharSequence {
        if (emojis.isNullOrEmpty()) return this

        emojis.forEach { (shortcode, url, staticUrl) ->
            var start = indexOf(shortcode)

            while (start != -1) {
                val end = start + shortcode.length
                val span = EmojiSpan(view)

                setSpan(span, start, end, 0)
                val target = span.createGlideTarget(view, animate)
                glide.asDrawable().load(if (animate) url else staticUrl).into(target)
                _targets.add(target)

                start = indexOf(shortcode, end)
            }
        }

        return this
    }
}

inline fun <T : View, R> T.updateEmojiTargets(glide: RequestManager, body: EmojiTargetScope<T>.() -> R): R {
    clearEmojiTargets(glide)
    val scope = EmojiTargetScope(this)
    val result = body(scope)
    setEmojiTargets(scope.targets)
    return result
}

@Suppress("UNCHECKED_CAST")
fun View.clearEmojiTargets(glide: RequestManager) {
    getTag(R.id.custom_emoji_targets_tag)?.let { tag ->
        val targets = tag as List<Target<Drawable>>
        targets.forEach { glide.clear(it) }
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
    private fun getScaledSize(paint: Paint): Size {
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

            override fun onLoadFailed(errorDrawable: Drawable?) = Unit

            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                if (animate && resource is Animatable) {
                    resource.callback = object : Drawable.Callback {
                        override fun invalidateDrawable(who: Drawable) {
                            invalidate()
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
                invalidate()
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

            /**
             * Invalidate, or otherwise cause the view containing the span to redraw to
             * reflect the current state of the span.
             */
            fun invalidate() {
                // Views that aren't EditText do the right thing if you call invalidate().
                if (view !is EditText) {
                    view.invalidate()
                    return
                }

                // EditText has a bug -- calling invalidate() does not cause it to call
                // the span's draw() method.
                //
                // Unless EditText.layerType is "software". But, if it's software then
                // there's a limit on the number of characters the EditText can display
                // before it starts emitting errors like "not displayed because it is
                // too large to fit into a software layer (or drawing cache), needs
                // 10210380 bytes, only 10108800 available" and then all the text
                // disappears.
                //
                // So work around this bug by recording the span's state, removing the
                // span and immediately adding it back.
                //
                // https://issuetracker.google.com/issues/446220461
                val text = view.text
                val start = text.getSpanStart(this@EmojiSpan)
                // Bail early if the span is no longer attached
                if (start == -1) return
                val end = text.getSpanEnd(this@EmojiSpan)
                val flags = text.getSpanFlags(this@EmojiSpan)
                text.removeSpan(this@EmojiSpan)
                text.setSpan(this@EmojiSpan, start, end, flags)
            }
        }
    }
}
