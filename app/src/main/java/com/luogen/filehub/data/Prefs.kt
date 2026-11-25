package com.luogen.filehub.data

import android.content.Context
import android.content.SharedPreferences
import com.luogen.filehub.model.SortMode
import org.json.JSONObject

object Prefs {
    private lateinit var sp: SharedPreferences

    const val KEY_VAULT_CREDS = "vault_creds"

    // ---------- FTP ----------
    fun ftpSettings(c: Context): FtpSettings {
        val defRoot = android.os.Environment.getExternalStorageDirectory().absolutePath
        return FtpSettings(
            port = sp.getInt("ftp_port", 2121),
            user = sp.getString("ftp_user", "luogen") ?: "luogen",
            pass = sp.getString("ftp_pass", "123456") ?: "123456",
            anonymous = sp.getBoolean("ftp_anon", false),
            readOnly = sp.getBoolean("ftp_ro", false),
            root = sp.getString("ftp_root", defRoot) ?: defRoot
        )
    }

    fun setFtpSettings(c: Context, s: FtpSettings) {
        commit {
            putInt("ftp_port", s.port)
            putString("ftp_user", s.user)
            putString("ftp_pass", s.pass)
            putBoolean("ftp_anon", s.anonymous)
            putBoolean("ftp_ro", s.readOnly)
            putString("ftp_root", s.root)
        }
    }

    fun init(c: Context) {
        if (!::sp.isInitialized) {
            sp = c.applicationContext.getSharedPreferences("lgfm", Context.MODE_PRIVATE)
        }
    }

    /** 设置类数据必须落盘：后台被清理后也要保留，不能只靠 apply 的内存态 */
    private fun commit(block: SharedPreferences.Editor.() -> Unit): Boolean {
        if (!::sp.isInitialized) return false
        return try { sp.edit().apply(block).commit() } catch (_: Throwable) { false }
    }

    /** 输入框类轻量状态先用 apply，onPause 时再统一 flush */
    private fun soft(block: SharedPreferences.Editor.() -> Unit) {
        if (!::sp.isInitialized) return
        try { sp.edit().apply(block).apply() } catch (_: Throwable) {}
    }

    fun flush() {
        commit { putLong("last_flush", System.currentTimeMillis()) }
    }

    fun uiInt(key: String, def: Int = 0): Int {
        if (!::sp.isInitialized) return def
        return try { sp.getInt("ui_$key", def) } catch (_: Throwable) { def }
    }

    fun uiBool(key: String, def: Boolean = false): Boolean {
        if (!::sp.isInitialized) return def
        return try { sp.getBoolean("ui_$key", def) } catch (_: Throwable) { def }
    }

    fun uiString(key: String, def: String = ""): String {
        if (!::sp.isInitialized) return def
        return try { sp.getString("ui_$key", def) ?: def } catch (_: Throwable) { def }
    }

    fun setUiInt(key: String, v: Int) = commit { putInt("ui_$key", v) }
    fun setUiBool(key: String, v: Boolean) = commit { putBoolean("ui_$key", v) }
    fun setUiString(key: String, v: String) = commit { putString("ui_$key", v) }
    fun setUiStringSoft(key: String, v: String) = soft { putString("ui_$key", v) }

