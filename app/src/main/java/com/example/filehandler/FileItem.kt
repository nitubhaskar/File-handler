package com.example.filehandler

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val file: File,
    val name: String = file.name.ifEmpty { file.path },
    val path: String = file.absolutePath,
    val isDirectory: Boolean = file.isDirectory,
    val sizeBytes: Long = if (file.isFile) file.length() else 0L,
    val lastModified: Long = file.lastModified(),
    val isReadable: Boolean = file.canRead(),
    val isWritable: Boolean = file.canWrite(),
    val itemCount: Int = if (file.isDirectory) (file.listFiles()?.size ?: 0) else 0
) {
    val formattedSize: String
        get() {
            if (isDirectory) {
                return "$itemCount ${if (itemCount == 1) "item" else "items"}"
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

    val formattedLastModified: String
        get() {
            if (lastModified <= 0) return "Unknown"
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }

    val fileCategory: FileCategory
        get() = when {
            isDirectory -> FileCategory.FOLDER
            file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> FileCategory.IMAGE
            file.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov", "webm") -> FileCategory.VIDEO
            file.extension.lowercase() in listOf("mp3", "wav", "aac", "flac", "ogg") -> FileCategory.AUDIO
            file.extension.lowercase() in listOf("pdf", "txt", "doc", "docx", "json", "xml", "zip", "apk") -> FileCategory.DOCUMENT
            else -> FileCategory.OTHER
        }
}

enum class FileCategory {
    FOLDER,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    OTHER
}
