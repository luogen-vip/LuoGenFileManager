package com.luogen.filehub.data

import android.content.Context
import android.content.SharedPreferences
import com.luogen.filehub.model.SortMode
import org.json.JSONObject

object Prefs {
    private lateinit var sp: SharedPreferences

    const val KEY_VAULT_CREDS = "vault_creds"

    fun init(c: Context) {
        if (!::sp.isInitialized) {
            sp = c.applicationContext.getSharedPreferences("lgfm", Context.MODE_PRIVATE)
        }
    }

    // ---------- 收藏 ----------
    fun favorites(): MutableSet<String> =
        (sp.getStringSet("fav_paths", emptySet()) ?: emptySet()).toMutableSet()

    fun isFav(path: String): Boolean = favorites().contains(path)

    fun toggleFav(path: String): Boolean {
        val set = favorites()
        val added = if (set.contains(path)) {
            set.remove(path); false
        } else {
            set.add(path); true
        }
        sp.edit().putStringSet("fav_paths", set).apply()
        return added
    }

    fun addFav(path: String) {
        val set = favorites()
        set.add(path)
        sp.edit().putStringSet("fav_paths", set).apply()
    }

    fun removeFav(path: String) {
        val set = favorites()
        set.remove(path)
        sp.edit().putStringSet("fav_paths", set).apply()
    }

    // ---------- 排序 ----------
    fun sortMode(): SortMode {
        val v = sp.getString("sort_mode", "TIME") ?: "TIME"
        return try { SortMode.valueOf(v) } catch (_: Exception) { SortMode.TIME }
    }

    fun sortAsc(): Boolean = sp.getBoolean("sort_asc", false)

    fun setSort(mode: SortMode, asc: Boolean) {
        sp.edit().putString("sort_mode", mode.name).putBoolean("sort_asc", asc).apply()
    }

    // ---------- 显示隐藏文件 ----------
    fun showHidden(): Boolean = sp.getBoolean("show_hidden", true)
    fun setShowHidden(v: Boolean) = sp.edit().putBoolean("show_hidden", v).apply()

    // ---------- 扩展名警告 ----------
    fun extWarnMuted(): Boolean = sp.getBoolean("ext_warn_muted", false)
    fun muteExtWarn() = sp.edit().putBoolean("ext_warn_muted", true).apply()

    // ---------- 原始文件名（用于“恢复默认”） ----------
    fun originalOf(path: String): String? {
        val raw = sp.getString("orig_names", null) ?: return null
        return try {
            JSONObject(raw).optString(path, "").ifEmpty { null }
        } catch (_: Exception) { null }
    }

    fun rememberOriginal(path: String, originalName: String) {
        if (originalOf(path) != null) return
        val obj = try { JSONObject(sp.getString("orig_names", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        try { obj.put(path, originalName) } catch (_: Exception) {}
        sp.edit().putString("orig_names", obj.toString()).apply()
    }

    fun moveOriginal(from: String, to: String) {
        val obj = try { JSONObject(sp.getString("orig_names", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        val v = obj.optString(from, "")
        if (v.isNotEmpty()) {
            obj.remove(from)
            try { obj.put(to, v) } catch (_: Exception) {}
            sp.edit().putString("orig_names", obj.toString()).apply()
        }
    }

    // ---------- 音频自定义封面 ----------
    fun coverFor(path: String): String? = sp.getString("cover_" + path.hashCode(), null)
    fun setCover(path: String, coverPath: String?) {
        if (coverPath == null) sp.edit().remove("cover_" + path.hashCode()).apply()
        else sp.edit().putString("cover_" + path.hashCode(), coverPath).apply()
    }

    fun swirl(path: String): Boolean = sp.getBoolean("swirl_" + path.hashCode(), true)
    fun setSwirl(path: String, v: Boolean) = sp.edit().putBoolean("swirl_" + path.hashCode(), v).apply()

    // ---------- 保险箱 ----------
    fun vaultCredsRaw(): String = sp.getString(KEY_VAULT_CREDS, "[]") ?: "[]"
    fun setVaultCreds(raw: String) = sp.edit().putString(KEY_VAULT_CREDS, raw).apply()
    fun vaultConfigured(): Boolean = vaultCredsRaw().let { it.length > 6 && it != "[]" }

    fun vaultEnabled(): Boolean = sp.getBoolean("vault_enabled", true)
    fun setVaultEnabled(v: Boolean) {
        sp.edit().putBoolean("vault_enabled", v).apply()
    }

    fun failedAttempts(): Int = sp.getInt("vault_fails", 0)
    fun setFailedAttempts(n: Int) = sp.edit().putInt("vault_fails", n).apply()
    fun lockUntil(): Long = sp.getLong("vault_lock_until", 0L)
    fun setLockUntil(t: Long) = sp.edit().putLong("vault_lock_until", t).apply()

    fun lastVaultList(): String = sp.getString("vault_layout", "list") ?: "list"
    fun setVaultLayout(v: String) = sp.edit().putString("vault_layout", v).apply()
}
