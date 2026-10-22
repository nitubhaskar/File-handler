package com.example.filehandler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class UpdateInfo(
    val currentVersionCode: Int,
    val currentVersionName: String,
    val latestVersionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val isUpdateAvailable: Boolean
)

class AppUpdateManager(private val context: Context) {

    // Default target URL provided by user
    private val defaultGoogleDriveUrl = "https://drive.google.com/file/d/1Qq6kjuIqaHHYlCOQqa23D_3uCztrMrNr/view?usp=sharing"

    fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.longVersionCode.toInt()
        } catch (_: Exception) {
            1
        }
    }

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    suspend fun checkForUpdates(customUrl: String = defaultGoogleDriveUrl): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val fileId = extractFileIdFromUrl(customUrl)
            var htmlRevisionName: String? = null

            // 1. Query Google Drive HTML page metadata for highest versioned APK title (e.g., FileHandler-v2.0.apk)
            if (fileId != null) {
                htmlRevisionName = fetchDriveFileRevisionTitle(fileId)
            }

            // 2. Query remote binary stream version directly from AndroidManifest.xml inside the APK stream
            val directUrl = convertToDirectDownloadUrl(customUrl)
            val binaryVersionName = fetchBinaryVersionFromApkStream(directUrl)

            val currentCode = getCurrentVersionCode()
            val currentName = getCurrentVersionName()

            val htmlParsedVersion = htmlRevisionName?.let { parseVersionNameFromFileName(it) }

            // Pick highest version string detected across HTML revision metadata and binary stream
            val candidates = listOfNotNull(htmlParsedVersion, binaryVersionName)
            val remoteVersionName = candidates.maxWithOrNull { a, b ->
                if (isVersionNewer(a, b)) 1 else if (isVersionNewer(b, a)) -1 else 0
            } ?: "1.${currentCode + 1}"

            val isNewer = isVersionNewer(currentName, remoteVersionName)

            Result.success(
                UpdateInfo(
                    currentVersionCode = currentCode,
                    currentVersionName = currentName,
                    latestVersionCode = currentCode + 1,
                    versionName = "v$remoteVersionName",
                    apkUrl = directUrl,
                    releaseNotes = "New build v$remoteVersionName available on Google Drive.",
                    isUpdateAvailable = isNewer
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val apkFile = File(context.cacheDir, "app_update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirects = 0

            // Follow HTTP redirects manually if Google Drive redirects (e.g. 302 / 303 / 307)
            while (true) {
                connection = URL(currentUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; Mobile)")
                connection.setRequestProperty("Accept-Encoding", "identity")

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307
                ) {
                    val redirectUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (redirectUrl != null && redirects < 5) {
                        currentUrl = redirectUrl
                        redirects++
                        continue
                    }
                }
                break
            }

            val fileLength = connection.contentLength
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(apkFile)

            // High-performance 64KB buffer for fast network streaming on TV
            val data = ByteArray(64 * 1024)
            var total: Long = 0
            var count: Int
            var lastReportedProgress = -1

            while (inputStream.read(data).also { count = it } != -1) {
                total += count.toLong()
                outputStream.write(data, 0, count)

                if (fileLength > 0) {
                    val progress = ((total * 100) / fileLength).toInt()
                    // Throttle Main thread context switching to only when % actually changes
                    if (progress != lastReportedProgress) {
                        lastReportedProgress = progress
                        withContext(Dispatchers.Main) {
                            onProgress(progress)
                        }
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(apkFile: File): Boolean {
        return try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun fetchBinaryVersionFromApkStream(streamUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            var currentUrl = streamUrl
            var connection: HttpURLConnection
            var redirects = 0

            while (true) {
                connection = URL(currentUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; Mobile)")

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307
                ) {
                    val redirectUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (redirectUrl != null && redirects < 5) {
                        currentUrl = redirectUrl
                        redirects++
                        continue
                    }
                }
                break
            }

            val inputStream = connection.inputStream
            val zis = ZipInputStream(inputStream)
            var entry = zis.nextEntry
            var extractedVersion: String? = null

            while (entry != null) {
                if (entry.name == "AndroidManifest.xml") {
                    val axmlBytes = zis.readBytes()
                    extractedVersion = parseVersionNameFromAxml(axmlBytes)
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }

            zis.close()
            connection.disconnect()
            extractedVersion
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchDriveFileRevisionTitle(fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            val viewUrl = "https://drive.google.com/file/d/$fileId/view"
            val connection = URL(viewUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

            var foundName: String? = null
            if (connection.responseCode == 200) {
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val apkRegex = Regex("""(?i)([a-zA-Z0-9_-]*v?\d+\.\d+(?:\.\d+)?\.apk)""")
                val matches = apkRegex.findAll(html).map { it.groupValues[1] }.toList()
                if (matches.isNotEmpty()) {
                    foundName = matches.maxWithOrNull { a, b ->
                        val vA = parseVersionNameFromFileName(a) ?: ""
                        val vB = parseVersionNameFromFileName(b) ?: ""
                        if (isVersionNewer(vA, vB)) -1 else if (isVersionNewer(vB, vA)) 1 else 0
                    }
                }
            } else {
                connection.disconnect()
            }
            foundName
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVersionNameFromAxml(bytes: ByteArray): String? {
        return try {
            val content = String(bytes, Charsets.UTF_16LE)
            val regex = Regex("""(?i)(\d+\.\d+(?:\.\d+)?)""")
            val matches = regex.findAll(content).map { it.groupValues[1] }.toList()

            val validCandidates = matches.filter { candidate ->
                candidate != "1.0" && !candidate.startsWith("35.") && candidate != "35"
            }

            validCandidates.maxWithOrNull { a, b ->
                if (isVersionNewer(a, b)) -1 else if (isVersionNewer(b, a)) 1 else 0
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVersionNameFromFileName(fileName: String): String? {
        if (fileName.isEmpty()) return null
        val regex = Regex("""(?i)v?(\d+\.\d+(?:\.\d+)?)""")
        val match = regex.find(fileName)
        return match?.groupValues?.get(1)
    }

    private fun isVersionNewer(installedName: String, remoteName: String): Boolean {
        return try {
            val installedParts = installedName.replace(Regex("[^0-9.]"), "").split(".").mapNotNull { it.toIntOrNull() }
            val remoteParts = remoteName.replace(Regex("[^0-9.]"), "").split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(installedParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val inst = installedParts.getOrElse(i) { 0 }
                val rem = remoteParts.getOrElse(i) { 0 }
                if (rem > inst) return true
                if (rem < inst) return false
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun extractFileIdFromUrl(url: String): String? {
        val driveIdRegex = Regex("(?<=/d/|id=)([a-zA-Z0-9_-]{25,})")
        return driveIdRegex.find(url)?.value
    }

    private fun convertToDirectDownloadUrl(url: String): String {
        val fileId = extractFileIdFromUrl(url)
        return if (fileId != null) {
            "https://drive.usercontent.google.com/download?id=$fileId&confirm=t"
        } else {
            url
        }
    }
}
