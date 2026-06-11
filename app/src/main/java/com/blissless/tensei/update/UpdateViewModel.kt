package com.blissless.tensei.update

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import android.os.Build
import java.io.File

data class UpdateUiState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val release: GitHubRelease? = null,
    val error: String? = null,
    val downloadedFile: File? = null
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val owner = "Suntrax"
    private val repo = "Tensei"

    fun checkForUpdates(showToast: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = UpdateUiState(isChecking = true)
            try {
                val release = fetchLatestRelease()
                val currentVersion = getApplication<Application>().let { ctx ->
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
                }
                if (release.tagName.isBlank()) {
                    _uiState.value = UpdateUiState(error = "No releases found")
                    return@launch
                }
                val cleanTag = release.tagName.removePrefix("v").removePrefix("V")
                if (compareVersions(cleanTag, currentVersion) <= 0) {
                    _uiState.value = UpdateUiState(release = release)
                    if (showToast) {
                        Toast.makeText(getApplication(), "Already up to date ($currentVersion)", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                _uiState.value = UpdateUiState(release = release)
            } catch (e: Exception) {
                _uiState.value = UpdateUiState(error = e.message ?: "Update check failed")
            }
        }
    }

    fun setReleaseAndDownload(release: GitHubRelease) {
        _uiState.value = UpdateUiState(release = release)
        downloadUpdate()
    }

    fun downloadUpdate() {
        val release = _uiState.value.release ?: return
        val targetAbi = getDeviceAbi()
        val asset = release.assets.find { it.name.contains(targetAbi, ignoreCase = true) }
            ?: release.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: run {
                _uiState.value = _uiState.value.copy(error = "No APK found for $targetAbi")
                return
            }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDownloading = true, error = null)
            try {
                val file = downloadApk(asset.downloadUrl, "tensei-update")
                _uiState.value = _uiState.value.copy(isDownloading = false, downloadedFile = file)
                installApk(file)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDownloading = false, error = e.message ?: "Download failed")
            }
        }
    }

    private suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("GitHub API returned ${response.code}")
        }
        val body = response.body.string()
        json.decodeFromString<GitHubRelease>(body)
    }

    private suspend fun downloadApk(url: String, fileName: String): File = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val cacheDir = File(ctx.cacheDir, "apks")
        cacheDir.mkdirs()
        cacheDir.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() }
        val apkFile = File(cacheDir, "$fileName.apk")

        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Server returned ${response.code}")
        }
        apkFile.outputStream().use { output ->
            response.body.byteStream().use { input -> input.copyTo(output) }
        }
        apkFile
    }

    private fun installApk(file: File) {
        val ctx = getApplication<Application>()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "Failed to open installer: ${e.message}")
        }
    }

    private fun getDeviceAbi(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.startsWith("arm64") -> "arm64-v8a"
            abi.startsWith("armeabi") -> "armeabi-v7a"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> abi
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}



