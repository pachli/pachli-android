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

package app.pachli.fakes

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.content.SharedPreferences.OnSharedPreferenceChangeListener

/**
 * An in-memory implementation of [SharedPreferences] suitable for use in tests.
 *
 * @param initialValues optional map of initial values
 */
@Suppress("UNCHECKED_CAST")
class InMemorySharedPreferences(
    initialValues: Map<String, Any?>? = null,
) : SharedPreferences {
    private var store: MutableMap<String, Any?> = initialValues?.toMutableMap() ?: mutableMapOf()

    private var listeners: MutableSet<OnSharedPreferenceChangeListener> = HashSet()

    private val preferenceEditor: MockSharedPreferenceEditor =
        MockSharedPreferenceEditor(this, store, listeners)

    override fun getAll(): Map<String, Any?> = store

    override fun getString(key: String?, defValue: String?) = store.getOrDefault(key, defValue) as String?

    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = store.getOrDefault(key, defValues) as MutableSet<String>?

    override fun getInt(key: String, defaultValue: Int) = store.getOrDefault(key, defaultValue) as Int

    override fun getLong(key: String, defaultValue: Long) = store.getOrDefault(key, defaultValue) as Long

    override fun getFloat(key: String, defaultValue: Float) = store.getOrDefault(key, defaultValue) as Float

    override fun getBoolean(key: String, defaultValue: Boolean) = store.getOrDefault(key, defaultValue) as Boolean

    override fun contains(key: String) = key in store

    override fun edit(): Editor = preferenceEditor

    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    class MockSharedPreferenceEditor(
        private val sharedPreferences: InMemorySharedPreferences,
        private val store: MutableMap<String, Any?>,
        private val listeners: MutableSet<OnSharedPreferenceChangeListener>,
    ) : Editor {
        private val edits: MutableMap<String, Any?> = mutableMapOf()
        private var deletes: MutableList<String> = ArrayList()

        override fun putString(key: String, value: String?): Editor {
            edits[key] = value
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): Editor {
            edits[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): Editor {
            edits[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): Editor {
            edits[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): Editor {
            edits[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): Editor {
            edits[key] = value
            return this
        }

        override fun remove(key: String): Editor {
            edits.remove(key)
            deletes.add(key)
            return this
        }

        override fun clear(): Editor {
            deletes.clear()
            store.clear()
            edits.clear()
            return this
        }

        override fun commit(): Boolean {
            deletes.forEach { key ->
                store.remove(key)
                listeners.forEach { it.onSharedPreferenceChanged(sharedPreferences, key) }
            }

            edits.forEach { entry ->
                store[entry.key] = entry.value
                listeners.forEach { it.onSharedPreferenceChanged(sharedPreferences, entry.key) }
            }

            deletes.clear()
            edits.clear()
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
