package com.example.filehandler

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

@Composable
fun TvFileManagerApp(viewModel: FileManagerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showOpeningScreen by remember { mutableStateOf(true) }
    var lastBackPressTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        delay(1000)
        showOpeningScreen = false
    }

    // Smart Back Handler for Android TV Remote Control
    BackHandler {
        when {
            uiState.showMediaPlayerDialog -> viewModel.dismissMediaPlayerDialog()
            uiState.showImageViewerDialog -> viewModel.dismissImageViewerDialog()
            uiState.showTextViewerDialog -> viewModel.dismissTextViewerDialog()
            uiState.showSourceDialog -> viewModel.dismissSourceDialog()
            uiState.showConflictDialog -> viewModel.dismissConflictDialog()
            uiState.showCreateFolderDialog -> viewModel.dismissCreateFolderDialog()
            uiState.showCreateFileDialog -> viewModel.dismissCreateFileDialog()
            uiState.showRenameDialog -> viewModel.dismissRenameDialog()
            uiState.showSortDialog -> viewModel.dismissSortDialog()
            uiState.showActionDialog -> viewModel.dismissFileActionDialog()
            uiState.showDeleteDialog -> viewModel.dismissDeleteDialog()
            uiState.showInfoDialog -> viewModel.dismissInfoDialog()
            uiState.showUpdateDialog -> viewModel.dismissUpdateDialog()
            uiState.currentDirectory.path != (uiState.activeSource?.rootFile?.path ?: Environment.getExternalStorageDirectory().path) -> {
                viewModel.navigateUp()
            }
            else -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressTime = currentTime
                    Toast.makeText(context, "Press Back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = Color(0xFF121212)
            )
        ) {
            if (showOpeningScreen) {
                AppOpeningSplashScreen()
            } else if (!uiState.hasStoragePermission) {
                PermissionRequiredScreen(
                    onGrantClicked = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    },
                    onRefreshClicked = {
                        viewModel.checkPermissionAndLoad()
                    }
                )
            } else {
                MainContent(
                    uiState = uiState,
                    onNavigateTo = { viewModel.navigateTo(it) },
                    onNavigateUp = { viewModel.navigateUp() },
                    onOpenFile = { viewModel.openFile(it, context) },
                    onOpenSourceDialog = { viewModel.showSourceDialog() },
                    onOpenCreateFolderDialog = { viewModel.showCreateFolderDialog() },
                    onOpenCreateFileDialog = { viewModel.showCreateFileDialog() },
                    onOpenSortDialog = { viewModel.showSortDialog() },
                    onPasteClipboardItem = { viewModel.checkPasteConflict() },
                    onCheckUpdates = { viewModel.checkForUpdates() },
                    onSelectFile = { viewModel.selectFile(it) },
                    onShowFileActionDialog = { viewModel.showFileActionDialog(it) },
                    onRenameItem = { viewModel.showRenameDialog(it) },
                    onCopyItem = { viewModel.copyToClipboard(it, isCut = false) },
                    onCutItem = { viewModel.copyToClipboard(it, isCut = true) },
                    onRequestDelete = { viewModel.requestDeleteFile(it) },
                    onRequestInfo = { viewModel.showInfoDialog(it) }
                )
            }

            if (!showOpeningScreen) {
                if (uiState.showMediaPlayerDialog && uiState.mediaFileToPlay != null) {
                    TvMediaPlayerDialog(
                        fileItem = uiState.mediaFileToPlay!!,
                        context = context,
                        onDismiss = { viewModel.dismissMediaPlayerDialog() }
                    )
                }

                if (uiState.showImageViewerDialog && uiState.imageFileToView != null) {
                    TvImageViewerDialog(
                        fileItem = uiState.imageFileToView!!,
                        onDismiss = { viewModel.dismissImageViewerDialog() }
                    )
                }

                if (uiState.showTextViewerDialog && uiState.textFileToRead != null && uiState.textContent != null) {
                    TvTextViewerDialog(
                        fileItem = uiState.textFileToRead!!,
                        content = uiState.textContent!!,
                        onDismiss = { viewModel.dismissTextViewerDialog() }
                    )
                }

                if (uiState.showSourceDialog) {
                    StorageSourcePickerDialog(
                        sources = uiState.storageSources,
                        activeSource = uiState.activeSource,
                        onSourceSelected = { viewModel.selectStorageSource(it) },
                        onDismiss = { viewModel.dismissSourceDialog() }
                    )
                }

                if (uiState.showConflictDialog && uiState.conflictTargetName != null) {
                    NameConflictDialog(
                        targetName = uiState.conflictTargetName!!,
                        onOverwrite = { viewModel.confirmOverwriteConflict() },
                        onKeepBoth = { viewModel.confirmKeepBothConflict() },
                        onDismiss = { viewModel.dismissConflictDialog() }
                    )
                }

                if (uiState.showCreateFolderDialog) {
                    TextInputDialog(
                        title = "Create New Folder",
                        initialValue = "",
                        confirmLabel = "Create Folder",
                        onConfirm = { viewModel.createNewFolder(it) },
                        onDismiss = { viewModel.dismissCreateFolderDialog() }
                    )
                }

                if (uiState.showCreateFileDialog) {
                    TextInputDialog(
                        title = "Create New File",
                        initialValue = "new_file.txt",
                        confirmLabel = "Create File",
                        onConfirm = { viewModel.createNewFile(it) },
                        onDismiss = { viewModel.dismissCreateFileDialog() }
                    )
                }

                if (uiState.showRenameDialog && uiState.fileToRename != null) {
                    TextInputDialog(
                        title = "Rename '${uiState.fileToRename!!.name}'",
                        initialValue = uiState.fileToRename!!.name,
                        confirmLabel = "Rename",
                        onConfirm = { viewModel.renameItem(uiState.fileToRename!!, it) },
                        onDismiss = { viewModel.dismissRenameDialog() }
                    )
                }

                if (uiState.showSortDialog) {
                    SortOptionPickerDialog(
                        currentSortOption = uiState.currentSortOption,
                        onSortSelected = { viewModel.applySortOption(it) },
                        onDismiss = { viewModel.dismissSortDialog() }
                    )
                }

                if (uiState.showActionDialog && uiState.fileForAction != null) {
                    FileActionOptionsDialog(
                        fileItem = uiState.fileForAction!!,
                        onOpenFile = { viewModel.openFile(it, context) },
                        onRenameClicked = { viewModel.showRenameDialog(uiState.fileForAction!!) },
                        onCopyClicked = { viewModel.copyToClipboard(uiState.fileForAction!!, isCut = false) },
                        onCutClicked = { viewModel.copyToClipboard(uiState.fileForAction!!, isCut = true) },
                        onInfoClicked = { viewModel.showInfoDialog(uiState.fileForAction!!) },
                        onDeleteClicked = { viewModel.requestDeleteFile(uiState.fileForAction!!) },
                        onDismiss = { viewModel.dismissFileActionDialog() }
                    )
                }

                if (uiState.showDeleteDialog && uiState.filePendingDeletion != null) {
                    DeleteConfirmationDialog(
                        fileItem = uiState.filePendingDeletion!!,
                        onConfirm = { viewModel.confirmDeleteFile() },
                        onDismiss = { viewModel.dismissDeleteDialog() }
                    )
                }

                if (uiState.showInfoDialog && uiState.selectedFile != null) {
                    FileInfoDialog(
                        fileItem = uiState.selectedFile!!,
                        onDismiss = { viewModel.dismissInfoDialog() }
                    )
                }

                if (uiState.showUpdateDialog && uiState.updateInfo != null) {
                    UpdateAppDialog(
                        updateInfo = uiState.updateInfo!!,
                        isDownloading = uiState.isDownloadingUpdate,
                        progress = uiState.downloadProgress,
                        onInstallClicked = { viewModel.downloadAndInstallUpdate() },
                        onDismiss = { viewModel.dismissUpdateDialog() }
                    )
                }
            }
        }
    }
}

