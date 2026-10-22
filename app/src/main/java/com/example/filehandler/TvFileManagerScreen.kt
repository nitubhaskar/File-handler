package com.example.filehandler

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
        delay(1500)
        showOpeningScreen = false
    }

    // Smart Back Handler for Android TV Remote Control
    BackHandler {
        when {
            uiState.showActionDialog -> viewModel.dismissFileActionDialog()
            uiState.showDeleteDialog -> viewModel.dismissDeleteDialog()
            uiState.showInfoDialog -> viewModel.dismissInfoDialog()
            uiState.showUpdateDialog -> viewModel.dismissUpdateDialog()
            uiState.currentDirectory.path != Environment.getExternalStorageDirectory().path -> {
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
                    onRefresh = { viewModel.loadDirectory(uiState.currentDirectory) },
                    onCheckUpdates = { viewModel.checkForUpdates() },
                    onSelectFile = { viewModel.selectFile(it) },
                    onShowFileActionDialog = { viewModel.showFileActionDialog(it) },
                    onRequestDelete = { viewModel.requestDeleteFile(it) },
                    onRequestInfo = { viewModel.showInfoDialog(it) }
                )
            }

            if (!showOpeningScreen) {
                if (uiState.showActionDialog && uiState.fileForAction != null) {
                    FileActionOptionsDialog(
                        fileItem = uiState.fileForAction!!,
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
    onRefresh: () -> Unit,
    onCheckUpdates: () -> Unit,
    onSelectFile: (FileItem) -> Unit,
    onShowFileActionDialog: (FileItem) -> Unit,
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
            .padding(24.dp)
    ) {
        // Header
        HeaderSection(
            currentPath = uiState.currentDirectory.absolutePath,
            isCheckingUpdate = uiState.isCheckingUpdate,
            onNavigateUp = onNavigateUp,
            onRefresh = onRefresh,
            onCheckUpdates = onCheckUpdates
        )

        Spacer(modifier = Modifier.height(16.dp))

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
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(uiState.fileList, key = { _, item -> item.path }) { index, item ->
                            FileListItem(
                                fileItem = item,
                                isSelected = uiState.selectedFile?.path == item.path,
                                modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                                onFocused = { onSelectFile(item) },
                                onClick = {
                                    if (item.isDirectory) {
                                        onNavigateTo(item.file)
                                    } else {
                                        onShowFileActionDialog(item)
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
                    onNavigateInside = { file -> onNavigateTo(file) },
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
    isCheckingUpdate: Boolean,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Up Folder")
            }

            Spacer(modifier = Modifier.width(16.dp))

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

        Spacer(modifier = Modifier.width(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onCheckUpdates,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF282828),
                    contentColor = Color(0xFF00E5FF),
                    focusedContainerColor = Color(0xFF00E5FF),
                    focusedContentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Check Updates",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCheckingUpdate) "Checking..." else "Check Updates",
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF282828),
                    contentColor = Color.White,
                    focusedContainerColor = Color(0xFF00E5FF),
                    focusedContentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Refresh")
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

    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = if (isFocused) Color(0xFF00E5FF) else if (isSelected) Color(0xFF333333) else Color(0xFF222222)
        ),
        modifier = modifier
            .fillMaxWidth()
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
    onNavigateInside: (java.io.File) -> Unit,
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
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2B2B2B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(selectedFile.fileCategory),
                        contentDescription = null,
                        tint = getCategoryColor(selectedFile.fileCategory),
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = selectedFile.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                DetailRow(label = "Type", value = if (selectedFile.isDirectory) "Directory Folder" else "File (${selectedFile.file.extension.uppercase()})")
                DetailRow(label = "Size", value = selectedFile.formattedSize)
                DetailRow(label = "Modified", value = selectedFile.formattedLastModified)
                DetailRow(label = "Permissions", value = "Read: ${selectedFile.isReadable} | Write: ${selectedFile.isWritable}")
            }

            // Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectedFile.isDirectory) {
                    Button(
                        onClick = { onNavigateInside(selectedFile.file) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF282828),
                            contentColor = Color(0xFF00E5FF),
                            focusedContainerColor = Color(0xFF00E5FF),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Text(text = "Open Folder", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = { onRequestInfo(selectedFile) },
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
                    Text(text = "Full File Info")
                }

                Button(
                    onClick = { onRequestDelete(selectedFile) },
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
                    Text(text = "Delete", fontWeight = FontWeight.Bold)
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 13.sp)
        Text(
            text = value,
            color = Color.LightGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FileActionOptionsDialog(
    fileItem: FileItem,
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
                    .width(460.dp)
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

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onInfoClicked,
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
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "View File Info", fontWeight = FontWeight.Bold)
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
                        text = "New Version: ${updateInfo.versionName}",
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
                                text = if (isDownloading) "Downloading..." else "Install Update",
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
