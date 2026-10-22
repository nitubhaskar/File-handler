package com.example.filehandler

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
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
    val downloadedApkFile: File? = null
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val appUpdateManager = AppUpdateManager(application)

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
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
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
                    files.map { FileItem(it) }
                        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val defaultSelected = items.firstOrNull()
            _uiState.value = _uiState.value.copy(
                currentDirectory = directory,
                fileList = items,
                selectedFile = defaultSelected,
                isLoading = false
            )
        }
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
                        statusMessage = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        updateInfo = info,
                        showUpdateDialog = false,
                        statusMessage = "App is already up to date!"
                    )
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isCheckingUpdate = false,
                    statusMessage = "Update check failed: ${e.localizedMessage}"
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
