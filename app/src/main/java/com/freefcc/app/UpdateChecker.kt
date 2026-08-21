package com.freefcc.app

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Checks for app updates by querying the GitHub Releases API.
 * Supports both Stable ("latest") and Experimental ("all releases / prerelease") channels.
 */
data class UpdateInfo(
    val version: String,       // e.g. "1.5.66"
    val title: String,         // e.g. "v1.5.66 — FreeFCC Custom Release"
    val changelog: String,    // release body (markdown)
    val downloadUrl: String,  // direct APK URL
    val apkSize: Long,        // bytes
    val publishedAt: String,  // ISO date
    val sha256: String?,      // expected hex digest from GitHub, or null if absent
    val isPreRelease: Boolean = false
) {
    fun isNewerThan(currentVersion: String, targetChannel: String = "stable"): Boolean {
        if (targetChannel == "stable" && isPreRelease) {
            return false
        }
        val cur = parseVersion(currentVersion)
        val new = parseVersion(version)
        val maxLen = maxOf(cur.size, new.size)
        for (i in 0 until maxLen) {
            val c = cur.getOrElse(i) { 0 }
            val n = new.getOrElse(i) { 0 }
            if (n != c) return n > c
        }
        val currentIsPre = currentVersion.contains("-")
        val newIsPre = isPreRelease || version.contains("-")
        if (currentIsPre && !newIsPre) {
            return true
        }
        return false
    }

    private fun parseVersion(v: String): List<Int> {
        return v.removePrefix("v").split("-").first().split(".").mapNotNull { it.toIntOrNull() }
    }
}

object UpdateChecker {

    internal fun normalizeReleaseBody(raw: String): String = raw
        .replace("\\r\\n", "\n")
        .replace("\\n", "\n")
        .lineSequence()
        .filterNot { line ->
            line.startsWith("APK SHA-256:", ignoreCase = true) ||
                line.startsWith("Signing certificate SHA-256:", ignoreCase = true)
        }
        .joinToString("\n")
        .trim()

    /**
     * Fetches the latest release info from GitHub.
     * If includePreRelease is true, queries all releases and returns the newest release or pre-release.
     * Returns null on any error (network, parse, etc).
     */
    fun fetchLatest(includePreRelease: Boolean = false): UpdateInfo? {
        var conn: HttpURLConnection? = null
        return try {
            val apiUrl = if (includePreRelease) ProjectLinks.ALL_RELEASES_API else ProjectLinks.LATEST_RELEASE_API
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "FreeFCC-App")
            }

            if (conn.responseCode != 200) return null

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = if (includePreRelease) {
                val array = org.json.JSONArray(body)
                if (array.length() == 0) return null
                var bestRelease = array.getJSONObject(0)
                for (i in 1 until array.length()) {
                    val cand = array.getJSONObject(i)
                    val candTag = cand.optString("tag_name", "").removePrefix("v")
                    val bestTag = bestRelease.optString("tag_name", "").removePrefix("v")
                    val candV = candTag.split("-").first().split(".").mapNotNull { it.toIntOrNull() }
                    val bestV = bestTag.split("-").first().split(".").mapNotNull { it.toIntOrNull() }
                    val maxLen = maxOf(candV.size, bestV.size)
                    var candIsGreater = false
                    var checked = false
                    for (j in 0 until maxLen) {
                        val cA = candV.getOrElse(j) { 0 }
                        val cB = bestV.getOrElse(j) { 0 }
                        if (cA != cB) {
                            candIsGreater = cA > cB
                            checked = true
                            break
                        }
                    }
                    if (!checked) {
                        val candPre = cand.optBoolean("prerelease", false)
                        val bestPre = bestRelease.optBoolean("prerelease", false)
                        if (!candPre && bestPre) candIsGreater = true
                    }
                    if (candIsGreater) {
                        bestRelease = cand
                    }
                }
                bestRelease
            } else {
                JSONObject(body)
            }

            val tagName = json.optString("tag_name", "").removePrefix("v")
            val name = json.optString("name", "v$tagName")
            val changelog = normalizeReleaseBody(json.optString("body", ""))
            val publishedAt = json.optString("published_at", "")
            val isPreRelease = json.optBoolean("prerelease", false)

            // Find the first APK asset
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            var apkSize = 0L
            var sha256: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val nameField = asset.optString("name", "")
                if (nameField.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0)
                    sha256 = asset.optString("digest", "")
                        .removePrefix("sha256:")
                        .takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
                    break
                }
            }

            if (apkUrl == null) return null

            UpdateInfo(
                version = tagName,
                title = name,
                changelog = changelog,
                downloadUrl = apkUrl,
                apkSize = apkSize,
                publishedAt = publishedAt,
                sha256 = sha256,
                isPreRelease = isPreRelease
            )
        } catch (e: Exception) {
            Log.w("FreeFCC-Update", "fetchLatest failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Downloads the APK file to the app cache directory.
     * Calls onProgress with bytes downloaded / total bytes.
     * Verifies the SHA-256 digest if the GitHub release provided one.
     * Returns the downloaded file, or null on failure (including hash mismatch).
     */
    fun downloadApk(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 30000
                setRequestProperty("User-Agent", "FreeFCC-App")
            }

            if (conn.responseCode != 200) return null

            val totalBytes = conn.contentLengthLong.coerceAtLeast(1L)
            val outputDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outputFile = File(outputDir, "freefcc_update.apk")
            val md = info.sha256?.let { MessageDigest.getInstance("SHA-256") }
            var downloaded = 0L

            FileOutputStream(outputFile).use { fos ->
                conn.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        fos.write(buffer, 0, read)
                        md?.update(buffer, 0, read)
                        downloaded += read
                        onProgress((downloaded.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
            }

            if (info.apkSize > 0 && downloaded != info.apkSize) {
                outputFile.delete()
                return null
            }

            if (md != null) {
                val actual = md.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(info.sha256, ignoreCase = true)) {
                    outputFile.delete()
                    return null
                }
            }

            outputFile
        } catch (e: Exception) {
            Log.w("FreeFCC-Update", "downloadApk failed: ${e.javaClass.simpleName}: ${e.message}")
            try { File(context.cacheDir, "updates/freefcc_update.apk").delete() } catch (_: Exception) {}
            null
        } finally {
            conn?.disconnect()
        }
    }
}
