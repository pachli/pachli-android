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
import android.util.AttributeSet
import androidx.preference.ListPreference
import app.pachli.core.preferences.PreferenceEnum

/**
 * Displays the different enums in a [PreferenceEnum], allowing the user to choose one
 * value.
 *
 * A [SummaryProvider][androidx.preference.Preference.SummaryProvider] is automatically
 * set to show the chosen value.
 */
class EnumListPreference<T> @JvmOverloads constructor(
    clazz: Class<T>,
    private val context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = android.R.attr.dialogPreferenceStyle,
) : ListPreference(context, attrs, defStyleAttr, defStyleRes)
    where T : Enum<T>,
          T : PreferenceEnum {
    init {
        entries = clazz.enumConstants?.map { context.getString(it.displayResource) }?.toTypedArray<CharSequence>()
            ?: emptyArray()
        entryValues = clazz.enumConstants?.map { it.value ?: it.name }?.toTypedArray<CharSequence>() ?: emptyArray()
        setSummaryProvider { entry }
    }

    @Deprecated(
        "Do not use, call setDefaultValue with an enum",
        replaceWith = ReplaceWith("setDefaultValue(defaultValue)"),
        level = DeprecationLevel.ERROR,
    )
    override fun setDefaultValue(defaultValue: Any?) {
        throw IllegalStateException("Call setDefaultValue with an enum value")
    }

    /**
     * Sets the default value for this preference, which will be set either if persistence is off
     * or persistence is on and the preference is not found in the persistent storage.
     *
     * @param defaultValue The default value
     */
    fun setDefaultValue(defaultValue: T) {
        super.setDefaultValue(defaultValue.value ?: defaultValue.name)
    }

    companion object {
        // Can't use reified types in a class constructor, but you can in inline
        // functions, so this helper supplies the correct type for the constructor's
        // first class parameter, making it more ergonomic to use this class.
        inline operator fun <reified T> invoke(
            context: Context,
            attrs: AttributeSet? = null,
            defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
            defStyleRes: Int = android.R.attr.dialogPreferenceStyle,
        ): EnumListPreference<T>
            where T : Enum<T>,
                  T : PreferenceEnum = EnumListPreference(
            T::class.java,
            context,
            attrs,
            defStyleAttr,
            defStyleRes,
        )
    }
}