@Composable
fun AppOpeningSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F172A))
                    .border(3.dp, Color(0xFF00E5FF), RoundedCornerShape(20.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10B981)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF38BDF8)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "FILEHANDLER",
                color = Color(0xFF00E5FF),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "DUAL PANE FILE MANAGER",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun PermissionRequiredScreen(
    onGrantClicked: () -> Unit,
    onRefreshClicked: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.width(600.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF00E5FF)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Storage Access Permission Needed",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "On Android 11+, the TV File Manager needs All Files Access permission to browse file details and perform file deletion.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onGrantClicked,
                    modifier = Modifier.focusRequester(focusRequester),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF282828),
                        contentColor = Color(0xFF00E5FF),
                        focusedContainerColor = Color(0xFF00E5FF),
                        focusedContentColor = Color.Black
                    )
                ) {
                    Text(text = "Grant Storage Permission", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onRefreshClicked,
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF282828),
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFF00E5FF),
                        focusedContentColor = Color.Black
                    )
                ) {
                    Text(text = "Refresh Permission Status")
                }
            }
        }
    }
}

@Composable
fun MainContent(
    uiState: FileManagerUiState,
    onNavigateTo: (java.io.File) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onOpenSourceDialog: () -> Unit,
    onOpenCreateFolderDialog: () -> Unit,
    onOpenCreateFileDialog: () -> Unit,
    onOpenSortDialog: () -> Unit,
    onPasteClipboardItem: () -> Unit,
    onCheckUpdates: () -> Unit,
    onSelectFile: (FileItem) -> Unit,
    onShowFileActionDialog: (FileItem) -> Unit,
    onRenameItem: (FileItem) -> Unit,
    onCopyItem: (FileItem) -> Unit,
    onCutItem: (FileItem) -> Unit,
    onRequestDelete: (FileItem) -> Unit,
    onRequestInfo: (FileItem) -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.currentDirectory.path, uiState.fileList.size) {
        if (uiState.fileList.isNotEmpty()) {
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Header Bar
        HeaderSection(
            currentPath = uiState.currentDirectory.absolutePath,
            activeSourceLabel = uiState.activeSource?.label ?: "Storage Source",
            googleDriveVersion = uiState.googleDriveVersion,
            hasUpdateAvailable = uiState.hasUpdateAvailable,
            isCheckingUpdate = uiState.isCheckingUpdate,
            clipboardItem = uiState.clipboardItem,
            onNavigateUp = onNavigateUp,
            onOpenSourceDialog = onOpenSourceDialog,
            onOpenCreateFolderDialog = onOpenCreateFolderDialog,
            onOpenCreateFileDialog = onOpenCreateFileDialog,
            onOpenSortDialog = onOpenSortDialog,
            onPasteClipboardItem = onPasteClipboardItem,
            onCheckUpdates = onCheckUpdates
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Two-pane TV layout
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Left Pane: File List
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(12.dp)
            ) {
                if (uiState.fileList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.isLoading) "Loading files..." else "Folder is empty",
                            color = Color.Gray,
                            fontSize = 18.sp
                        )
                    }
                } else {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val targetIndex = remember(uiState.currentDirectory.path) {
                        uiState.fileList.indexOfFirst { it.path == uiState.selectedFile?.path }
                    }

                    LaunchedEffect(uiState.currentDirectory.path) {
                        if (targetIndex >= 0) {
                            try {
                                listState.scrollToItem(targetIndex)
                            } catch (_: Exception) {}
                        }
                    }

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.fileList,
                            key = { _, item -> item.path },
                            contentType = { _, _ -> "file_item" }
                        ) { index, item ->
                            val isTargetFocus = (item.path == uiState.selectedFile?.path)
                            val itemFocusRequester = remember { FocusRequester() }

                            LaunchedEffect(uiState.currentDirectory.path, isTargetFocus) {
                                if (isTargetFocus) {
                                    try {
                                        itemFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                }
                            }

                            FileListItem(
                                fileItem = item,
                                isSelected = uiState.selectedFile?.path == item.path,
                                modifier = if (isTargetFocus) Modifier.focusRequester(itemFocusRequester) else if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                                onFocused = { onSelectFile(item) },
                                onClick = {
                                    if (item.isDirectory) {
                                        onNavigateTo(item.file)
                                    } else {
                                        onOpenFile(item)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right Pane: Details & Quick Actions
            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(20.dp)
            ) {
                FileDetailsPanel(
                    selectedFile = uiState.selectedFile,
                    onOpenFile = onOpenFile,
                    onRenameItem = onRenameItem,
                    onCopyItem = onCopyItem,
                    onCutItem = onCutItem,
                    onRequestInfo = onRequestInfo,
                    onRequestDelete = onRequestDelete
                )
            }
        }
    }
}

@Composable
fun HeaderSection(
    currentPath: String,
    activeSourceLabel: String,
    googleDriveVersion: String?,
    hasUpdateAvailable: Boolean,
    isCheckingUpdate: Boolean,
    clipboardItem: FileItem?,
    onNavigateUp: () -> Unit,
    onOpenSourceDialog: () -> Unit,
    onOpenCreateFolderDialog: () -> Unit,
    onOpenCreateFileDialog: () -> Unit,
    onOpenSortDialog: () -> Unit,
    onPasteClipboardItem: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Button(
                onClick = onNavigateUp,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF282828),
                    contentColor = Color.White,
                    focusedContainerColor = Color(0xFF00E5FF),
                    focusedContentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Up",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Up", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "TV File Manager",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF00E5FF)
                )
                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // New Folder Button
            Button(
                onClick = onOpenCreateFolderDialog,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF282828),
                    contentColor = Color(0xFF81C784),
                    focusedContainerColor = Color(0xFF00E5FF),
                    focusedContentColor = Color.Black
                )
            ) {
                Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "+ Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // New File Button
            Button(
                onClick = onOpenCreateFileDialog,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF282828),
                    contentColor = Color(0xFF38BDF8),
                    focusedContainerColor = Color(0xFF00E5FF),
                    focusedContentColor = Color.Black
                )
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "+ File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Sort Button
            Button(
                onClick = onOpenSortDialog,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF282828),
                    contentColor = Color(0xFFBA68C8),
                    focusedContainerColor = Color(0xFF00E5FF),
                    focusedContentColor = Color.Black
                )
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Sort", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Paste Button (Visible when item in clipboard)
            if (clipboardItem != null) {
                Button(
                    onClick = onPasteClipboardItem,
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.Black,
                        focusedContainerColor = Color(0xFF00E5FF),
                        focusedContentColor = Color.Black
                    )
                ) {
                    Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Paste", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Storage Source Switcher Button
            Button(
                onClick = onOpenSourceDialog,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF282828),
                    contentColor = Color(0xFFFFD54F),
                    focusedContainerColor = Color(0xFF00E5FF),
                    focusedContentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.Default.SdCard,
                    contentDescription = "Storage Source",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = activeSourceLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            // Smart Update Button
            Button(
                onClick = onCheckUpdates,
                colors = ButtonDefaults.colors(
                    containerColor = if (hasUpdateAvailable) Color(0xFF1E293B) else Color(0xFF282828),
                    contentColor = if (hasUpdateAvailable) Color(0xFFFFD54F) else Color(0xFF00E5FF),
                    focusedContainerColor = Color(0xFF00E5FF),
                    focusedContentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = if (hasUpdateAvailable) Icons.Default.CloudDownload else Icons.Default.SystemUpdate,
                    contentDescription = "Check Updates",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when {
                        isCheckingUpdate -> "Checking..."
                        hasUpdateAvailable -> "Update: ${googleDriveVersion ?: "Available"}"
                        else -> "Check Updates"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TvMediaPlayerDialog(
    fileItem: FileItem,
    context: Context,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    val exoPlayer = remember(fileItem.path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(fileItem.file)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Media Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111111))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (fileItem.fileCategory == FileCategory.VIDEO) Icons.Default.Movie else Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = fileItem.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(focusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282828),
                            contentColor = Color.White,
                            focusedContainerColor = Color(0xFF00E5FF),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Text(text = "Close Player", fontWeight = FontWeight.Bold)
                    }
                }

                // Player Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (fileItem.fileCategory == FileCategory.AUDIO) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(100.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Playing Audio Track",
                                color = Color.LightGray,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun TvImageViewerDialog(
    fileItem: FileItem,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    val bitmap = remember(fileItem.path) {
        try {
            BitmapFactory.decodeFile(fileItem.file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111111))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fileItem.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(focusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282828),
                            contentColor = Color.White,
                            focusedContainerColor = Color(0xFF00E5FF),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Text(text = "Close", fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = fileItem.name,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(text = "Failed to load image preview", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun TvTextViewerDialog(
    fileItem: FileItem,
    content: String,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fileItem.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00E5FF)
                        )

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.focusRequester(focusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color.White,
                                focusedContainerColor = Color(0xFF00E5FF),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(text = "Close Viewer", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF121212))
                            .padding(16.dp)
                    ) {
                        LazyColumn {
                            item {
                                Text(
                                    text = content,
                                    color = Color.LightGray,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NameConflictDialog(
    targetName: String,
    onOverwrite: () -> Unit,
    onKeepBoth: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(500.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222222))
                    .border(2.dp, Color(0xFFFFD54F), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Item Already Exists",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "An item named '$targetName' already exists in this directory.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOverwrite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color(0xFFFF5252),
                                focusedContainerColor = Color(0xFFFF3D00),
                                focusedContentColor = Color.White
                            )
                        ) {
                            Text(text = "Overwrite Existing Item", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onKeepBoth,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color(0xFF00E5FF),
                                focusedContainerColor = Color(0xFF00E5FF),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(text = "Keep Both (Auto-Rename)", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color.LightGray,
                                focusedContainerColor = Color(0xFF00E5FF),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(text = "Cancel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(480.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222222))
                    .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E1E))
                            .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        BasicTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            cursorBrush = SolidColor(Color(0xFF00E5FF))
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color.White,
                                focusedContainerColor = Color(0xFF00E5FF),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(text = "Cancel", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onConfirm(textValue) },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color(0xFF00E5FF),
                                focusedContainerColor = Color(0xFF00E5FF),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(text = confirmLabel, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SortOptionPickerDialog(
    currentSortOption: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(460.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222222))
                    .border(2.dp, Color(0xFFBA68C8), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            tint = Color(0xFFBA68C8),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sort File List",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(SortOption.entries) { index, option ->
                            val isSelected = option == currentSortOption

                            Card(
                                onClick = { onSortSelected(option) },
                                modifier = if (index == 0) Modifier.fillMaxWidth().focusRequester(focusRequester) else Modifier.fillMaxWidth(),
                                colors = CardDefaults.colors(
                                    containerColor = if (isSelected) Color(0xFF333333) else Color(0xFF282828),
                                    focusedContainerColor = Color(0xFF00E5FF),
                                    focusedContentColor = Color.Black
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = option.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )

                                    if (isSelected) {
                                        Text(
                                            text = "ACTIVE",
                                            color = Color(0xFF00E5FF),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282828),
                            contentColor = Color.White,
                            focusedContainerColor = Color(0xFF00E5FF),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Text(text = "Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StorageSourcePickerDialog(
    sources: List<StorageSource>,
    activeSource: StorageSource?,
    onSourceSelected: (StorageSource) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(520.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222222))
                    .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SdCard,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Select Storage Source",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (sources.isEmpty()) {
                        Text(
                            text = "No storage sources detected.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(sources, key = { _, src -> src.rootFile.absolutePath }) { index, source ->
                                val isSelected = activeSource?.rootFile?.absolutePath == source.rootFile.absolutePath

                                Card(
                                    onClick = { onSourceSelected(source) },
                                    modifier = if (index == 0) Modifier.fillMaxWidth().focusRequester(focusRequester) else Modifier.fillMaxWidth(),
                                    colors = CardDefaults.colors(
                                        containerColor = if (isSelected) Color(0xFF333333) else Color(0xFF282828),
                                        focusedContainerColor = Color(0xFF00E5FF),
                                        focusedContentColor = Color.Black
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (source.isPrimary) Icons.Default.Storage else Icons.Default.Usb,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFFFFD54F) else Color.LightGray,
                                            modifier = Modifier.size(32.dp)
                                        )

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = source.label,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${source.freeSpaceFormatted} free of ${source.totalSpaceFormatted}",
                                                fontSize = 12.sp,
                                                color = Color.LightGray
                                            )
                                        }

                                        if (isSelected) {
                                            Text(
                                                text = "ACTIVE",
                                                color = Color(0xFF00E5FF),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282828),
                            contentColor = Color.White,
                            focusedContainerColor = Color(0xFF00E5FF),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Text(text = "Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FileListItem(
    fileItem: FileItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        label = "item_scale"
    )

    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = if (isFocused) Color(0xFF00E5FF) else if (isSelected) Color(0xFF333333) else Color(0xFF222222)
        ),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (focusState.isFocused) {
                    onFocused()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getCategoryIcon(fileItem.fileCategory),
                contentDescription = null,
                tint = if (isFocused) Color.Black else getCategoryColor(fileItem.fileCategory),
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = fileItem.name,
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${fileItem.formattedSize} • ${fileItem.formattedLastModified}",
                    color = if (isFocused) Color(0xFF222222) else Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun FileDetailsPanel(
    selectedFile: FileItem?,
    onOpenFile: (FileItem) -> Unit,
    onRenameItem: (FileItem) -> Unit,
    onCopyItem: (FileItem) -> Unit,
    onCutItem: (FileItem) -> Unit,
    onRequestInfo: (FileItem) -> Unit,
    onRequestDelete: (FileItem) -> Unit
) {
    if (selectedFile == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Select an item to view details",
                color = Color.Gray,
                fontSize = 16.sp
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2B2B2B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(selectedFile.fileCategory),
                        contentDescription = null,
                        tint = getCategoryColor(selectedFile.fileCategory),
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = selectedFile.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                DetailRow(label = "Type", value = if (selectedFile.isDirectory) "Directory Folder" else "File (${selectedFile.file.extension.uppercase()})")
                DetailRow(label = "Size", value = selectedFile.formattedSize)
                DetailRow(label = "Modified", value = selectedFile.formattedLastModified)
                DetailRow(label = "Permissions", value = "Read: ${selectedFile.isReadable} | Write: ${selectedFile.isWritable}")
            }

            // Quick Actions directly in Right Side Pane
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onOpenFile(selectedFile) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF282828),
                        contentColor = Color(0xFF00E5FF),
                        focusedContainerColor = Color(0xFF00E5FF),
                        focusedContentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = if (selectedFile.isDirectory) Icons.Default.Folder else if (selectedFile.fileCategory == FileCategory.VIDEO || selectedFile.fileCategory == FileCategory.AUDIO) Icons.Default.PlayArrow else Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedFile.isDirectory) "Open Folder" else if (selectedFile.fileCategory == FileCategory.VIDEO || selectedFile.fileCategory == FileCategory.AUDIO) "Play Media" else "Open File",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onRenameItem(selectedFile) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282828),
                            contentColor = Color(0xFFBA68C8),
                            focusedContainerColor = Color(0xFF00E5FF),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Icon(imageVector = Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Rename", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onCopyItem(selectedFile) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282828),
                            contentColor = Color(0xFF81C784),
                            focusedContainerColor = Color(0xFF00E5FF),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Copy", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onCutItem(selectedFile) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282828),
                            contentColor = Color(0xFFFFD54F),
                            focusedContainerColor = Color(0xFF00E5FF),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Cut", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onRequestInfo(selectedFile) },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282828),
                            contentColor = Color.White,
                            focusedContainerColor = Color(0xFF00E5FF),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Full Info", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onRequestDelete(selectedFile) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282828),
                            contentColor = Color(0xFFFF5252),
                            focusedContainerColor = Color(0xFFFF3D00),
                            focusedContentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Delete", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
        Text(
            text = value,
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FileActionOptionsDialog(
    fileItem: FileItem,
    onOpenFile: (FileItem) -> Unit,
    onRenameClicked: () -> Unit,
    onCopyClicked: () -> Unit,
    onCutClicked: () -> Unit,
    onInfoClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(480.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222222))
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = getCategoryIcon(fileItem.fileCategory),
                        contentDescription = null,
                        tint = getCategoryColor(fileItem.fileCategory),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = fileItem.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fileItem.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onOpenFile(fileItem) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color(0xFF00E5FF),
                                focusedContainerColor = Color(0xFF00E5FF),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = if (fileItem.isDirectory) Icons.Default.Folder else if (fileItem.fileCategory == FileCategory.VIDEO || fileItem.fileCategory == FileCategory.AUDIO) Icons.Default.PlayArrow else Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (fileItem.isDirectory) "Open Folder" else if (fileItem.fileCategory == FileCategory.VIDEO || fileItem.fileCategory == FileCategory.AUDIO) "Play Media" else "Open File",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onRenameClicked,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color(0xFFBA68C8),
                                focusedContainerColor = Color(0xFF00E5FF),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Icon(imageVector = Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Rename", fontWeight = FontWeight.Bold)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onCopyClicked,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0xFF282828),
                                    contentColor = Color(0xFF81C784),
                                    focusedContainerColor = Color(0xFF00E5FF),
                                    focusedContentColor = Color.Black
                                )
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Copy", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onCutClicked,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0xFF282828),
                                    contentColor = Color(0xFFFFD54F),
                                    focusedContainerColor = Color(0xFF00E5FF),
                                    focusedContentColor = Color.Black
                                )
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Cut", fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = onInfoClicked,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color.White,
                                focusedContainerColor = Color(0xFF00E5FF),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Full File Info", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDeleteClicked,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color(0xFFFF5252),
                                focusedContainerColor = Color(0xFFFF3D00),
                                focusedContentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Delete File", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color.LightGray,
                                focusedContainerColor = Color(0xFF00E5FF),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(text = "Cancel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateAppDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    progress: Int,
    onInstallClicked: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(520.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222222))
                    .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "App Update Available",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current: v${updateInfo.currentVersionName} (Code ${updateInfo.currentVersionCode})  ➔  New: ${updateInfo.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = updateInfo.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )

                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Downloading Update: $progress%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF444444))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = (progress / 100f).coerceIn(0f, 1f))
                                    .background(Color(0xFF00E5FF))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            enabled = !isDownloading,
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color.White,
                                focusedContainerColor = Color(0xFF00E5FF),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(text = "Later", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onInstallClicked,
                            modifier = Modifier
                                .weight(1.3f)
                                .focusRequester(focusRequester),
                            enabled = !isDownloading,
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color(0xFF00E5FF),
                                focusedContainerColor = Color(0xFF00E5FF),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(
                                text = if (isDownloading) "Downloading..." else "Download Update",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    fileItem: FileItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(480.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222222))
                    .border(2.dp, Color(0xFFFF3D00), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFFF3D00),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Confirm Deletion",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Are you sure you want to permanently delete '${fileItem.name}'?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    if (fileItem.isDirectory) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Warning: This will delete all contents inside this directory!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9100),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color.White,
                                focusedContainerColor = Color(0xFF00E5FF),
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Text(text = "Cancel", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF282828),
                                contentColor = Color(0xFFFF5252),
                                focusedContainerColor = Color(0xFFFF3D00),
                                focusedContentColor = Color.White
                            )
                        ) {
                            Text(text = "Delete", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileInfoDialog(
    fileItem: FileItem,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(520.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222222))
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "File Information",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    DetailRow(label = "Name", value = fileItem.name)
                    DetailRow(label = "Full Path", value = fileItem.path)
                    DetailRow(label = "Category", value = fileItem.fileCategory.name)
                    DetailRow(label = "Size", value = fileItem.formattedSize)
                    DetailRow(label = "Last Modified", value = fileItem.formattedLastModified)
                    DetailRow(label = "Is Directory", value = fileItem.isDirectory.toString())
                    DetailRow(label = "Readable", value = fileItem.isReadable.toString())
                    DetailRow(label = "Writable", value = fileItem.isWritable.toString())

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282828),
                            contentColor = Color.White,
                            focusedContainerColor = Color(0xFF00E5FF),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Text(text = "Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun getCategoryIcon(category: FileCategory): ImageVector {
    return when (category) {
        FileCategory.FOLDER -> Icons.Default.Folder
        FileCategory.IMAGE -> Icons.Default.Image
        FileCategory.VIDEO -> Icons.Default.Movie
        FileCategory.AUDIO -> Icons.Default.Audiotrack
        FileCategory.DOCUMENT -> Icons.AutoMirrored.Filled.InsertDriveFile
        FileCategory.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

fun getCategoryColor(category: FileCategory): Color {
    return when (category) {
        FileCategory.FOLDER -> Color(0xFFFFD54F)
        FileCategory.IMAGE -> Color(0xFF81C784)
        FileCategory.VIDEO -> Color(0xFFE57373)
        FileCategory.AUDIO -> Color(0xFF64B5F6)
        FileCategory.DOCUMENT -> Color(0xFFBA68C8)
        FileCategory.OTHER -> Color(0xFFB0BEC5)
    }
}
