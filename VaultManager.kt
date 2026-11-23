package com.onion.calcognito

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object VaultManager {
    private const val KEY_ALIAS = "CalculatorVaultKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val TAG_BYTES = 16
    private const val IV_SIZE = 12
    private const val CHUNK = 64 * 1024
    private const val ENC_CHUNK = CHUNK + TAG_BYTES
    private const val COPY_BUF = 1 shl 20
    private const val THUMB_MAX = 200
    private const val ENC_SUFFIX = ".enc"
    private const val PLAIN_SUFFIX = ".vlt"
    private const val PART_SUFFIX = ".part"
    private const val PREF_NAME = "vault_prefs"
    private const val KEY_PIN_HASH = "vault_pin_hash"
    private const val TEMP_DIR = "vault_tmp"
    private const val PREF_ORIGIN = "vault_origin"

    private val MAGIC = "CVLT-V2\u0000".toByteArray(Charsets.ISO_8859_1)
    private val lock = Any()

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun isPinSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_PIN_HASH)
    }

    fun setPin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PIN_HASH, pin.hashCode().toString()).apply()
    }

    fun checkPin(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return saved == pin.hashCode().toString()
    }

    private fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, "secure_vault")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun dirFor(context: Context, folder: String?): File =
        if (folder == null) getVaultDir(context) else File(getVaultDir(context), folder).apply { mkdirs() }

    private fun isVaultFile(f: File) = f.isFile && (f.name.endsWith(ENC_SUFFIX) || f.name.endsWith(PLAIN_SUFFIX))

    fun listFiles(context: Context, folder: String? = null): List<File> {
        val files = dirFor(context, folder).listFiles { f -> isVaultFile(f) }
        return files?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    fun isEncrypted(file: File): Boolean = file.name.endsWith(ENC_SUFFIX)

    private fun suffixOf(file: File): String = if (isEncrypted(file)) ENC_SUFFIX else PLAIN_SUFFIX

    fun displayName(file: File): String = file.name.removeSuffix(suffixOf(file))

    fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        EXTRA_MIME[ext]?.let { return it }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /** Media types Android's MimeTypeMap doesn't know (or maps inconsistently), so every audio / video file gets a player. */
    private val EXTRA_MIME: Map<String, String> = mapOf(
        // video
        "mkv" to "video/x-matroska", "webm" to "video/webm", "mp4" to "video/mp4", "m4v" to "video/mp4",
        "mov" to "video/quicktime", "avi" to "video/x-msvideo", "wmv" to "video/x-ms-wmv", "asf" to "video/x-ms-asf",
        "flv" to "video/x-flv", "f4v" to "video/x-flv", "3gp" to "video/3gpp", "3gpp" to "video/3gpp",
        "3g2" to "video/3gpp2", "mpg" to "video/mpeg", "mpeg" to "video/mpeg", "mpe" to "video/mpeg",
        "vob" to "video/mpeg", "ts" to "video/mp2t", "m2ts" to "video/mp2t", "mts" to "video/mp2t",
        "ogv" to "video/ogg", "divx" to "video/x-msvideo", "rm" to "video/vnd.rn-realvideo",
        "rmvb" to "video/vnd.rn-realvideo", "mxf" to "video/mxf",
        // audio
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "m4b" to "audio/mp4", "m4r" to "audio/mp4",
        "aac" to "audio/aac", "adts" to "audio/aac", "flac" to "audio/flac", "ogg" to "audio/ogg",
        "oga" to "audio/ogg", "opus" to "audio/opus", "wav" to "audio/x-wav", "wave" to "audio/x-wav",
        "wma" to "audio/x-ms-wma", "amr" to "audio/amr", "awb" to "audio/amr-wb", "mka" to "audio/x-matroska",
        "mid" to "audio/midi", "midi" to "audio/midi", "kar" to "audio/midi", "rtttl" to "audio/midi",
        "ac3" to "audio/ac3", "eac3" to "audio/eac3", "dts" to "audio/vnd.dts", "ape" to "audio/x-ape",
        "alac" to "audio/mp4", "aif" to "audio/x-aiff", "aiff" to "audio/x-aiff", "aifc" to "audio/x-aiff",
        "mp2" to "audio/mpeg", "mpga" to "audio/mpeg", "ra" to "audio/x-pn-realaudio", "caf" to "audio/x-caf",
        // images that MimeTypeMap misses
        "heic" to "image/heic", "heif" to "image/heic", "webp" to "image/webp", "avif" to "image/avif",
    )

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().trimStart('.')
        return cleaned.ifEmpty { "file_${System.currentTimeMillis()}" }
    }

    private fun nameTaken(dir: File, base: String) =
        File(dir, base + ENC_SUFFIX).exists() || File(dir, base + PLAIN_SUFFIX).exists()

    private fun uniqueFile(dir: File, safeName: String, suffix: String): File {
        if (!nameTaken(dir, safeName)) return File(dir, safeName + suffix)
        val dot = safeName.lastIndexOf('.')
        val base = if (dot > 0) safeName.substring(0, dot) else safeName
        val ext = if (dot > 0) safeName.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!nameTaken(dir, candidate)) return File(dir, candidate + suffix)
            i++
        }
    }

    // --- Original location tracking (so Unhide can restore a file to the folder it came from) ---

    private fun originKey(context: Context, file: File): String =
        file.relativeTo(getVaultDir(context)).path

    private fun recordOrigin(context: Context, file: File, volume: String, relDir: String) {
        val prefs = context.getSharedPreferences(PREF_ORIGIN, Context.MODE_PRIVATE)
        prefs.edit().putString(originKey(context, file), "$volume|$relDir").apply()
    }

    private fun peekOrigin(context: Context, file: File): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREF_ORIGIN, Context.MODE_PRIVATE)
        val raw = prefs.getString(originKey(context, file), null) ?: return null
        val idx = raw.indexOf('|')
        if (idx < 0) return null
        return raw.substring(0, idx) to raw.substring(idx + 1)
    }

    private fun clearOrigin(context: Context, file: File) {
        val prefs = context.getSharedPreferences(PREF_ORIGIN, Context.MODE_PRIVATE)
        prefs.edit().remove(originKey(context, file)).apply()
    }

    private fun moveOrigin(context: Context, from: File, to: File) {
        val prefs = context.getSharedPreferences(PREF_ORIGIN, Context.MODE_PRIVATE)
        val fromKey = originKey(context, from)
        val value = prefs.getString(fromKey, null) ?: return
        prefs.edit().remove(fromKey).putString(originKey(context, to), value).apply()
    }

    /** Best-effort: figure out which folder (and volume) a picked file originally lived in. */
    private fun resolveOriginalLocation(context: Context, uri: Uri): Pair<String, String>? {
        // A plain file picked in the built-in file picker: the folder is simply part of its path.
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            @Suppress("DEPRECATION")
            val primary = Environment.getExternalStorageDirectory().path
            if (path.startsWith("$primary/")) {
                return "primary" to path.removePrefix("$primary/").substringBeforeLast('/', "")
            }
            val m = Regex("^/storage/([^/]+)/(.*)$").find(path) ?: return null
            return m.groupValues[1] to m.groupValues[2].substringBeforeLast('/', "")
        }
        try {
            if (DocumentsContract.isDocumentUri(context, uri) &&
                uri.authority == "com.android.externalstorage.documents"
            ) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":", limit = 2)
                if (split.size == 2) {
                    val volume = split[0]
                    val dir = split[1].substringBeforeLast('/', "")
                    return volume to dir
                }
            }
        } catch (_: Exception) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val media = toMediaStoreUri(context, uri) ?: uri
                context.contentResolver.query(
                    media,
                    arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.VOLUME_NAME),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val relIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        val volIdx = c.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
                        val rel = (if (relIdx >= 0) c.getString(relIdx) else null)?.trim('/')
                        val vol = if (volIdx >= 0) c.getString(volIdx) else null
                        if (!rel.isNullOrEmpty()) return (vol ?: "primary") to rel
                    }
                }
            } catch (_: Exception) {
            }
        }

        try {
            val media = toMediaStoreUri(context, uri) ?: uri
            context.contentResolver.query(media, arrayOf("_data"), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex("_data")
                    val path = if (idx >= 0) c.getString(idx) else null
                    val root = Environment.getExternalStorageDirectory().path
                    if (path != null && path.startsWith(root)) {
                        val rel = path.removePrefix(root).trim('/').substringBeforeLast('/', "")
                        return "primary" to rel
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun cleanFolderName(name: String): String? {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().trimStart('.')
        return cleaned.take(60).trim().ifEmpty { null }
    }

    /** The folder hidden apps are kept in. It always exists and can't be renamed or deleted. */
    const val APPS_FOLDER = "Apps"

    fun listFolders(context: Context): List<String> {
        File(getVaultDir(context), APPS_FOLDER).mkdirs()
        val dirs = getVaultDir(context).listFiles { f -> f.isDirectory }
        return dirs?.map { it.name }?.sortedBy { it.lowercase() } ?: emptyList()
    }

    fun folderExists(context: Context, name: String): Boolean = File(getVaultDir(context), name).isDirectory

    fun createFolder(context: Context, name: String): Boolean {
        val dir = File(getVaultDir(context), name)
        return !dir.exists() && dir.mkdirs()
    }

    fun renameFolder(context: Context, old: String, new: String): Boolean {
        if (old == APPS_FOLDER || new == APPS_FOLDER) return false
        val target = File(getVaultDir(context), new)
        if (target.exists()) return false
        return File(getVaultDir(context), old).renameTo(target)
    }

    fun countFiles(context: Context, folder: String): Int = listFiles(context, folder).size

    fun deleteFolder(context: Context, folder: String) {
        if (folder == APPS_FOLDER) return
        val dir = File(getVaultDir(context), folder)
        listFiles(context, folder).forEach { moveFile(context, it, null) }
        dir.delete()
    }

    fun renameFile(context: Context, file: File, newName: String): File? = synchronized(lock) {
        val dir = file.parentFile ?: return@synchronized null
        val safe = sanitize(newName)
        if (safe == displayName(file)) return@synchronized file
        if (nameTaken(dir, safe)) return@synchronized null
        val dest = File(dir, safe + suffixOf(file))
        if (file.renameTo(dest)) { moveOrigin(context, file, dest); dest } else null
    }

    fun moveFile(context: Context, file: File, target: String?): File? = synchronized(lock) {
        val destDir = dirFor(context, target)
        if (file.parentFile?.absolutePath == destDir.absolutePath) return@synchronized file
        val dest = uniqueFile(destDir, displayName(file), suffixOf(file))
        if (file.renameTo(dest)) { moveOrigin(context, file, dest); dest } else null
    }

    /** Copies [ins] to [out] in large blocks; [cancelled] is polled before every block (throws IOException when set). */
    private fun copyStream(ins: InputStream, out: OutputStream, cancelled: () -> Boolean, onProgress: ((Long) -> Unit)?) {
        val buf = ByteArray(COPY_BUF)
        while (true) {
            if (cancelled()) throw IOException("Cancelled")
            val n = ins.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            onProgress?.invoke(n.toLong())
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    private fun wrapKey(keyBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        return cipher.iv + cipher.doFinal(keyBytes)
    }

    private fun unwrapKey(wrapped: ByteArray): SecretKey {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE, getSecretKey(),
            GCMParameterSpec(TAG_LENGTH_BIT, wrapped.copyOfRange(0, IV_SIZE))
        )
        return SecretKeySpec(cipher.doFinal(wrapped, IV_SIZE, wrapped.size - IV_SIZE), "AES")
    }

    private fun gcmEncrypt(key: SecretKey, data: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        return iv + cipher.doFinal(data)
    }

    private fun gcmDecrypt(key: SecretKey, blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE, key,
            GCMParameterSpec(TAG_LENGTH_BIT, blob.copyOfRange(0, IV_SIZE))
        )
        return cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }

    private fun nonce(counter: Long): ByteArray {
        val n = ByteArray(IV_SIZE)
        for (i in 0 until 8) n[IV_SIZE - 1 - i] = (counter ushr (8 * i)).toByte()
        return n
    }

    private fun encryptChunks(
        input: InputStream, out: OutputStream, key: SecretKey,
        onProgress: ((Long) -> Unit)? = null,
        cancelled: (() -> Boolean)? = null
    ): Long {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        var cur = ByteArray(CHUNK)
        var next = ByteArray(CHUNK)
        var curLen = readFully(input, cur)
        var counter = 0L
        var total = 0L
        while (true) {
            if (cancelled?.invoke() == true) throw IOException("Cancelled")
            val nextLen = if (curLen == CHUNK) readFully(input, next) else 0
            val last = nextLen <= 0
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, nonce(counter)))
            cipher.updateAAD(byteArrayOf(if (last) 1 else 0))
            out.write(cipher.doFinal(cur, 0, curLen))
            total += curLen
            onProgress?.invoke(curLen.toLong())
            if (last) break
            val tmp = cur; cur = next; next = tmp
            curLen = nextLen
            counter++
        }
        return total
    }

    private class ChunkDecryptStream(private val src: InputStream, private val key: SecretKey) : InputStream() {
        private val cipher = Cipher.getInstance(TRANSFORMATION)
        private var cur = ByteArray(ENC_CHUNK)
        private var next = ByteArray(ENC_CHUNK)
        private var curLen = 0
        private var started = false
        private var finished = false
        private var plain = ByteArray(0)
        private var pos = 0
        private var counter = 0L

        private fun fill(): Boolean {
            if (finished) return false
            if (!started) {
                curLen = readFully(src, cur)
                started = true
                if (curLen < TAG_BYTES) throw IOException("Corrupt or truncated file")
            }
            val nextLen = if (curLen == ENC_CHUNK) readFully(src, next) else 0
            val last = nextLen <= 0
            try {
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, nonce(counter)))
                cipher.updateAAD(byteArrayOf(if (last) 1 else 0))
                plain = cipher.doFinal(cur, 0, curLen)
            } catch (e: GeneralSecurityException) {
                throw IOException("Decryption failed", e)
            }
            pos = 0
            counter++
            if (last) {
                finished = true
            } else {
                val tmp = cur; cur = next; next = tmp
                curLen = nextLen
            }
            return true
        }

        override fun read(): Int {
            while (pos >= plain.size) {
                if (!fill()) return -1
            }
            return plain[pos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= plain.size) {
                if (!fill()) return -1
            }
            val n = minOf(len, plain.size - pos)
            System.arraycopy(plain, pos, b, off, n)
            pos += n
            return n
        }

        override fun close() = src.close()
    }

    private class V2Header(val fileKey: SecretKey, val thumb: ByteArray?)

    private fun readV2Header(din: DataInputStream): V2Header {
        val wrapped = ByteArray(din.readUnsignedByte())
        din.readFully(wrapped)
        val fileKey = unwrapKey(wrapped)
        val thumbLen = din.readInt()
        var thumb: ByteArray? = null
        if (thumbLen in 1..(4 * 1024 * 1024)) {
            val blob = ByteArray(thumbLen)
            din.readFully(blob)
            thumb = try { gcmDecrypt(fileKey, blob) } catch (_: Exception) { null }
        } else if (thumbLen != 0) {
            throw IOException("Corrupt header")
        }
        return V2Header(fileKey, thumb)
    }

    private fun buildHeader(wrapped: ByteArray, thumbBlob: ByteArray?): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.write(MAGIC)
        dos.writeByte(wrapped.size)
        dos.write(wrapped)
        dos.writeInt(thumbBlob?.size ?: 0)
        if (thumbBlob != null) dos.write(thumbBlob)
        dos.flush()
        return baos.toByteArray()
    }

    /**
     * Copies (and optionally encrypts) [uri] into the vault.
     *
     * The data is written to a hidden "<name>.part" file and renamed only once it is complete, so a half-written
     * file can never show up in the vault (even if the app is killed mid-copy; see [cleanStaleMigrations]).
     * [onProgress] receives the number of source bytes handled after each block (called on the calling thread);
     * [cancelled] is polled between blocks and makes the call return false, leaving nothing behind.
     */
    fun addToVault(
        context: Context, uri: Uri, originalName: String, folder: String?, encrypt: Boolean,
        cancelled: () -> Boolean = { false },
        onProgress: ((Long) -> Unit)? = null
    ): Boolean {
        val safe = sanitize(originalName)
        val target = uniqueFile(dirFor(context, folder), safe, if (encrypt) ENC_SUFFIX else PLAIN_SUFFIX)
        val part = File(target.parentFile, target.name + PART_SUFFIX)
        return try {
            if (encrypt) {
                val keyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val fileKey = SecretKeySpec(keyBytes, "AES")
                val thumbBlob = thumbnailFromUri(context, uri, mimeFor(originalName))?.let { gcmEncrypt(fileKey, it) }
                val header = buildHeader(wrapKey(keyBytes), thumbBlob)
                var copied = 0L
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open source")
                input.use { ins ->
                    BufferedOutputStream(FileOutputStream(part), COPY_BUF).use { fos ->
                        fos.write(header)
                        copied = encryptChunks(ins, fos, fileKey, onProgress, cancelled)
                    }
                }
                val chunks = if (copied == 0L) 1L else (copied + CHUNK - 1) / CHUNK
                if (part.length() != header.size + copied + TAG_BYTES * chunks) throw IOException("Incomplete write")
            } else {
                var copied = 0L
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open source")
                input.use { ins ->
                    FileOutputStream(part).use { fos ->
                        val buf = ByteArray(COPY_BUF)
                        while (true) {
                            if (cancelled()) throw IOException("Cancelled")
                            val n = ins.read(buf)
                            if (n < 0) break
                            fos.write(buf, 0, n)
                            copied += n
                            onProgress?.invoke(n.toLong())
                        }
                    }
                }
                if (part.length() != copied) throw IOException("Incomplete write")
            }
            if (cancelled()) throw IOException("Cancelled")
            if (!part.renameTo(target)) throw IOException("Could not finish file")
            resolveOriginalLocation(context, uri)?.let { (volume, relDir) -> recordOrigin(context, target, volume, relDir) }
            true
        } catch (e: Exception) {
            part.delete()
            target.delete()
            false
        }
    }

    fun tryDeleteOriginal(context: Context, uri: Uri): Boolean {
        if (uri.scheme == "file") {
            // Picked in the built-in file picker (needs all-files access, which is what let us read it).
            val f = File(uri.path ?: return false)
            val gone = f.delete() || !f.exists()
            if (gone) {
                // Tell the gallery / media database the file is gone so it doesn't keep a dead entry.
                try { MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), null, null) } catch (_: Exception) {}
            }
            return gone
        }
        val resolver = context.contentResolver
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                if (DocumentsContract.deleteDocument(resolver, uri)) return true
            } else if (resolver.delete(uri, null, null) > 0) {
                return true
            }
        } catch (_: Exception) {
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val media = toMediaStoreUri(context, uri)
            if (media != null) {
                try {
                    if (resolver.delete(media, null, null) > 0) return true
                } catch (_: Exception) {
                }
            }
        }
        return false
    }

    fun toMediaStoreUri(context: Context, uri: Uri): Uri? {
        try {
            if (uri.authority == MediaStore.AUTHORITY) return uri
            if (!DocumentsContract.isDocumentUri(context, uri)) return null
            val parts = DocumentsContract.getDocumentId(uri).split(":")
            if (parts.size != 2) return null
            val id = parts[1].toLongOrNull() ?: return null
            return when (uri.authority) {
                "com.android.providers.media.documents" -> {
                    val base = when (parts[0]) {
                        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> return null
                    }
                    ContentUris.withAppendedId(base, id)
                }
                "com.android.providers.downloads.documents" ->
                    if (parts[0] == "msf") ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id) else null
                else -> null
            }
        } catch (_: Exception) {
            return null
        }
    }

    fun openStream(file: File): InputStream {
        if (!isEncrypted(file)) return FileInputStream(file)
        val fis = FileInputStream(file)
        try {
            val din = DataInputStream(fis)
            val first = ByteArray(MAGIC.size)
            din.readFully(first)
            if (first.contentEquals(MAGIC)) {
                val header = readV2Header(din)
                return ChunkDecryptStream(fis, header.fileKey)
            }

            val iv = ByteArray(IV_SIZE)
            System.arraycopy(first, 0, iv, 0, first.size)
            din.readFully(iv, first.size, IV_SIZE - first.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(TAG_LENGTH_BIT, iv))
            return CipherInputStream(fis, cipher)
        } catch (e: Exception) {
            fis.close()
            throw e
        }
    }

    fun readDecrypted(file: File): ByteArray? = try {
        openStream(file).use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    /**
     * Makes [file] readable by another app: returns a file in the temp dir with the original display name.
     *
     *  - Plain (not encrypted) files are hard-linked, which is instant and uses no extra space whatever
     *    their size. If linking isn't possible the file is copied with a large buffer instead.
     *  - Encrypted files are decrypted into the temp dir (buffered, with progress).
     *
     * [unique] picks "name (2).ext" when the name is taken (needed when several files are prepared at once).
     * [onProgress] gets 0..1 while copying/decrypting; [cancelled] is polled between blocks.
     * Returns null on failure or cancellation (and leaves nothing behind).
     */
    fun prepareForOpen(
        context: Context, file: File, unique: Boolean = false,
        cancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null
    ): File? {
        val dir = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
        val name = displayName(file)
        var out = File(dir, name)
        if (unique) {
            val dot = name.lastIndexOf('.')
            var n = 2
            while (out.exists()) {
                out = File(dir, if (dot > 0) name.substring(0, dot) + " ($n)" + name.substring(dot) else "$name ($n)")
                n++
            }
        } else if (out.exists()) {
            out.delete()
        }

        if (!isEncrypted(file)) {
            try {
                android.system.Os.link(file.absolutePath, out.absolutePath)
                return out
            } catch (_: Exception) {
                // Fall through to a plain copy.
            }
        }

        val total = file.length().coerceAtLeast(1L)
        return try {
            openStream(file).use { ins ->
                BufferedOutputStream(FileOutputStream(out), COPY_BUF).use { os ->
                    val buf = ByteArray(256 * 1024)
                    var copied = 0L
                    while (true) {
                        if (cancelled()) throw IOException("Cancelled")
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        copied += n
                        onProgress?.invoke((copied.toFloat() / total).coerceAtMost(0.99f))
                    }
                }
            }
            out
        } catch (e: Exception) {
            out.delete()
            null
        }
    }

    fun decryptToTemp(context: Context, file: File): File? = prepareForOpen(context, file, unique = false)

    /** Prepares a file for sharing; renames on collision so same-named files don't overwrite each other. */
    fun decryptForShare(context: Context, file: File): File? = prepareForOpen(context, file, unique = true)

    fun clearTemp(context: Context) {
        File(context.cacheDir, TEMP_DIR).listFiles()?.forEach { it.delete() }
        context.cacheDir.listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    fun cleanStaleMigrations(context: Context) {
        val root = getVaultDir(context)
        root.listFiles()?.forEach { f ->
            if (f.isDirectory) f.listFiles()?.forEach { if (it.name.endsWith(".tmp") || it.name.endsWith(PART_SUFFIX)) it.delete() }
            else if (f.name.endsWith(".tmp") || f.name.endsWith(PART_SUFFIX)) f.delete()
        }
    }

    private fun sampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        var s = 1
        if (h > reqH || w > reqW) {
            val halfH = h / 2
            val halfW = w / 2
            while ((halfH / s) >= reqH && (halfW / s) >= reqW) s *= 2
        }
        return s
    }

    private fun rotate(bm: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bm
        }
        return try {
            Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, m, true)
        } catch (_: Throwable) {
            bm
        }
    }

    fun decodeBitmap(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH) }
        val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return rotate(bm, orientation)
    }

    private fun fitThumb(bm: Bitmap): Bitmap {
        val longest = maxOf(bm.width, bm.height)
        if (longest <= THUMB_MAX) return bm
        val scale = THUMB_MAX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bm, (bm.width * scale).toInt().coerceAtLeast(1), (bm.height * scale).toInt().coerceAtLeast(1), true
        )
    }

    private fun thumbToJpeg(bm: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        fitThumb(bm).compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }

    private fun videoFrame(setup: MediaMetadataRetriever.() -> Unit): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setup()
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun thumbnailFromUri(context: Context, uri: Uri, mime: String): ByteArray? {
        return try {
            val bm: Bitmap? = when {
                mime.startsWith("image/") -> {
                    val resolver = context.contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, THUMB_MAX, THUMB_MAX)
                    }
                    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    var orientation = ExifInterface.ORIENTATION_NORMAL
                    try {
                        resolver.openInputStream(uri)?.use {
                            orientation = ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                        }
                    } catch (_: Exception) {
                    }
                    decoded?.let { rotate(it, orientation) }
                }
                mime.startsWith("video/") -> videoFrame { setDataSource(context, uri) }
                else -> null
            }
            bm?.let { thumbToJpeg(it) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun plainThumb(file: File): Bitmap? {
        val mime = mimeFor(displayName(file))
        return when {
            mime.startsWith("image/") -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, THUMB_MAX, THUMB_MAX)
                }
                val decoded = BitmapFactory.decodeFile(file.path, opts) ?: return null
                val orientation = try {
                    ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } catch (_: Exception) {
                    ExifInterface.ORIENTATION_NORMAL
                }
                fitThumb(rotate(decoded, orientation))
            }
            mime.startsWith("video/") -> videoFrame { setDataSource(file.path) }?.let { fitThumb(it) }
            else -> null
        }
    }

    private const val VIDEO_THUMB_DIR = "video_thumbs"
    private const val VIDEO_THUMB_MS = 30_000L

    /**
     * The thumbnail of a plain (not encrypted) video: the frame at 30 seconds (the middle of videos shorter than
     * that), taken once and kept on disk, so the same picture comes back every time and the video is not opened
     * again. Encrypted videos keep their lock icon, so nothing readable shows. Null if no frame could be read.
     */
    fun cachedVideoFrame(context: Context, file: File): Bitmap? {
        if (isEncrypted(file)) return null
        val dir = File(context.filesDir, VIDEO_THUMB_DIR).apply { mkdirs() }
        val key = java.security.MessageDigest.getInstance("SHA-1")
            .digest("${file.absolutePath}|${file.length()}|${file.lastModified()}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cached = File(dir, "$key.jpg")
        if (cached.isFile) {
            try {
                BitmapFactory.decodeFile(cached.path)?.let { return it }
            } catch (_: Throwable) {
            }
            cached.delete()
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val atMs = if (durationMs > VIDEO_THUMB_MS) VIDEO_THUMB_MS else durationMs / 2
            val frame = retriever.getFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val thumb = frame?.let { fitThumb(it) }
            if (thumb != null) {
                try {
                    FileOutputStream(cached).use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                } catch (_: Throwable) {
                    cached.delete()
                }
            }
            thumb
        } catch (_: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /** Removes saved video thumbnails (the next vault open makes them again). */
    fun clearVideoThumbs(context: Context) {
        File(context.filesDir, VIDEO_THUMB_DIR).deleteRecursively()
    }

    fun readThumb(file: File): Bitmap? {
        return try {
            if (!isEncrypted(file)) return plainThumb(file)
            FileInputStream(file).use { fis ->
                val din = DataInputStream(fis)
                val first = ByteArray(MAGIC.size)
                din.readFully(first)
                if (!first.contentEquals(MAGIC)) return null
                val bytes = readV2Header(din).thumb ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun isLegacy(file: File): Boolean {
        if (!isEncrypted(file)) return false
        return try {
            FileInputStream(file).use {
                val b = ByteArray(MAGIC.size)
                readFully(it, b) == MAGIC.size && !b.contentEquals(MAGIC)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun migrateLegacy(file: File): Boolean {
        if (!isLegacy(file)) return false
        val tmp = File(file.path + ".tmp")
        return try {
            val keyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val fileKey = SecretKeySpec(keyBytes, "AES")
            var thumbBlob: ByteArray? = null
            val src: InputStream
            if (mimeFor(displayName(file)).startsWith("image/") && file.length() < 30L * 1024 * 1024) {
                val bytes = openStream(file).use { it.readBytes() }
                thumbBlob = decodeBitmap(bytes, THUMB_MAX, THUMB_MAX)?.let { gcmEncrypt(fileKey, thumbToJpeg(it)) }
                src = ByteArrayInputStream(bytes)
            } else {
                src = openStream(file)
            }
            val header = buildHeader(wrapKey(keyBytes), thumbBlob)
            src.use { ins ->
                FileOutputStream(tmp).use { fos ->
                    fos.write(header)
                    encryptChunks(ins, fos, fileKey)
                }
            }
            synchronized(lock) {
                if (!file.exists() || !tmp.renameTo(file)) {
                    tmp.delete()
                    false
                } else {
                    true
                }
            }
        } catch (e: Throwable) {
            tmp.delete()
            false
        }
    }

    /**
     * Encrypts a plain vault file in place (same folder, same name). Returns the new file, or null on failure or
     * cancellation (the original is then left untouched). The data is written to a hidden ".part" file and only
     * renamed once complete, so a half-finished file never appears in the vault. [onProgress] gets the bytes
     * handled after each block; [cancelled] is polled between blocks.
     */
    fun encryptInVault(
        context: Context, file: File,
        cancelled: () -> Boolean = { false },
        onProgress: ((Long) -> Unit)? = null
    ): File? {
        if (isEncrypted(file)) return file
        val dir = file.parentFile ?: return null
        var target = File(dir, displayName(file) + ENC_SUFFIX)
        if (target.exists()) target = uniqueFile(dir, displayName(file), ENC_SUFFIX)
        val part = File(dir, target.name + PART_SUFFIX)
        return try {
            val keyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val fileKey = SecretKeySpec(keyBytes, "AES")
            val thumbBlob = plainThumb(file)?.let { gcmEncrypt(fileKey, thumbToJpeg(it)) }
            val header = buildHeader(wrapKey(keyBytes), thumbBlob)
            val srcLen = file.length()
            var copied = 0L
            FileInputStream(file).use { ins ->
                BufferedOutputStream(FileOutputStream(part), COPY_BUF).use { fos ->
                    fos.write(header)
                    copied = encryptChunks(ins, fos, fileKey, onProgress, cancelled)
                }
            }
            val chunks = if (copied == 0L) 1L else (copied + CHUNK - 1) / CHUNK
            if (copied != srcLen || part.length() != header.size + copied + TAG_BYTES * chunks) throw IOException("Incomplete write")
            if (cancelled()) throw IOException("Cancelled")
            synchronized(lock) {
                if (!part.renameTo(target)) throw IOException("Could not finish file")
                moveOrigin(context, file, target)   // Unhide must still know the folder this file came from
                file.delete()
            }
            target
        } catch (e: Throwable) {
            part.delete()
            null
        }
    }

    /**
     * Decrypts an encrypted vault file in place (same folder, same name). Returns the new file, or null on failure
     * or cancellation (the original is then left untouched). Same ".part" / progress / cancel behaviour as
     * [encryptInVault].
     */
    fun decryptInVault(
        context: Context, file: File,
        cancelled: () -> Boolean = { false },
        onProgress: ((Long) -> Unit)? = null
    ): File? {
        if (!isEncrypted(file)) return file
        val dir = file.parentFile ?: return null
        var target = File(dir, displayName(file) + PLAIN_SUFFIX)
        if (target.exists()) target = uniqueFile(dir, displayName(file), PLAIN_SUFFIX)
        val part = File(dir, target.name + PART_SUFFIX)
        return try {
            openStream(file).use { ins ->
                BufferedOutputStream(FileOutputStream(part), COPY_BUF).use { out ->
                    copyStream(ins, out, cancelled, onProgress)
                }
            }
            if (cancelled()) throw IOException("Cancelled")
            synchronized(lock) {
                if (!part.renameTo(target)) throw IOException("Could not finish file")
                moveOrigin(context, file, target)
                file.delete()
            }
            target
        } catch (e: Throwable) {
            part.delete()
            null
        }
    }

    /**
     * Replaces the content of a vault file in place (same name, same encrypted/plain state).
     * Writes to a temp file first, so a failure never damages the original. Used by the text editor.
     */
    /**
     * Creates a new text note in [folder] (null = main vault) and returns it, or null if it couldn't be written.
     * The note holds a single newline so the encrypted and plain forms both start from a real (non-empty) file.
     */
    fun createTextNote(context: Context, folder: String?, title: String, encrypt: Boolean): File? {
        val clean = title.trim().removeSuffix(".txt").trim().ifEmpty { "Note" }
        val target = synchronized(lock) {
            val dir = dirFor(context, folder)
            if (!dir.exists()) dir.mkdirs()
            val f = uniqueFile(dir, sanitize("$clean.txt"), if (encrypt) ENC_SUFFIX else PLAIN_SUFFIX)
            if (!f.createNewFile()) return null
            f
        }
        return if (overwriteContent(context, target, "\n".toByteArray(Charsets.UTF_8))) target else { target.delete(); null }
    }

    fun overwriteContent(context: Context, file: File, data: ByteArray): Boolean {
        val tmp = File(file.path + ".tmp")
        return try {
            if (isEncrypted(file)) {
                val keyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val fileKey = SecretKeySpec(keyBytes, "AES")
                val header = buildHeader(wrapKey(keyBytes), null)
                FileOutputStream(tmp).use { fos ->
                    fos.write(header)
                    encryptChunks(ByteArrayInputStream(data), fos, fileKey)
                }
            } else {
                FileOutputStream(tmp).use { it.write(data) }
            }
            synchronized(lock) {
                if (!file.exists() || !tmp.renameTo(file)) {
                    tmp.delete()
                    false
                } else {
                    true
                }
            }
        } catch (e: Throwable) {
            tmp.delete()
            false
        }
    }

    /**
     * Copies a vault file back to public storage. Returns the folder it was restored to, or null on failure or
     * cancellation (nothing is left behind and the vault file is untouched). [onProgress] gets the bytes handled
     * after each block; [cancelled] is polled between blocks.
     */
    fun restoreToPublic(
        context: Context, file: File,
        cancelled: () -> Boolean = { false },
        onProgress: ((Long) -> Unit)? = null
    ): String? {
        val name = displayName(file)
        val mime = mimeFor(name)
        val origin = peekOrigin(context, file)
        return try {
            if (origin != null) {
                val (_, relDir) = origin
                val restored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    restoreWithMediaStoreAt(context, file, name, mime, relDir, cancelled, onProgress)
                } else {
                    restoreLegacyAt(context, file, name, relDir, cancelled, onProgress)
                }
                // Cancelled: keep the saved origin so a later Unhide still goes back to the same folder.
                if (!restored && cancelled()) return null
                clearOrigin(context, file)
                if (restored) relDir.ifEmpty { "Internal storage" }
                else restoreToGenericFolder(context, file, name, mime, cancelled, onProgress)
            } else {
                restoreToGenericFolder(context, file, name, mime, cancelled, onProgress)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun restoreToGenericFolder(
        context: Context, file: File, name: String, mime: String,
        cancelled: () -> Boolean, onProgress: ((Long) -> Unit)?
    ): String {
        val folder = when {
            mime.startsWith("image/") -> Environment.DIRECTORY_PICTURES
            mime.startsWith("video/") -> Environment.DIRECTORY_MOVIES
            mime.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            restoreWithMediaStore(context, file, name, mime, folder, cancelled, onProgress)
        } else {
            restoreLegacy(context, file, name, folder, cancelled, onProgress)
        }
        return folder
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun restoreWithMediaStoreAt(
        context: Context, file: File, name: String, mime: String, relDir: String,
        cancelled: () -> Boolean, onProgress: ((Long) -> Unit)?
    ): Boolean {
        if (relDir.isEmpty()) return false
        val resolver = context.contentResolver
        val collection = when {
            mime.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            mime.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            mime.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = try { resolver.insert(collection, values) } catch (e: Exception) { null } ?: return false
        return try {
            val out = resolver.openOutputStream(target) ?: throw IOException("Could not open output")
            out.use { o -> openStream(file).use { copyStream(it, o, cancelled, onProgress) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(target, done, null, null)
            true
        } catch (e: Exception) {
            try { resolver.delete(target, null, null) } catch (_: Exception) {}
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreLegacyAt(
        context: Context, file: File, name: String, relDir: String,
        cancelled: () -> Boolean, onProgress: ((Long) -> Unit)?
    ): Boolean {
        val base = Environment.getExternalStorageDirectory()
        val dir = if (relDir.isEmpty()) base else File(base, relDir)
        dir.mkdirs()
        var target = File(dir, name)
        if (target.exists()) {
            val dot = name.lastIndexOf('.')
            val baseName = if (dot > 0) name.substring(0, dot) else name
            val ext = if (dot > 0) name.substring(dot) else ""
            var i = 1
            while (target.exists()) {
                target = File(dir, "$baseName ($i)$ext")
                i++
            }
        }
        return try {
            FileOutputStream(target).use { out -> openStream(file).use { copyStream(it, out, cancelled, onProgress) } }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
            true
        } catch (e: Exception) {
            target.delete()
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun restoreWithMediaStore(
        context: Context, file: File, name: String, mime: String, folder: String,
        cancelled: () -> Boolean, onProgress: ((Long) -> Unit)?
    ) {
        val resolver = context.contentResolver
        val collection = when {
            mime.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            mime.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            mime.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(collection, values) ?: throw IOException("Could not create file")
        try {
            val out = resolver.openOutputStream(target) ?: throw IOException("Could not open output")
            out.use { o -> openStream(file).use { copyStream(it, o, cancelled, onProgress) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(target, done, null, null)
        } catch (e: Exception) {
            try { resolver.delete(target, null, null) } catch (_: Exception) {}
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreLegacy(
        context: Context, file: File, name: String, folder: String,
        cancelled: () -> Boolean, onProgress: ((Long) -> Unit)?
    ) {
        val dir = Environment.getExternalStoragePublicDirectory(folder)
        dir.mkdirs()
        var target = File(dir, name)
        if (target.exists()) {
            val dot = name.lastIndexOf('.')
            val base = if (dot > 0) name.substring(0, dot) else name
            val ext = if (dot > 0) name.substring(dot) else ""
            var i = 1
            while (target.exists()) {
                target = File(dir, "$base ($i)$ext")
                i++
            }
        }
        try {
            FileOutputStream(target).use { out -> openStream(file).use { copyStream(it, out, cancelled, onProgress) } }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
    }

    fun deleteFile(context: Context, file: File) {
        synchronized(lock) {
            if (file.exists()) file.delete()
            clearOrigin(context, file)
        }
    }

    /** Clears the saved PIN and permanently erases every file in the vault. Used by the PIN-reset flow. */
    fun resetVault(context: Context) {
        synchronized(lock) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().remove(KEY_PIN_HASH).apply()
            context.getSharedPreferences(PREF_ORIGIN, Context.MODE_PRIVATE).edit().clear().apply()
            HiddenApps.clearAll(context)
            VirtualSpace.clearAll(context)
            ShizukuHider.restoreAll(context)
            getVaultDir(context).deleteRecursively()
            getVaultDir(context) // recreate empty root
            clearTemp(context)
        }
    }

    fun applySecurityFlags(activity: Activity) {
        // Screenshots and screen recording are allowed.
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
