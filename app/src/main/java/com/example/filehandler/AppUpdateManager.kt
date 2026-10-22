package com.example.filehandler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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
            val directUrl = convertToDirectDownloadUrl(customUrl)
            val connection = URL(directUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; Mobile)")

            val contentType = connection.contentType ?: ""
            val currentCode = getCurrentVersionCode()
            val currentName = getCurrentVersionName()

            // If the URL serves JSON configuration
            if (contentType.contains("json") || contentType.contains("text")) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(responseText)
                val latestCode = json.optInt("latestVersionCode", currentCode + 1)
                val versionName = json.optString("versionName", "1.${latestCode}")
                val apkUrl = json.optString("apkUrl", customUrl)
                val notes = json.optString("releaseNotes", "New performance, feature, and UI updates available.")

                Result.success(
                    UpdateInfo(
                        currentVersionCode = currentCode,
                        currentVersionName = currentName,
                        latestVersionCode = latestCode,
                        versionName = versionName,
                        apkUrl = convertToDirectDownloadUrl(apkUrl),
                        releaseNotes = notes,
                        isUpdateAvailable = latestCode > currentCode
                    )
                )
            } else {
                // If the URL directly points to an APK file (or Google Drive binary link)
                connection.disconnect()
                val targetCode = currentCode + 1
                val targetName = "v1.${targetCode}"

                Result.success(
                    UpdateInfo(
                        currentVersionCode = currentCode,
                        currentVersionName = currentName,
                        latestVersionCode = targetCode,
                        versionName = targetName,
                        apkUrl = directUrl,
                        releaseNotes = "New APK build uploaded on Google Drive.",
                        isUpdateAvailable = true
                    )
                )
            }
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

    private fun convertToDirectDownloadUrl(url: String): String {
        // Extract Google Drive File ID if present
        val driveIdRegex = Regex("(?<=/d/|id=)([a-zA-Z0-9_-]{25,})")
        val match = driveIdRegex.find(url)
        return if (match != null) {
            val fileId = match.value
            "https://drive.usercontent.google.com/download?id=$fileId&confirm=t"
        } else {
            url
        }
    }
}
