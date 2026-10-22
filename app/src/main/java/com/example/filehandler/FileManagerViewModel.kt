package com.example.filehandler

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class StorageSource(
    val label: String,
    val rootFile: File,
    val isPrimary: Boolean,
    val totalSpaceFormatted: String,
    val freeSpaceFormatted: String
)

enum class SortOption(val label: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    DATE_DESC("Date (Newest First)"),
    DATE_ASC("Date (Oldest First)"),
    SIZE_DESC("Size (Largest First)"),
    SIZE_ASC("Size (Smallest First)")
}

data class FileManagerUiState(
    val hasStoragePermission: Boolean = false,
    val currentDirectory: File = Environment.getExternalStorageDirectory(),
    val fileList: List<FileItem> = emptyList(),
    val selectedFile: FileItem? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val showDeleteDialog: Boolean = false,
    val showInfoDialog: Boolean = false,
    val showActionDialog: Boolean = false,
    val fileForAction: FileItem? = null,
    val filePendingDeletion: FileItem? = null,
    // File Operation Dialogs
    val showCreateFolderDialog: Boolean = false,
    val showCreateFileDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val fileToRename: FileItem? = null,
    val showSortDialog: Boolean = false,
    val currentSortOption: SortOption = SortOption.NAME_ASC,
    val clipboardItem: FileItem? = null,
    val isCutOperation: Boolean = false,
    // Name Conflict / Overwrite Dialog
    val showConflictDialog: Boolean = false,
    val conflictSourceFile: File? = null,
    val conflictTargetName: String? = null,
    // In-App Viewers & Media State
    val showMediaPlayerDialog: Boolean = false,
    val mediaFileToPlay: FileItem? = null,
    val showImageViewerDialog: Boolean = false,
    val imageFileToView: FileItem? = null,
    val showTextViewerDialog: Boolean = false,
    val textFileToRead: FileItem? = null,
    val textContent: String? = null,
    // Storage Sources State
    val storageSources: List<StorageSource> = emptyList(),
    val activeSource: StorageSource? = null,
    val showSourceDialog: Boolean = false,
    // Update Checker State
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Int = 0,
    val updateInfo: UpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val downloadedApkFile: File? = null,
    val googleDriveVersion: String? = null,
    val hasUpdateAvailable: Boolean = false
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val appUpdateManager = AppUpdateManager(application)

    // Map of Parent Directory Path -> Last Opened Child Item Path (for TV Focus Memory)
    private val subfolderHistoryMap = mutableMapOf<String, String>()

    private val _uiState = MutableStateFlow(FileManagerUiState())
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    init {
        checkPermissionAndLoad()
    }

    fun checkPermissionAndLoad() {
        val hasPermission = checkStoragePermission()
        _uiState.value = _uiState.value.copy(hasStoragePermission = hasPermission)
        if (hasPermission) {
            detectStorageSources()
            loadDirectory(_uiState.value.currentDirectory)
            fetchGoogleDriveVersion()
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun fetchGoogleDriveVersion() {
        viewModelScope.launch {
            val result = appUpdateManager.checkForUpdates()
            result.onSuccess { info ->
                _uiState.value = _uiState.value.copy(
                    googleDriveVersion = info.versionName,
                    hasUpdateAvailable = info.isUpdateAvailable,
                    updateInfo = if (info.isUpdateAvailable) info else null
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    googleDriveVersion = null,
                    hasUpdateAvailable = false
                )
            }
        }
    }

    fun detectStorageSources() {
        viewModelScope.launch(Dispatchers.IO) {
            val sources = mutableListOf<StorageSource>()

            // 1. Primary Internal Storage
            val internalFile = Environment.getExternalStorageDirectory()
            if (internalFile.exists()) {
                sources.add(
                    StorageSource(
                        label = "Internal Storage",
                        rootFile = internalFile,
                        isPrimary = true,
                        totalSpaceFormatted = formatBytes(internalFile.totalSpace),
                        freeSpaceFormatted = formatBytes(internalFile.freeSpace)
                    )
                )
            }

            // 2. External Storage Volumes (SD Card, USB Flash Drive)
            val externalDirs = ContextCompat.getExternalFilesDirs(getApplication(), null)
            for (dir in externalDirs) {
                if (dir != null) {
                    val path = dir.absolutePath
                    val androidIndex = path.indexOf("/Android/data/")
                    if (androidIndex > 0) {
                        val rootPath = path.substring(0, androidIndex)
                        val rootFile = File(rootPath)
                        if (rootFile.exists() && rootFile.path != internalFile.path) {
                            val volumeLabel = if (rootFile.name.length > 4) "USB Drive (${rootFile.name})" else "SD Card (${rootFile.name})"
                            sources.add(
                                StorageSource(
                                    label = volumeLabel,
                                    rootFile = rootFile,
                                    isPrimary = false,
                                    totalSpaceFormatted = formatBytes(rootFile.totalSpace),
                                    freeSpaceFormatted = formatBytes(rootFile.freeSpace)
                                )
                            )
                        }
                    }
                }
            }

            val active = sources.find { _uiState.value.currentDirectory.absolutePath.startsWith(it.rootFile.absolutePath) } ?: sources.firstOrNull()
            _uiState.value = _uiState.value.copy(
                storageSources = sources,
                activeSource = active
            )
        }
    }

    fun showSourceDialog() {
        detectStorageSources()
        _uiState.value = _uiState.value.copy(showSourceDialog = true)
    }

    fun dismissSourceDialog() {
        _uiState.value = _uiState.value.copy(showSourceDialog = false)
    }

    fun selectStorageSource(source: StorageSource) {
        _uiState.value = _uiState.value.copy(
            showSourceDialog = false,
            activeSource = source
        )
        navigateTo(source.rootFile)
    }

    fun navigateTo(directory: File) {
        if (directory.exists() && directory.isDirectory && directory.canRead()) {
            val currentPath = _uiState.value.currentDirectory.absolutePath
            // Store focus memory: parent directory remembers child directory
            subfolderHistoryMap[currentPath] = directory.absolutePath
            loadDirectory(directory)
        } else {
            _uiState.value = _uiState.value.copy(
                statusMessage = "Cannot access directory: ${directory.name}"
            )
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentDirectory
        val parent = current.parentFile
        val activeRoot = _uiState.value.activeSource?.rootFile ?: Environment.getExternalStorageDirectory()

        if (parent != null && current.path != activeRoot.path) {
            // Store focus memory: parent directory remembers child directory we are returning from
            subfolderHistoryMap[parent.absolutePath] = current.absolutePath
            loadDirectory(parent)
        } else {
            _uiState.value = _uiState.value.copy(
                statusMessage = "Already at root of ${uiState.value.activeSource?.label ?: "Storage"}"
            )
        }
    }

    fun loadDirectory(directory: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
            val items = withContext(Dispatchers.IO) {
                try {
                    val files = directory.listFiles() ?: emptyArray()
                    val mapped = files.map { FileItem(it) }
                    sortItems(mapped, _uiState.value.currentSortOption)
                } catch (_: Exception) {
                    emptyList()
                }
            }

            // TV Focus Memory: restore selection to last visited child item if returning to this folder!
            val lastVisitedChildPath = subfolderHistoryMap[directory.absolutePath]
            val targetSelected = items.find { it.path == lastVisitedChildPath } ?: items.firstOrNull()

            _uiState.value = _uiState.value.copy(
                currentDirectory = directory,
                fileList = items,
                selectedFile = targetSelected,
                isLoading = false
            )
        }
    }

    fun applySortOption(sortOption: SortOption) {
        val sorted = sortItems(_uiState.value.fileList, sortOption)
        _uiState.value = _uiState.value.copy(
            currentSortOption = sortOption,
            fileList = sorted,
            showSortDialog = false,
            statusMessage = "Sorted by ${sortOption.label}"
        )
    }

    private fun sortItems(items: List<FileItem>, sortOption: SortOption): List<FileItem> {
        val folders = items.filter { it.isDirectory }
        val files = items.filter { !it.isDirectory }

        val sortedFolders = when (sortOption) {
            SortOption.NAME_ASC -> folders.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> folders.sortedByDescending { it.name.lowercase() }
            SortOption.DATE_DESC -> folders.sortedByDescending { it.lastModified }
            SortOption.DATE_ASC -> folders.sortedBy { it.lastModified }
            SortOption.SIZE_DESC -> folders.sortedByDescending { it.itemCount }
            SortOption.SIZE_ASC -> folders.sortedBy { it.itemCount }
        }

        val sortedFiles = when (sortOption) {
            SortOption.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
            SortOption.DATE_DESC -> files.sortedByDescending { it.lastModified }
            SortOption.DATE_ASC -> files.sortedBy { it.lastModified }
            SortOption.SIZE_DESC -> files.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> files.sortedBy { it.sizeBytes }
        }

        return sortedFolders + sortedFiles
    }

    // --- IN-APP FILE OPENING & MEDIA PREVIEWS ---

    fun openFile(fileItem: FileItem, context: Context) {
        if (fileItem.isDirectory) {
            navigateTo(fileItem.file)
            return
        }

        when (fileItem.fileCategory) {
            FileCategory.VIDEO, FileCategory.AUDIO -> {
                _uiState.value = _uiState.value.copy(
                    showActionDialog = false,
                    mediaFileToPlay = fileItem,
                    showMediaPlayerDialog = true
                )
            }
            FileCategory.IMAGE -> {
                _uiState.value = _uiState.value.copy(
                    showActionDialog = false,
                    imageFileToView = fileItem,
                    showImageViewerDialog = true
                )
            }
            FileCategory.DOCUMENT -> {
                val ext = fileItem.file.extension.lowercase()
                if (ext in listOf("txt", "json", "xml", "log", "properties", "md")) {
                    loadAndShowTextFile(fileItem)
                } else {
                    openExternalApp(fileItem, context)
                }
            }
            else -> {
                openExternalApp(fileItem, context)
            }
        }
    }

    private fun loadAndShowTextFile(fileItem: FileItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, showActionDialog = false)
            val content = withContext(Dispatchers.IO) {
                try {
                    if (fileItem.sizeBytes < 500 * 1024) {
                        fileItem.file.readText()
                    } else {
                        fileItem.file.bufferedReader().useLines { lines ->
                            lines.take(1000).joinToString("\n")
                        } + "\n\n--- [File truncated for performance] ---"
                    }
                } catch (e: Exception) {
                    "Error reading text file: ${e.localizedMessage}"
                }
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                textFileToRead = fileItem,
                textContent = content,
                showTextViewerDialog = true
            )
        }
    }

    fun dismissMediaPlayerDialog() {
        _uiState.value = _uiState.value.copy(showMediaPlayerDialog = false, mediaFileToPlay = null)
    }

    fun dismissImageViewerDialog() {
        _uiState.value = _uiState.value.copy(showImageViewerDialog = false, imageFileToView = null)
    }

    fun dismissTextViewerDialog() {
        _uiState.value = _uiState.value.copy(showTextViewerDialog = false, textFileToRead = null, textContent = null)
    }

    fun openExternalApp(fileItem: FileItem, context: Context) {
        try {
            _uiState.value = _uiState.value.copy(showActionDialog = false)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileItem.file
            )

            val mimeType = getMimeType(fileItem)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Open '${fileItem.name}' with...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "No application found to open '${fileItem.name}'"
            )
        }
    }

    private fun getMimeType(fileItem: FileItem): String {
        val ext = fileItem.file.extension.lowercase()
        return when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "txt", "log", "json", "xml" -> "text/plain"
            "mp4", "mkv", "avi", "webm" -> "video/*"
            "mp3", "wav", "aac", "ogg" -> "audio/*"
            "jpg", "jpeg", "png", "webp", "gif" -> "image/*"
            else -> "*/*"
        }
    }

    // --- FILE & FOLDER OPERATIONS ---

    fun showCreateFolderDialog() {
        _uiState.value = _uiState.value.copy(showCreateFolderDialog = true)
    }

    fun dismissCreateFolderDialog() {
        _uiState.value = _uiState.value.copy(showCreateFolderDialog = false)
    }

    fun createNewFolder(folderName: String) {
        if (folderName.isBlank()) return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val newDir = File(_uiState.value.currentDirectory, folderName.trim())
                    if (!newDir.exists()) newDir.mkdir() else false
                } catch (_: Exception) {
                    false
                }
            }

            _uiState.value = _uiState.value.copy(showCreateFolderDialog = false)

            if (success) {
                _uiState.value = _uiState.value.copy(statusMessage = "Folder '$folderName' created!")
                loadDirectory(_uiState.value.currentDirectory)
            } else {
                _uiState.value = _uiState.value.copy(statusMessage = "Failed to create folder '$folderName'")
            }
        }
    }

    fun showCreateFileDialog() {
        _uiState.value = _uiState.value.copy(showCreateFileDialog = true)
    }

    fun dismissCreateFileDialog() {
        _uiState.value = _uiState.value.copy(showCreateFileDialog = false)
    }

    fun createNewFile(fileName: String) {
        if (fileName.isBlank()) return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val newFile = File(_uiState.value.currentDirectory, fileName.trim())
                    if (!newFile.exists()) newFile.createNewFile() else false
                } catch (_: Exception) {
                    false
                }
            }

            _uiState.value = _uiState.value.copy(showCreateFileDialog = false)

            if (success) {
                _uiState.value = _uiState.value.copy(statusMessage = "File '$fileName' created!")
                loadDirectory(_uiState.value.currentDirectory)
            } else {
                _uiState.value = _uiState.value.copy(statusMessage = "Failed to create file '$fileName'")
            }
        }
    }

    fun showRenameDialog(fileItem: FileItem) {
        _uiState.value = _uiState.value.copy(
            showActionDialog = false,
            fileToRename = fileItem,
            showRenameDialog = true
        )
    }

    fun dismissRenameDialog() {
        _uiState.value = _uiState.value.copy(
            showRenameDialog = false,
            fileToRename = null
        )
    }

    fun renameItem(fileItem: FileItem, newName: String) {
        if (newName.isBlank() || newName == fileItem.name) return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val target = File(fileItem.file.parentFile, newName.trim())
                    if (!target.exists()) fileItem.file.renameTo(target) else false
                } catch (_: Exception) {
                    false
                }
            }

            _uiState.value = _uiState.value.copy(showRenameDialog = false, fileToRename = null)

            if (success) {
                _uiState.value = _uiState.value.copy(statusMessage = "Renamed to '$newName'")
                loadDirectory(_uiState.value.currentDirectory)
            } else {
                _uiState.value = _uiState.value.copy(statusMessage = "Failed to rename item")
            }
        }
    }

    fun copyToClipboard(fileItem: FileItem, isCut: Boolean) {
        _uiState.value = _uiState.value.copy(
            showActionDialog = false,
            clipboardItem = fileItem,
            isCutOperation = isCut,
            statusMessage = if (isCut) "'${fileItem.name}' cut to clipboard" else "'${fileItem.name}' copied to clipboard"
        )
    }

    fun checkPasteConflict() {
        val clipboard = _uiState.value.clipboardItem ?: return
        val targetDir = _uiState.value.currentDirectory
        val dest = File(targetDir, clipboard.name)

        // If source file and target file are identical in location and name
        if (clipboard.file.absolutePath == dest.absolutePath) {
            _uiState.value = _uiState.value.copy(
                clipboardItem = null,
                isCutOperation = false,
                statusMessage = "Cannot paste onto itself"
            )
            return
        }

        if (dest.exists()) {
            _uiState.value = _uiState.value.copy(
                showConflictDialog = true,
                conflictSourceFile = clipboard.file,
                conflictTargetName = clipboard.name
            )
        } else {
            pasteClipboardItem(overwrite = false, keepBoth = false)
        }
    }

    fun confirmOverwriteConflict() {
        _uiState.value = _uiState.value.copy(showConflictDialog = false)
        pasteClipboardItem(overwrite = true, keepBoth = false)
    }

    fun confirmKeepBothConflict() {
        _uiState.value = _uiState.value.copy(showConflictDialog = false)
        pasteClipboardItem(overwrite = false, keepBoth = true)
    }

    fun dismissConflictDialog() {
        _uiState.value = _uiState.value.copy(showConflictDialog = false)
    }

    fun pasteClipboardItem(overwrite: Boolean = false, keepBoth: Boolean = false) {
        val clipboard = _uiState.value.clipboardItem ?: return
        val isCut = _uiState.value.isCutOperation
        val targetDir = _uiState.value.currentDirectory

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val success = withContext(Dispatchers.IO) {
                try {
                    var targetName = clipboard.name
                    if (keepBoth) {
                        val ext = clipboard.file.extension
                        val baseName = clipboard.file.nameWithoutExtension
                        var count = 1
                        var testFile = File(targetDir, if (clipboard.isDirectory) "$targetName ($count)" else "$baseName ($count).$ext")
                        while (testFile.exists()) {
                            count++
                            testFile = File(targetDir, if (clipboard.isDirectory) "$targetName ($count)" else "$baseName ($count).$ext")
                        }
                        targetName = testFile.name
                    }

                    val dest = File(targetDir, targetName)

                    // Safely delete destination if overwrite is selected AND destination is NOT the source itself
                    if (overwrite && dest.exists() && dest.absolutePath != clipboard.file.absolutePath) {
                        dest.deleteRecursively()
                    }

                    if (isCut) {
                        clipboard.file.renameTo(dest)
                    } else {
                        clipboard.file.copyRecursively(dest, overwrite = true)
                    }
                } catch (_: Exception) {
                    false
                }
            }

            // Always clear clipboard so Paste button disappears after pasting!
            _uiState.value = _uiState.value.copy(
                clipboardItem = null,
                isCutOperation = false,
                isLoading = false
            )

            if (success) {
                _uiState.value = _uiState.value.copy(statusMessage = "Pasted '${clipboard.name}'")
                loadDirectory(targetDir)
            } else {
                _uiState.value = _uiState.value.copy(statusMessage = "Failed to paste item")
            }
        }
    }

    fun showSortDialog() {
        _uiState.value = _uiState.value.copy(showSortDialog = true)
    }

    fun dismissSortDialog() {
        _uiState.value = _uiState.value.copy(showSortDialog = false)
    }

    fun selectFile(fileItem: FileItem) {
        _uiState.value = _uiState.value.copy(selectedFile = fileItem)
    }

    fun showFileActionDialog(fileItem: FileItem) {
        _uiState.value = _uiState.value.copy(
            selectedFile = fileItem,
            fileForAction = fileItem,
            showActionDialog = true
        )
    }

    fun dismissFileActionDialog() {
        _uiState.value = _uiState.value.copy(
            showActionDialog = false,
            fileForAction = null
        )
    }

    fun requestDeleteFile(fileItem: FileItem) {
        _uiState.value = _uiState.value.copy(
            showActionDialog = false,
            filePendingDeletion = fileItem,
            showDeleteDialog = true
        )
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = false,
            filePendingDeletion = null
        )
    }

    fun confirmDeleteFile() {
        val fileToDelete = _uiState.value.filePendingDeletion ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                showDeleteDialog = false
            )

            val success = withContext(Dispatchers.IO) {
                deleteRecursively(fileToDelete.file)
            }

            if (success) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Successfully deleted ${fileToDelete.name}",
                    filePendingDeletion = null
                )
                loadDirectory(_uiState.value.currentDirectory)
            } else {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Failed to delete ${fileToDelete.name}",
                    isLoading = false,
                    filePendingDeletion = null
                )
            }
        }
    }

    fun showInfoDialog(fileItem: FileItem) {
        _uiState.value = _uiState.value.copy(
            showActionDialog = false,
            selectedFile = fileItem,
            showInfoDialog = true
        )
    }

    fun dismissInfoDialog() {
        _uiState.value = _uiState.value.copy(showInfoDialog = false)
    }

    fun checkForUpdates(customUrl: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingUpdate = true, statusMessage = "Checking Google Drive for app updates...")
            val result = if (customUrl != null) {
                appUpdateManager.checkForUpdates(customUrl)
            } else {
                appUpdateManager.checkForUpdates()
            }

            result.onSuccess { info ->
                if (info.isUpdateAvailable) {
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        updateInfo = info,
                        showUpdateDialog = true,
                        googleDriveVersion = info.versionName,
                        hasUpdateAvailable = true,
                        statusMessage = "New update found: ${info.versionName}!"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        updateInfo = info,
                        showUpdateDialog = false,
                        googleDriveVersion = info.versionName,
                        hasUpdateAvailable = false,
                        statusMessage = "App is up to date (Installed: v${info.currentVersionName}, Drive: ${info.versionName})"
                    )
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isCheckingUpdate = false,
                    statusMessage = "Update check error: ${e.localizedMessage}"
                )
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val updateInfo = _uiState.value.updateInfo ?: return

        if (!appUpdateManager.canInstallPackages()) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "Permission required: Enable 'Install unknown apps' for File Handler."
            )
            appUpdateManager.openInstallPermissionSettings()
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloadingUpdate = true,
                downloadProgress = 0
            )

            val downloadResult = appUpdateManager.downloadApk(updateInfo.apkUrl) { progress ->
                _uiState.value = _uiState.value.copy(downloadProgress = progress)
            }

            downloadResult.onSuccess { apkFile ->
                _uiState.value = _uiState.value.copy(
                    isDownloadingUpdate = false,
                    downloadedApkFile = apkFile,
                    statusMessage = "Download complete. Opening Package Installer..."
                )
                appUpdateManager.installApk(apkFile)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isDownloadingUpdate = false,
                    statusMessage = "Download failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.value = _uiState.value.copy(
            showUpdateDialog = false,
            isDownloadingUpdate = false
        )
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun deleteRecursively(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                val children = file.listFiles()
                if (children != null) {
                    for (child in children) {
                        deleteRecursively(child)
                    }
                }
            }
            file.delete()
        } catch (_: Exception) {
            false
        }
    }
}
