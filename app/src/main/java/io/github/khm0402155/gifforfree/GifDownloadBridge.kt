package io.github.khm0402155.gifforfree

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

/**
 * Receives a GIF in small base64 pieces from the page.
 * Android's JavaScript bridge cannot carry a multi-megabyte string in one call.
 */
class GifDownloadBridge(
    private val activity: Activity,
    private val requestLegacyWrite: (PendingGif) -> Unit,
) {
    data class PendingGif(val fileName: String, val bytes: ByteArray, val mime: String)

    private class Bucket(val fileName: String, val parts: Array<String?>)

    private val buckets = HashMap<String, Bucket>()

    @JavascriptInterface
    fun savePart(
        id: String,
        fileName: String,
        mime: String,
        index: Int,
        total: Int,
        data: String,
    ) {
        if (id.isBlank() || total <= 0 || total > 4096 || index !in 0 until total) return
        val base64 = takeIfComplete(id, fileName, index, total, data) ?: return
        val bytes = try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            toast(R.string.save_failed)
            return
        }
        save(fileName, bytes, mime.ifBlank { "image/gif" })
    }

    fun save(fileName: String, bytes: ByteArray, mime: String = "image/gif") {
        if (bytes.isEmpty()) {
            toast(R.string.save_failed)
            return
        }
        val name = safeName(fileName)
        if (needsLegacyWritePermission()) {
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                requestLegacyWrite(PendingGif(name, bytes, mime))
            }
            return
        }
        val saved = try {
            write(name, mime, bytes)
        } catch (_: Exception) {
            false
        }
        toast(if (saved) R.string.saved else R.string.save_failed)
    }

    private fun takeIfComplete(id: String, fileName: String, index: Int, total: Int, data: String): String? {
        synchronized(buckets) {
            val existing = buckets[id]
            val bucket = if (existing == null || existing.parts.size != total) {
                Bucket(fileName, arrayOfNulls(total)).also { buckets[id] = it }
            } else {
                existing
            }
            bucket.parts[index] = data
            if (bucket.parts.any { it == null }) return null
            buckets.remove(id)
            return bucket.parts.joinToString(separator = "")
        }
    }

    private fun needsLegacyWritePermission(): Boolean {
        return Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
    }

    private fun write(name: String, mime: String, bytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = activity.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            try {
                val stream = resolver.openOutputStream(uri)
                if (stream == null) {
                    resolver.delete(uri, null, null)
                    return false
                }
                stream.use { it.write(bytes) }
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
            val published = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, published, null, null)
            return true
        }

        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) return false
        FileOutputStream(uniqueFile(dir, name)).use { stream -> stream.write(bytes) }
        return true
    }

    private fun uniqueFile(dir: File, name: String): File {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ".gif"
        var file = File(dir, base + ext)
        var count = 1
        while (file.exists()) {
            file = File(dir, "$base ($count)$ext")
            count += 1
        }
        return file
    }

    private fun safeName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("""[\\/:*?"<>|]"""), "_").take(80)
        val named = cleaned.ifBlank { "animation" }
        return if (named.endsWith(".gif", ignoreCase = true)) named else "$named.gif"
    }

    private fun toast(messageId: Int) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            Toast.makeText(activity, messageId, Toast.LENGTH_LONG).show()
        }
    }
}
