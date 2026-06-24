package com.blissless.tensei.extensions

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.content.edit

data class ExtensionsUiState(
    val isLoading: Boolean = true,
    val extensions: List<Extension> = emptyList(),
    val error: String? = null,
    val repos: List<RepoState> = emptyList(),
    val refreshMessage: String? = null,
    val updatablePackageNames: Set<String> = emptySet()
)

data class RepoState(
    val url: String,
    val repo: Repo? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ExtensionsViewModel(application: Application) : AndroidViewModel(application) {

    private val detector = ExtensionDetector(application)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val repoPrefs = application.getSharedPreferences("extension_repos", Context.MODE_PRIVATE)
    private val KEY_SAVED_REPOS = "saved_repos"

    private val notificationManager = getApplication<Application>().getSystemService(NotificationManager::class.java)
    private val extensionUpdateNotificationId = 1002

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()

    private var lastExtensionCount = 0

    init {
        createExtensionUpdateChannel()
        loadExtensions()
        loadSavedRepos()
    }

    private fun createExtensionUpdateChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "extension_updates",
                    "Extension Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Extension update notifications"
                }
                notificationManager.createNotificationChannel(channel)
            }
        } catch (_: Exception) {}
    }

    fun loadExtensions(isManualRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val previousCount = lastExtensionCount
                val extensions = detector.detectInstalledExtensions()
                lastExtensionCount = extensions.size
                val diff = extensions.size - previousCount
                val message = when {
                    previousCount == 0 -> null
                    isManualRefresh -> when {
                        diff > 0 -> "Found $diff new extension(s)"
                        diff < 0 -> "${-diff} extension(s) removed"
                        else -> "No new extensions found"
                    }
                    diff > 0 -> "Found $diff new extension(s)"
                    else -> null
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    extensions = extensions,
                    refreshMessage = message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load extensions"
                )
            }
        }
    }

    fun addRepo(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        val currentRepos = _uiState.value.repos
        if (currentRepos.any { it.url == trimmed }) return

        _uiState.value = _uiState.value.copy(
            repos = currentRepos + RepoState(url = trimmed, isLoading = true)
        )
        persistRepos()

        viewModelScope.launch {
            try {
                val repo = fetchRepo(trimmed)
                updateRepoState(trimmed) {
                    copy(repo = repo, isLoading = false, error = null)
                }
            } catch (e: Exception) {
                updateRepoState(trimmed) {
                    copy(repo = null, isLoading = false, error = e.message ?: "Failed to load repo")
                }
            }
        }
    }

    fun removeRepo(url: String) {
        _uiState.value = _uiState.value.copy(
            repos = _uiState.value.repos.filter { it.url != url }
        )
        persistRepos()
    }

    fun clearRefreshMessage() {
        _uiState.value = _uiState.value.copy(refreshMessage = null)
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    fun installExtension(repoExtension: RepoExtension) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                Toast.makeText(ctx, "Downloading ${repoExtension.name}...", Toast.LENGTH_SHORT).show()
                val apkFile = downloadApk(repoExtension.apk, repoExtension.packageName)
                val uri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    apkFile
                )
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = uri
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                waitForInstallation(repoExtension.packageName, repoExtension.code)
                loadExtensions()
                refreshUpdatableState(findUpdatableExtensions())
            } catch (e: Exception) {
                Toast.makeText(ctx, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun waitForInstallation(packageName: String, targetVersionCode: Long = -1L): Boolean {
        val pm = getApplication<Application>().packageManager
        repeat(30) {
            try {
                val info = pm.getPackageInfo(packageName, 0)
                if (targetVersionCode < 0) return true
                val currentCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
                if (currentCode >= targetVersionCode) return true
            } catch (_: PackageManager.NameNotFoundException) { }
            kotlinx.coroutines.delay(1000.milliseconds)
        }
        return false
    }

    private suspend fun fetchRepo(repoUrl: String): Repo = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(repoUrl).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Server returned ${response.code} ${response.message}")
        }
        val body = response.body.string()
        val json = Json { ignoreUnknownKeys = true }
        val element = json.parseToJsonElement(body)
        val repo = parseRepoJson(repoUrl, element)
        repo.copy(
            extensions = repo.extensions.map { ext ->
                ext.copy(apk = resolveApkUrl(repoUrl, ext.apk))
            }
        )
    }

    private suspend fun downloadApk(url: String, packageName: String): File = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val cacheDir = File(ctx.cacheDir, "apks")
        cacheDir.mkdirs()
        val apkFile = File(cacheDir, "${packageName}.apk")

        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Server returned ${response.code} ${response.message}")
        }
        val body = response.body

        apkFile.outputStream().use { output ->
            body.byteStream().use { input ->
                input.copyTo(output)
            }
        }
        apkFile
    }

    private fun loadSavedRepos() {
        viewModelScope.launch {
            val savedJson = repoPrefs.getString(KEY_SAVED_REPOS, null) ?: return@launch
            try {
                val urls = Json.decodeFromString<List<String>>(savedJson)
                val currentUrls = _uiState.value.repos.map { it.url }.toSet()
                for (url in urls) {
                    if (url in currentUrls) continue
                    _uiState.value = _uiState.value.copy(
                        repos = _uiState.value.repos + RepoState(url = url, isLoading = true)
                    )
                    try {
                        val repo = fetchRepo(url)
                        updateRepoState(url) { copy(repo = repo, isLoading = false, error = null) }
                    } catch (e: Exception) {
                        updateRepoState(url) { copy(repo = null, isLoading = false, error = e.message) }
                    }
                }
                // Wait for extensions to be detected before checking for updates
                delay(500)
                checkForExtensionUpdates()
            } catch (_: Exception) { }
        }
    }

    private fun persistRepos() {
        val urls = _uiState.value.repos.map { it.url }
        val json = Json.encodeToString(urls)
        repoPrefs.edit {putString(KEY_SAVED_REPOS, json) }
    }

    private fun updateRepoState(url: String, transform: RepoState.() -> RepoState) {
        _uiState.value = _uiState.value.copy(
            repos = _uiState.value.repos.map {
                if (it.url == url) it.transform() else it
            }
        )
    }

    fun checkForExtensionUpdates() {
        viewModelScope.launch {
            val updatable = findUpdatableExtensions()
            refreshUpdatableState(updatable)
            if (updatable.isNotEmpty()) {
                val names = updatable.map { it.second.name }
                Log.i("ExtensionsViewModel", "Found ${updatable.size} updatable extension(s): $names")
                showUpdatesAvailableNotification(names)
                if (isAutoUpdateEnabled()) {
                    autoUpdateExtensions(updatable)
                }
            }
        }
    }

    private fun findUpdatableExtensions(): List<Pair<Extension, RepoExtension>> {
        val installed = _uiState.value.extensions
        if (installed.isEmpty()) return emptyList()
        val repoExtensionsByPkg = _uiState.value.repos
            .mapNotNull { it.repo }
            .flatMap { it.extensions }
            .groupBy { it.packageName }
        return installed.mapNotNull { ext ->
            val repoExt = repoExtensionsByPkg[ext.packageName]?.firstOrNull()
            if (repoExt != null && repoExt.code > ext.versionCode) {
                ext to repoExt
            } else {
                null
            }
        }
    }

    private fun refreshUpdatableState(updatable: List<Pair<Extension, RepoExtension>>) {
        val pkgNames = updatable.map { it.first.packageName }.toSet()
        _uiState.value = _uiState.value.copy(updatablePackageNames = pkgNames)
    }

    private fun isAutoUpdateEnabled(): Boolean {
        return try {
            val prefs = getApplication<Application>().getSharedPreferences("anilist_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("auto_update_extensions", true)
        } catch (_: Exception) { false }
    }

    private suspend fun autoUpdateExtensions(updatable: List<Pair<Extension, RepoExtension>>) {
        val ctx = getApplication<Application>()
        val updatedNames = mutableListOf<String>()
        for ((_, repoExt) in updatable) {
            try {
                val apkFile = downloadApk(repoExt.apk, repoExt.packageName)
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apkFile)
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = uri
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                if (waitForInstallation(repoExt.packageName, repoExt.code)) {
                    updatedNames.add(repoExt.name)
                }
            } catch (e: Exception) {
                Log.w("ExtensionsViewModel", "Failed to auto-update ${repoExt.name}", e)
            }
        }
        if (updatedNames.isNotEmpty()) {
            loadExtensions()
            showUpdatesSuccessNotification(updatedNames)
        }
        val remainingUpdatable = findUpdatableExtensions()
        refreshUpdatableState(remainingUpdatable)
    }

    fun updateExtension(packageName: String) {
        val repoExt = _uiState.value.repos
            .mapNotNull { it.repo }
            .flatMap { it.extensions }
            .firstOrNull { it.packageName == packageName } ?: return
        installExtension(repoExt)
    }

    private fun showUpdatesAvailableNotification(extensionNames: List<String>) {
        val ctx = getApplication<Application>()
        if (ActivityCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_extensions", true)
        }
        val pendingIntent = intent?.let {
            android.app.PendingIntent.getActivity(
                ctx, 0, it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(ctx, "extension_updates")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Extension Updates Available")
            .setContentText(extensionNames.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(extensionNames.joinToString("\n")))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(extensionUpdateNotificationId, notification)
    }

    private fun showUpdatesSuccessNotification(extensionNames: List<String>) {
        val ctx = getApplication<Application>()
        if (ActivityCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_extensions", true)
        }
        val pendingIntent = intent?.let {
            android.app.PendingIntent.getActivity(
                ctx, 0, it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(ctx, "extension_updates")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Extensions Updated")
            .setContentText(extensionNames.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(extensionNames.joinToString("\n")))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(extensionUpdateNotificationId, notification)
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}


