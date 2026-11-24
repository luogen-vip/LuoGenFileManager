package com.luogen.filehub.ui

import android.content.Context
import com.luogen.filehub.data.FileRepo
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

/**
 * 跨页面、跨 Activity 重建都保留的全局状态：
 * - 导航栈（从某处点进去，返回仍在原处）
 * - 每个列表的滚动位置
 * - 列表数据缓存（第二次进入不再转圈）
 * - 首页统计/存储缓存
 * - 保险箱解锁状态（离开页面再回来不用重新输密码）
 */
object AppState {
    val nav = NavStack()

    // ---------- 滚动位置 ----------
    private val scroll = HashMap<String, Int>()
    fun scrollOf(key: String): Int = scroll[key] ?: 0
    fun keepScroll(key: String, value: Int) {
        scroll[key] = value
    }

    // ---------- 列表数据缓存 ----------
    private val lists = HashMap<String, List<FileItem>>()
    private val listsAt = HashMap<String, Long>()

    fun cached(key: String): List<FileItem>? = lists[key]
    fun cachedAge(key: String): Long = System.currentTimeMillis() - (listsAt[key] ?: 0L)
    fun put(key: String, data: List<FileItem>) {
        lists[key] = data
        listsAt[key] = System.currentTimeMillis()
    }
    fun drop(vararg keys: String) {
        keys.forEach { lists.remove(it); listsAt.remove(it) }
    }

    /** 有缓存先返回缓存（秒开），过期则在后台刷新 */
    suspend fun load(
        key: String,
        force: Boolean,
        ttlMs: Long = 5 * 60 * 1000L,
        block: suspend () -> List<FileItem>
    ): Pair<List<FileItem>, Boolean> {
        val old = cached(key)
        val fresh = old != null && cachedAge(key) < ttlMs
        if (!force && fresh) return old to false
        val data = withContext(Dispatchers.IO) {
            try { block() } catch (_: Throwable) { old ?: emptyList() }
        }
        put(key, data)
        return data to true
    }

    // ---------- 首页统计 / 存储 ----------
    var counts: Map<Cat, Int>? = null
        private set
    var countsAt: Long = 0L
        private set
    var storage: StorageInfo? = null
        private set
    var storageAt: Long = 0L
        private set

    fun countsFresh(): Boolean = counts != null && System.currentTimeMillis() - countsAt < 10 * 60 * 1000L
    fun storeCounts(c: Map<Cat, Int>) {
        counts = c
        countsAt = System.currentTimeMillis()
    }
    fun storeStorage(s: StorageInfo) {
        storage = s
        storageAt = System.currentTimeMillis()
    }

    // ---------- 保险箱 ----------
    var vaultKey: SecretKey? = null

    fun reset() {
        lists.clear(); listsAt.clear(); scroll.clear()
        counts = null; storage = null; vaultKey = null
    }
}
