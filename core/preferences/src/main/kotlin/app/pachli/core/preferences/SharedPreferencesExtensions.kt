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
