package com.luogen.filehub.data

import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {

    private const val ITER = 120000
    private const val TAG_BITS = 128

    fun random(n: Int): ByteArray {
        val b = ByteArray(n)
        SecureRandom().nextBytes(b)
        return b
    }

    fun deriveKey(payload: String, salt: ByteArray, iterations: Int = ITER): SecretKey {
        val algo = if (android.os.Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val factory = SecretKeyFactory.getInstance(algo)
        val spec = PBEKeySpec(payload.toCharArray(), salt, iterations, 256)
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    /** 加密任意字节，输出 iv + 密文 */
    fun encrypt(key: SecretKey, data: ByteArray): ByteArray {
        val iv = random(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(data)
        return iv + ct
    }

    fun decrypt(key: SecretKey, data: ByteArray): ByteArray? {
        if (data.size < 13) return null
        val iv = data.copyOfRange(0, 12)
        val ct = data.copyOfRange(12, data.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ct)
        } catch (_: Throwable) {
            null
        }
    }

    fun encryptStream(input: InputStream, out: OutputStream, key: SecretKey) {
        val iv = random(12)
        out.write(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val buf = ByteArray(128 * 1024)
        var r = input.read(buf)
        while (r > 0) {
            val enc = cipher.update(buf, 0, r)
            if (enc != null && enc.isNotEmpty()) out.write(enc)
            r = input.read(buf)
        }
        val fin = cipher.doFinal()
        if (fin != null && fin.isNotEmpty()) out.write(fin)
    }

    fun decryptStream(input: InputStream, out: OutputStream, key: SecretKey): Boolean {
        return try {
            val iv = ByteArray(12)
            var read = 0
            while (read < 12) {
                val n = input.read(iv, read, 12 - read)
                if (n <= 0) return false
                read += n
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            val buf = ByteArray(128 * 1024)
            var r = input.read(buf)
            while (r > 0) {
                val dec = cipher.update(buf, 0, r)
                if (dec != null && dec.isNotEmpty()) out.write(dec)
                r = input.read(buf)
            }
            val fin = cipher.doFinal()
            if (fin != null && fin.isNotEmpty()) out.write(fin)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    fun iter(): Int = ITER

    /** 加密文件到目标 */
    fun encryptFile(src: File, dst: File, key: SecretKey): Boolean = try {
        dst.parentFile?.mkdirs()
        FileInputStream(src).use { ins ->
            FileOutputStream(dst).use { fos -> encryptStream(ins, fos, key) }
        }
        true
    } catch (_: Throwable) {
        false
    }

    fun decryptFile(src: File, dst: File, key: SecretKey): Boolean = try {
        dst.parentFile?.mkdirs()
        FileInputStream(src).use { ins ->
            FileOutputStream(dst).use { fos -> decryptStream(ins, fos, key) }
        }
    } catch (_: Throwable) {
        false
    }
}
