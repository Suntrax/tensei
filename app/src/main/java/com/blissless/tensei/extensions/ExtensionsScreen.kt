package com.blissless.tensei.extensions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    viewModel: ExtensionsViewModel = viewModel(),
    onBrowseChanged: ((Boolean) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var repoUrl by remember { mutableStateOf("") }
    var selectedRepoUrl by remember { mutableStateOf<String?>(null) }
    var reposExpanded by remember { mutableStateOf(false) }
    val installedPackages = uiState.extensions.map { it.packageName }.toSet()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadExtensions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(selectedRepoUrl) {
        onBrowseChanged?.invoke(selectedRepoUrl != null)
    }

    BackHandler(enabled = selectedRepoUrl != null) {
        selectedRepoUrl = null
    }

    val selectedRepoState = selectedRepoUrl?.let { url ->
        uiState.repos.find { it.url == url }
    }

    if (selectedRepoState != null) {
        ExtensionBrowserScreen(
            repoState = selectedRepoState,
            installedPackages = installedPackages,
            onInstall = { viewModel.installExtension(it) },
            onBack = { selectedRepoUrl = null },
            onRemoveRepo = { url -> viewModel.removeRepo(url) }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Extensions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { viewModel.loadExtensions() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        OutlinedTextField(
            value = repoUrl,
            onValueChange = { repoUrl = it },
            label = { Text("Repo URL") },
            placeholder = { Text("https://example.com/index.json") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (repoUrl.isNotBlank()) {
                            viewModel.addRepo(repoUrl)
                            repoUrl = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add repo")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.repos.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reposExpanded = !reposExpanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "My Repos (${uiState.repos.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            if (reposExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (reposExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(uiState.repos, key = { it.url }) { repoState ->
                    AnimatedVisibility(
                        visible = reposExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        RepoCard(
                            repoState = repoState,
                            onClick = { selectedRepoUrl = repoState.url },
                            onRemoveRepo = { viewModel.removeRepo(repoState.url) }
                        )
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                uiState.error != null -> {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.error!!,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadExtensions() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                uiState.extensions.isEmpty() && uiState.repos.isEmpty() -> {
                    item {
                        Text(
                            text = "No extensions found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 48.dp).fillMaxWidth()
                        )
                    }
                }
                else -> {
                    items(uiState.extensions, key = { it.packageName }) { ext ->
                        val ctx = LocalContext.current
                        InstalledExtensionCard(
                            extension = ext,
                            onSettings = { openAppSettings(ctx, ext.packageName) },
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun RepoCard(
    repoState: RepoState,
    onClick: () -> Unit,
    onRemoveRepo: () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove repo?") },
            text = {
                Text("This will remove ${repoState.repo?.name ?: repoState.url} from your repos.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveDialog = false
                        onRemoveRepo()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = repoState.repo != null) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repoState.repo?.name ?: repoState.url,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (!repoState.repo?.description.isNullOrBlank()) {
                    Text(
                        text = repoState.repo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (repoState.repo != null) {
                        Text(
                            text = "${repoState.repo.extensions.size} extension(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    repoState.error?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (repoState.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            val ctx = LocalContext.current
            IconButton(onClick = {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Repo URL", repoState.url))
                Toast.makeText(ctx, "URL copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showRemoveDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove repo", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun openAppSettings(context: android.content.Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    android.widget.Toast.makeText(context, "Opening app settings...", android.widget.Toast.LENGTH_SHORT).show()
    context.startActivity(intent)
}

@Composable
private fun InstalledExtensionCard(
    extension: Extension,
    onSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val iconBitmap = extension.icon?.toBitmap(64, 64)
            if (iconBitmap != null) {
                Image(
                    painter = BitmapPainter(iconBitmap.asImageBitmap()),
                    contentDescription = extension.name,
                    modifier = Modifier.size(48.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSettings),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = extension.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "v${extension.versionName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (extension.isNsfw) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "NSFW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "App info",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


