package ai.deepcode.android.security

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory thread-safe implementation of [SharedPreferences] for unit tests.
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = ConcurrentHashMap<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)

    override fun getString(key: String?, defValue: String?): String? {
        val v = data[key ?: return defValue]
        return (v as? String) ?: defValue
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val v = data[key ?: return defValues]
        return (v as? Set<String>)?.toMutableSet() ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int {
        val v = data[key ?: return defValue]
        return (v as? Int) ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        val v = data[key ?: return defValue]
        return (v as? Long) ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        val v = data[key ?: return defValue]
        return (v as? Float) ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        val v = data[key ?: return defValue]
        return (v as? Boolean) ?: defValue
    }

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class EditorImpl : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) {
                if (value == null) pending[key] = null else pending[key] = value
            }
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) {
                if (values == null) pending[key] = null else pending[key] = values.toSet()
            }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) {
                data.clear()
            }
            for ((k, v) in pending) {
                if (v == null) {
                    data.remove(k)
                } else {
                    data[k] = v
                }
            }
            pending.clear()
        }
    }
}
