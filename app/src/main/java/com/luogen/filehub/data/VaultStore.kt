package com.luogen.filehub.data

import android.content.Context
import com.luogen.filehub.model.FileItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** 加密保险箱：文件以 AES-256-GCM 加密存放在应用私有目录 .vault，其他文件管理器不可见 */
object VaultStore {

    fun dir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null), ".vault")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun metaFile(ctx: Context) = File(dir(ctx), "vault.json")

    data class Entry(
        val id: String,
        val name: String,
        val mime: String,
        val size: Long,
        val time: Long,
        val orig: String = ""
    ) {
        fun toItem(ctx: Context): FileItem {
            val f = File(dir(ctx), id)
            return FileItem(
                path = f.absolutePath, name = name, size = size, modified = time,
                mime = mime, id = id, encrypted = true
            )
        }
    }

    fun list(ctx: Context): List<Entry> {
        val f = metaFile(ctx)
        if (!f.exists()) return emptyList()
        val out = ArrayList<Entry>()
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    Entry(
                        id = o.optString("id"), name = o.optString("name"),
                        mime = o.optString("mime"), size = o.optLong("size"),
                        time = o.optLong("time"), orig = o.optString("orig")
                    )
                )
            }
        } catch (_: Exception) {}
        return out.sortedByDescending { it.time }
    }

    private fun save(ctx: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().put("id", e.id).put("name", e.name).put("mime", e.mime)
                    .put("size", e.size).put("time", e.time).put("orig", e.orig)
            )
        }
        try { metaFile(ctx).writeText(arr.toString()) } catch (_: Exception) {}
    }

    fun add(ctx: Context, src: File, key: SecretKey): Boolean {
        if (!src.exists() || src.isDirectory) return false
        val id = "v_" + System.currentTimeMillis() + "_" + (Math.random() * 10000).toInt() + ".enc"
        val target = File(dir(ctx), id)
        val ok = Crypto.encryptFile(src, target, key)
        if (!ok) {
            target.delete()
            return false
        }
        val list = list(ctx).toMutableList()
        list.add(
            Entry(
                id = id, name = src.name, mime = TypeDetect.mimeOf(src.name),
                size = src.length(), time = System.currentTimeMillis(), orig = src.absolutePath
            )
        )
        save(ctx, list)
        return true
    }

    fun exportTo(ctx: Context, entry: Entry, dest: File, key: SecretKey): Boolean {
        val src = File(dir(ctx), entry.id)
        if (!src.exists()) return false
        return Crypto.decryptFile(src, dest, key)
    }

    fun delete(ctx: Context, entry: Entry) {
        File(dir(ctx), entry.id).delete()
        save(ctx, list(ctx).filterNot { it.id == entry.id })
    }

    fun clear(ctx: Context) {
        list(ctx).forEach { File(dir(ctx), it.id).delete() }
        save(ctx, emptyList())
    }

    fun totalSize(ctx: Context): Long = list(ctx).sumOf { it.size }
}

/** 保险箱的解锁凭证：数字密码 / 图案密码 / 问答密码，全部只用于包裹同一个随机主密钥 */
object VaultManager {

    data class Cred(
        val id: String,
        val type: String,
        val label: String,
        val salt: String,
        val iter: Int,
        val iv: String,
        val wrapped: String
    )

    fun creds(): List<Cred> {
        val out = ArrayList<Cred>()
        try {
            val arr = JSONArray(Prefs.vaultCredsRaw())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    Cred(
                        id = o.optString("id"),
                        type = o.optString("type"),
                        label = o.optString("label"),
                        salt = o.optString("salt"),
                        iter = o.optInt("iter", Crypto.iter()),
                        iv = o.optString("iv"),
                        wrapped = o.optString("wrapped")
                    )
                )
            }
        } catch (_: Exception) {}
        return out
    }

    private fun save(list: List<Cred>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject().put("id", c.id).put("type", c.type).put("label", c.label)
                    .put("salt", c.salt).put("iter", c.iter).put("iv", c.iv).put("wrapped", c.wrapped)
            )
        }
        Prefs.setVaultCreds(arr.toString())
    }

    fun hasCreds(): Boolean = creds().isNotEmpty()

    fun hasType(type: String): Boolean = creds().any { it.type == type }

    fun types(): List<String> = creds().map { it.type }.distinct()

    /** 用指定类型 + 作答内容尝试解锁，成功返回主密钥 */
    fun unlock(type: String, payload: String): SecretKey? {
        val candidates = creds().filter { it.type == type }
        for (c in candidates) {
            val key = unwrap(c, payload) ?: continue
            return key
        }
        return null
    }

    fun unlockCred(c: Cred, payload: String): SecretKey? = unwrap(c, payload)

    private fun unwrap(c: Cred, payload: String): SecretKey? = try {
        val salt = Crypto.unb64(c.salt)
        val derive = Crypto.deriveKey(payload, salt, c.iter)
        val plain = Crypto.decrypt(derive, Crypto.unb64(c.iv) + Crypto.unb64(c.wrapped))
            ?: null
        if (plain == null) null else SecretKeySpec(plain, "AES")
    } catch (_: Throwable) {
        null
    }

    /** 新增一个凭证（包裹同一个主密钥） */
    fun addCred(type: String, label: String, payload: String, vaultKey: SecretKey): Boolean {
        return try {
            val salt = Crypto.random(16)
            val derive = Crypto.deriveKey(payload, salt, Crypto.iter())
            val enc = Crypto.encrypt(derive, vaultKey.encoded)
            val iv = enc.copyOfRange(0, 12)
            val wrapped = enc.copyOfRange(12, enc.size)
            val c = Cred(
                id = "c_" + System.currentTimeMillis() + "_" + (Math.random() * 1000).toInt(),
                type = type, label = label,
                salt = Crypto.b64(salt), iter = Crypto.iter(),
                iv = Crypto.b64(iv), wrapped = Crypto.b64(wrapped)
            )
            save(creds() + c)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** 修改某个凭证的口令（不改主密钥） */
    fun changePayload(credId: String, newPayload: String, vaultKey: SecretKey): Boolean {
        return try {
            val salt = Crypto.random(16)
            val derive = Crypto.deriveKey(newPayload, salt, Crypto.iter())
            val enc = Crypto.encrypt(derive, vaultKey.encoded)
            val iv = enc.copyOfRange(0, 12)
            val wrapped = enc.copyOfRange(12, enc.size)
            save(
                creds().map {
                    if (it.id == credId) it.copy(salt = Crypto.b64(salt), iter = Crypto.iter(), iv = Crypto.b64(iv), wrapped = Crypto.b64(wrapped))
                    else it
                }
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun removeCred(credId: String) {
        save(creds().filterNot { it.id == credId })
    }

    fun clearAll() {
        Prefs.setVaultCreds("[]")
    }

    /** 主密钥：每次解锁后由外部持有；这里提供一个随机生成的入口 */
    fun newRandomKey(): SecretKey = SecretKeySpec(Crypto.random(32), "AES")
}
