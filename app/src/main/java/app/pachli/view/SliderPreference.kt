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

package app.pachli.view

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import app.pachli.R
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.designsystem.R as DR
import app.pachli.databinding.PrefSliderBinding
import com.google.android.material.slider.LabelFormatter.LABEL_GONE
import com.google.android.material.slider.Slider
import java.lang.Float.max
import java.lang.Float.min

/**
 * Slider preference
 *
 * Similar to [androidx.preference.SeekBarPreference], but better because:
 *
 * - Uses a [Slider] instead of a [android.widget.SeekBar]. Slider supports float values, and step sizes
 *   other than 1.
 * - Displays the currently selected value in the Preference's summary, for consistency
 *   with platform norms.
 * - Icon buttons can be displayed at the start/end of the slider. Pressing them will
 *   increment/decrement the slider by `stepSize`.
 * - User can supply a custom formatter to format the summary value
 */
class SliderPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes),
    Slider.OnChangeListener,
    Slider.OnSliderTouchListener {

    /** Backing property for `value` */
    private var _value = 0F

    /**
     * @see Slider.getValue
     * @see Slider.setValue
     */
    var value: Float = DEFAULT_VALUE
        get() = _value
        set(v) {
            val clamped = max(max(v, valueFrom), min(v, valueTo))
            if (clamped == field) return
            _value = clamped
            persistFloat(v)
            notifyChanged()
        }

    /** @see Slider.setValueFrom */
    var valueFrom: Float

    /** @see Slider.setValueTo */
    var valueTo: Float

    /** @see Slider.setStepSize */
    var stepSize: Float

    /**
     * Format string to be applied to values before setting the summary. For more control set
     * [SliderPreference.formatter]
     */
    var format: String = DEFAULT_FORMAT

    /**
     * Function that will be used to format the summary. The default formatter formats using the
     * value of the [SliderPreference.format] property.
     */
    var formatter: (Float) -> String = { format.format(it) }

    /**
     * Optional icon to show in a button at the start of the slide. If non-null the button is
     * shown. Clicking the button decrements the value by one step.
     */
    var decrementIcon: Drawable? = null

    /**
     * Optional icon to show in a button at the end of the slider. If non-null the button is
     * shown. Clicking the button increments the value by one step.
     */
    var incrementIcon: Drawable? = null

    /** View binding */
    private lateinit var binding: PrefSliderBinding

    init {
        // Using `widgetLayoutResource` here would be incorrect, as that tries to put the entire
        // preference layout to the right of the title and summary.
        layoutResource = R.layout.pref_slider

        val a = context.obtainStyledAttributes(attrs, DR.styleable.SliderPreference, defStyleAttr, defStyleRes)

        value = a.getFloat(DR.styleable.SliderPreference_android_value, DEFAULT_VALUE)
        valueFrom = a.getFloat(DR.styleable.SliderPreference_android_valueFrom, DEFAULT_VALUE_FROM)
        valueTo = a.getFloat(DR.styleable.SliderPreference_android_valueTo, DEFAULT_VALUE_TO)
        stepSize = a.getFloat(DR.styleable.SliderPreference_android_stepSize, DEFAULT_STEP_SIZE)
        format = a.getString(DR.styleable.SliderPreference_format) ?: DEFAULT_FORMAT

        val decrementIconResource = a.getResourceId(DR.styleable.SliderPreference_iconStart, -1)
        if (decrementIconResource != -1) {
            decrementIcon = AppCompatResources.getDrawable(context, decrementIconResource)
        }

        val incrementIconResource = a.getResourceId(DR.styleable.SliderPreference_iconEnd, -1)
        if (incrementIconResource != -1) {
            incrementIcon = AppCompatResources.getDrawable(context, incrementIconResource)
        }

        a.recycle()
    }

    override fun onGetDefaultValue(a: TypedArray, i: Int): Any {
        return a.getFloat(i, DEFAULT_VALUE)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedFloat((defaultValue ?: DEFAULT_VALUE) as Float)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        binding = PrefSliderBinding.bind(holder.itemView)

        binding.root.isClickable = false

        binding.slider.clearOnChangeListeners()
        binding.slider.clearOnSliderTouchListeners()
        binding.slider.addOnChangeListener(this)
        binding.slider.addOnSliderTouchListener(this)
        binding.slider.value = value // sliderValue
        binding.slider.valueTo = valueTo
        binding.slider.valueFrom = valueFrom
        binding.slider.stepSize = stepSize

        // Disable the label, the value is shown in the preference summary
        binding.slider.labelBehavior = LABEL_GONE
        binding.slider.isEnabled = isEnabled

        binding.summary.show()
        binding.summary.text = formatter(value)

        decrementIcon?.let { icon ->
            binding.decrement.icon = icon
            binding.decrement.show()
            binding.decrement.setOnClickListener {
                value -= stepSize
            }
        } ?: binding.decrement.hide()

        incrementIcon?.let { icon ->
            binding.increment.icon = icon
            binding.increment.show()
            binding.increment.setOnClickListener {
                value += stepSize
            }
        } ?: binding.increment.hide()
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (!fromUser) return
        binding.summary.text = formatter(value)
    }

    override fun onStartTrackingTouch(slider: Slider) {
        // Deliberately empty
    }

    override fun onStopTrackingTouch(slider: Slider) {
        value = slider.value
    }

    companion object {
        private const val DEFAULT_VALUE_FROM = 0F
        private const val DEFAULT_VALUE_TO = 1F
        private const val DEFAULT_VALUE = 0.5F
        private const val DEFAULT_STEP_SIZE = 0.1F
        private const val DEFAULT_FORMAT = "%3.1f"
    }
}
