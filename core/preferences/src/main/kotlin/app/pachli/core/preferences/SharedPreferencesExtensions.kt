package app.pachli.core.preferences

import android.content.SharedPreferences

fun SharedPreferences.getNonNullString(key: String, defValue: String): String {
    return this.getString(key, defValue) ?: defValue
}

/**
 * @return The enum for the preference at [key]. If there is no value for [key]
 * in preferences, or the value can not be converted to [E], then [defValue] is
 * returned.
 */
inline fun <reified E> SharedPreferences.getEnum(
    key: String,
    defValue: E,
): E
    where E : Enum<E>,
          E : PreferenceEnum {
    return getString(key, null)?.let { PreferenceEnum.from<E>(it) } ?: defValue
}

/**
 * Sets an enum value in the preferences editor, to be written back once
 * [commit][SharedPreferences.Editor.commit] or [apply][SharedPreferences.Editor.apply]
 * are called.
 *
 * The enum is persisted using it's [value][PreferenceEnum.value] property. If that
 * is null the enum's [name][Enum.name] property is used.
 *
 * @param key The name of the preference to modify
 * @param value The new value for the preference.
 */
inline fun <reified E> SharedPreferences.Editor.putEnum(
    key: String,
    value: E,
)
    where E : Enum<E>,
          E : PreferenceEnum {
    putString(key, value.value ?: value.name)
}
