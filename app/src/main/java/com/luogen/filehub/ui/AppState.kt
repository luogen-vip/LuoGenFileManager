package com.luogen.filehub.ui

import android.content.Context
import com.luogen.filehub.data.FileRepo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.StorageInfo
import com.luogen.filehub.model.VolumeInfo
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

    /** 点通知栏/首页迷你播放器时要打开的播放页 */
    var audioOpenRequested by mutableStateOf(false)
    var dataVersion by mutableStateOf(0)
    var ftpOpenRequested by mutableStateOf(false)

    // ---------- 滚动位置 ----------
    private val scroll = HashMap<String, Int>()
    private val scrollOffsets = HashMap<String, Int>()
    fun scrollOf(key: String): Int = scroll[key] ?: 0
    fun scrollOffsetOf(key: String): Int = scrollOffsets[key] ?: 0
    fun keepScroll(key: String, value: Int, offset: Int = 0) {
        scroll[key] = value
        scrollOffsets[key] = offset
    }

    // ---------- 全局搜索状态（点进文件再返回不丢）----------
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<FileItem>>(emptyList())
    var searchResultsFor by mutableStateOf("")
    var searchFilt by mutableStateOf<Cat?>(null)
    var searchExcluded by mutableStateOf<Set<Cat>>(emptySet())

    // ---------- 分类页搜索词（点进文件再返回，搜索结果不丢）----------
    private val catQueries = HashMap<String, String>()
    fun catQuery(cat: Cat): String = catQueries[cat.name] ?: ""
    fun setCatQuery(cat: Cat, q: String) { catQueries[cat.name] = q }

    private val catTabs = HashMap<String, Int>()
    private val catFavs = HashMap<String, String>()
    private val catGrids = HashMap<String, Boolean>()
    fun catTab(cat: Cat): Int = catTabs[cat.name] ?: 0
    fun setCatTab(cat: Cat, v: Int) { catTabs[cat.name] = v }
    fun catFav(cat: Cat): Cat? = catFavs[cat.name]?.let { n ->
        Cat.values().firstOrNull { it.name == n }
    }
    fun setCatFav(cat: Cat, v: Cat?) {
        if (v == null) catFavs.remove(cat.name) else catFavs[cat.name] = v.name
    }
    fun catGrid(cat: Cat): Boolean = catGrids[cat.name] ?: (cat == Cat.IMAGE || cat == Cat.VIDEO)
    fun setCatGrid(cat: Cat, v: Boolean) { catGrids[cat.name] = v }

    // ---------- 我的文件：每个浏览页记住当前目录 + 每个目录的搜索词 ----------
    private val browseCurrents = HashMap<String, String>()
    private val browseQueries = HashMap<String, String>()
    fun browseCurrent(owner: String, fallback: String): String = browseCurrents[owner] ?: fallback
    fun setBrowseCurrent(owner: String, path: String) { browseCurrents[owner] = path }
    fun browseQuery(owner: String, path: String): String = browseQueries[owner + "|" + path] ?: ""
    fun setBrowseQuery(owner: String, path: String, q: String) {
        if (q.isBlank()) browseQueries.remove(owner + "|" + path) else browseQueries[owner + "|" + path] = q
    }

    // ---------- 压缩包内条目搜索 ----------
    private val archiveFilters = HashMap<String, String>()
    fun archiveFilter(path: String): String = archiveFilters[path] ?: ""
    fun setArchiveFilter(path: String, q: String) {
        if (q.isBlank()) archiveFilters.remove(path) else archiveFilters[path] = q
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

    fun dropAllLists() {
        lists.clear()
        listsAt.clear()
    }

    /** 任何增删改/收藏/回收站操作后调用：清列表缓存 + 首页计数待刷新 */
    fun markDataChanged() {
        dropAllLists()
        countsDirty = true
        cleanDirty = true
        dataVersion++
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

    fun invalidateCounts() {
        counts = null
        countsAt = 0L
        countsDirty = true
    }

    fun countsFresh(): Boolean = counts != null && System.currentTimeMillis() - countsAt < 10 * 60 * 1000L
    fun storeCounts(c: Map<Cat, Int>) {
        counts = c
        countsAt = System.currentTimeMillis()
    }
    fun storeStorage(s: StorageInfo) {
        storage = s
        storageAt = System.currentTimeMillis()
    }

    // ---------- 存储卷（U盘/SD卡，避免每次回首页闪一下） ----------
    var volumes: List<VolumeInfo>? = null
    var volumesAt: Long = 0L
    fun storeVolumes(v: List<VolumeInfo>) {
        volumes = v
        volumesAt = System.currentTimeMillis()
    }

    var countsDirty by mutableStateOf(false)

    // ---------- 清理页 ----------
    var cleanDirty by mutableStateOf(false)
    var cleanSizes: Map<Cat, Long>? = null

    // ---------- 保险箱 ----------
    var vaultKey: SecretKey? = null

    fun reset() {
        lists.clear(); listsAt.clear(); scroll.clear()
        counts = null; storage = null; vaultKey = null
        cleanSizes = null; cleanDirty = false
    }
}
