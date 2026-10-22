package com.example.filehandler

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Immutable model representing a single file or directory item in the file browser list.
 *
 * Pre-computes and caches formatted display strings (e.g. human-readable file sizes and dates)
 * during background thread initialization ([Dispatchers.IO]) to eliminate main thread UI lag
 * during fast D-Pad list scrolling on TV devices.
 *
 * @property file Underlying Java [File] object on the file system.
 * @property name Display name of the file or directory.
 * @property path Absolute path on disk.
 * @property isDirectory True if this item represents a directory folder.
 * @property sizeBytes File size in bytes for files; 0 for directories.
 * @property lastModified Last modified timestamp in epoch milliseconds.
 * @property isReadable True if the current application has read access.
 * @property isWritable True if the current application has write access.
 * @property itemCount Number of immediate child entries if this item is a directory.
 * @property formattedSize Pre-formatted display size string (e.g. "24.50 MB" or "12 items").
 * @property formattedLastModified Pre-formatted last modified date string (e.g. "Nov 22, 2026 14:30").
 * @property fileCategory High-level media category used for icon and color selection.
 */
data class FileItem(
    val file: File,
    val name: String = file.name.ifEmpty { file.path },
    val path: String = file.absolutePath,
    val isDirectory: Boolean = file.isDirectory,
    val sizeBytes: Long = if (file.isFile) file.length() else 0L,
    val lastModified: Long = file.lastModified(),
    val isReadable: Boolean = file.canRead(),
    val isWritable: Boolean = file.canWrite(),
    val itemCount: Int = if (file.isDirectory) (file.list()?.size ?: 0) else 0,
    val formattedSize: String = formatSizeString(file.isDirectory, if (file.isDirectory) (file.list()?.size ?: 0) else 0, if (file.isFile) file.length() else 0L),
    val formattedLastModified: String = formatLastModifiedString(file.lastModified()),
    val fileCategory: FileCategory = computeCategory(file.isDirectory, file.extension)
)

/**
 * Formats raw file byte counts or directory item counts into human-readable strings.
 */
private fun formatSizeString(isDirectory: Boolean, count: Int, sizeBytes: Long): String {
    if (isDirectory) {
        return "$count ${if (count == 1) "item" else "items"}"
    }
    val kb = sizeBytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.2f KB", kb)
        else -> "$sizeBytes B"
    }
}

/**
 * Formats epoch timestamp milliseconds into formatted date string.
 */
private fun formatLastModifiedString(lastModified: Long): String {
    if (lastModified <= 0) return "Unknown"
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(lastModified))
}

/**
 * Computes high-level [FileCategory] based on file extension.
 */
private fun computeCategory(isDirectory: Boolean, extension: String): FileCategory {
    if (isDirectory) return FileCategory.FOLDER
    val ext = extension.lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp" -> FileCategory.IMAGE
        "mp4", "mkv", "avi", "mov", "webm" -> FileCategory.VIDEO
        "mp3", "wav", "aac", "flac", "ogg", "m4a" -> FileCategory.AUDIO
        "pdf", "txt", "doc", "docx", "json", "xml", "zip", "apk", "log" -> FileCategory.DOCUMENT
        else -> FileCategory.OTHER
    }
}

/**
 * Enumeration of media categories used for category-based icons and colors.
 */
enum class FileCategory {
    FOLDER,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    OTHER
}