    // ---------- 导航栈（进程被清理后仍回到原处） ----------
    fun navRoutes(): List<String> {
        if (!::sp.isInitialized) return emptyList()
        return try {
            val raw = sp.getString("nav_routes", "[]") ?: "[]"
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it, "").ifEmpty { null } }
        } catch (_: Throwable) { emptyList() }
    }

    fun saveNavRoutes(routes: List<String>) {
        val arr = org.json.JSONArray()
        routes.takeLast(24).forEach { arr.put(it) }
        commit { putString("nav_routes", arr.toString()) }
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
        commit { putStringSet("fav_paths", set) }
        return added
    }

    fun addFav(path: String) {
        val set = favorites()
        set.add(path)
        commit { putStringSet("fav_paths", set) }
    }

    fun removeFav(path: String) {
        val set = favorites()
        set.remove(path)
        commit { putStringSet("fav_paths", set) }
    }

    // ---------- 排序 ----------
    fun sortMode(): SortMode {
        val v = sp.getString("sort_mode", "TIME") ?: "TIME"
        return try { SortMode.valueOf(v) } catch (_: Exception) { SortMode.TIME }
    }

    fun sortAsc(): Boolean = sp.getBoolean("sort_asc", false)

    fun setSort(mode: SortMode, asc: Boolean) {
        commit { putString("sort_mode", mode.name).putBoolean("sort_asc", asc) }
    }

    // ---------- 显示隐藏文件 ----------
    fun showHidden(): Boolean = sp.getBoolean("show_hidden", true)
    fun setShowHidden(v: Boolean) = commit { putBoolean("show_hidden", v) }

    // ---------- 扩展名警告 ----------
    fun extWarnMuted(): Boolean = sp.getBoolean("ext_warn_muted", false)
    fun muteExtWarn() = commit { putBoolean("ext_warn_muted", true) }

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
        commit { putString("orig_names", obj.toString()) }
    }

    fun moveOriginal(from: String, to: String) {
        val obj = try { JSONObject(sp.getString("orig_names", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        val v = obj.optString(from, "")
        if (v.isNotEmpty()) {
            obj.remove(from)
            try { obj.put(to, v) } catch (_: Exception) {}
            commit { putString("orig_names", obj.toString()) }
        }
    }

    // ---------- 音频自定义封面 ----------
    fun coverFor(path: String): String? = sp.getString("cover_" + path.hashCode(), null)
    fun setCover(path: String, coverPath: String?) {
        if (coverPath == null) commit { remove("cover_" + path.hashCode()) }
        else commit { putString("cover_" + path.hashCode(), coverPath) }
    }

    fun swirl(path: String): Boolean = sp.getBoolean("swirl_" + path.hashCode(), true)
    fun setSwirl(path: String, v: Boolean) = commit { putBoolean("swirl_" + path.hashCode(), v) }

    // ---------- 保险箱 ----------
    fun vaultCredsRaw(): String = sp.getString(KEY_VAULT_CREDS, "[]") ?: "[]"
    fun setVaultCreds(raw: String) = commit { putString(KEY_VAULT_CREDS, raw) }
    fun vaultConfigured(): Boolean = vaultCredsRaw().let { it.length > 6 && it != "[]" }

    fun vaultEnabled(): Boolean = sp.getBoolean("vault_enabled", true)
    fun setVaultEnabled(v: Boolean) {
        commit { putBoolean("vault_enabled", v) }
    }

    fun failedAttempts(): Int = sp.getInt("vault_fails", 0)
    fun setFailedAttempts(n: Int) = commit { putInt("vault_fails", n) }
    fun lockUntil(): Long = sp.getLong("vault_lock_until", 0L)
    fun setLockUntil(t: Long) = commit { putLong("vault_lock_until", t) }

    fun lastVaultList(): String = sp.getString("vault_layout", "list") ?: "list"
    fun setVaultLayout(v: String) = commit { putString("vault_layout", v) }

    // ---------- 首页统计缓存（重启也不用等扫描） ----------
    fun countsCache(): Map<com.luogen.filehub.model.Cat, Int> {
        val out = HashMap<com.luogen.filehub.model.Cat, Int>()
        try {
            val raw = sp.getString("counts_json", "") ?: ""
            if (raw.isNotEmpty()) {
                val o = JSONObject(raw)
                o.keys().forEach { k ->
                    val c = com.luogen.filehub.model.Cat.values().firstOrNull { it.name == k }
                    if (c != null) out[c] = o.optInt(k, 0)
                }
            }
        } catch (_: Throwable) {
        }
        return out
    }

    fun setCountsCache(m: Map<com.luogen.filehub.model.Cat, Int>) {
        try {
            val o = JSONObject()
            m.forEach { (k, v) -> o.put(k.name, v) }
            sp.edit().putString("counts_json", o.toString()).apply()
        } catch (_: Throwable) {
        }
    }
}
