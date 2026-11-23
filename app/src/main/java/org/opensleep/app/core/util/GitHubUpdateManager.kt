package org.opensleep.app.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.opensleep.app.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L
)

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList()
)

data class AppUpdateInfo(
    val currentVersion: String,
    val latestTag: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long,
    val isUpdateAvailable: Boolean
)

sealed interface DownloadUpdateState {
    data class Progress(val percent: Int, val downloadedMb: Float, val totalMb: Float) : DownloadUpdateState
    data class ReadyToInstall(val apkFile: File) : DownloadUpdateState
    data class Error(val message: String) : DownloadUpdateState
}

@Singleton
class GitHubUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/WaterKansuza/OpenSleep/releases/latest"
        private val ALLOWED_GITHUB_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "codeload.github.com"
        )
        private const val MAX_REDIRECTS = 5
    }

    suspend fun checkLatestRelease(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = openSafeConnection(LATEST_RELEASE_API, acceptHeader = "application/vnd.github+json")
            try {
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    return@runCatching AppUpdateInfo(
                        currentVersion = BuildConfig.VERSION_NAME,
                        latestTag = "v${BuildConfig.VERSION_NAME}",
                        releaseTitle = "Chưa có bản Release trên GitHub",
                        releaseNotes = "",
                        htmlUrl = "https://github.com/WaterKansuza/OpenSleep/releases",
                        apkDownloadUrl = null,
                        apkSizeBytes = 0L,
                        isUpdateAvailable = false
                    )
                }
                if (code !in 200..299) {
                    error("GitHub trả về mã lỗi HTTP $code")
                }
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString<GitHubReleaseDto>(raw)
                val apkAsset = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                }
                val isNewer = !release.draft && !release.prerelease &&
                    isRemoteVersionNewer(release.tagName, BuildConfig.VERSION_NAME)

                AppUpdateInfo(
                    currentVersion = BuildConfig.VERSION_NAME,
                    latestTag = release.tagName,
                    releaseTitle = release.name ?: release.tagName,
                    releaseNotes = release.body.orEmpty().trim(),
                    htmlUrl = release.htmlUrl,
                    apkDownloadUrl = apkAsset?.browserDownloadUrl,
                    apkSizeBytes = apkAsset?.size ?: 0L,
                    isUpdateAvailable = isNewer
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    fun downloadApk(apkUrl: String, versionTag: String): Flow<DownloadUpdateState> = flow {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { oldFile ->
                if (oldFile.isFile && oldFile.name.endsWith(".apk")) oldFile.delete()
            }

            val safeTag = versionTag.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val targetFile = File(updatesDir, "OpenSleep-$safeTag.apk")
            if (!targetFile.canonicalPath.startsWith(updatesDir.canonicalPath)) {
                error("Đường dẫn tệp cập nhật không hợp lệ")
            }

            val conn = openSafeConnection(apkUrl, acceptHeader = "application/vnd.android.package-archive")
            try {
                if (conn.responseCode !in 200..299) {
                    error("Không thể tải APK (HTTP ${conn.responseCode})")
                }
                val totalBytes = conn.contentLengthLong.coerceAtLeast(1L)
                var downloaded = 0L
                val buffer = ByteArray(16 * 1024)
                var lastEmitPct = -1

                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (pct != lastEmitPct) {
                                lastEmitPct = pct
                                emit(
                                    DownloadUpdateState.Progress(
                                        percent = pct,
                                        downloadedMb = downloaded / (1024f * 1024f),
                                        totalMb = totalBytes / (1024f * 1024f)
                                    )
                                )
                            }
                        }
                    }
                }
                emit(DownloadUpdateState.ReadyToInstall(targetFile))
            } finally {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            emit(DownloadUpdateState.Error(t.message ?: "Lỗi tải bản cập nhật"))
        }
    }.flowOn(Dispatchers.IO)

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcePermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildInstallApkIntent(apkFile: File): Intent {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isRemoteVersionNewer(remoteTag: String, currentVersion: String): Boolean {
        val remoteParts = parseVersionParts(remoteTag)
        val currentParts = parseVersionParts(currentVersion)
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    private fun parseVersionParts(raw: String): List<Int> {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .split(".")
            .map { it.toIntOrNull() ?: 0 }
    }

    private fun openSafeConnection(initialUrl: String, acceptHeader: String): HttpURLConnection {
        var currentUrl = initialUrl
        for (hop in 0..MAX_REDIRECTS) {
            val uri = URI(currentUrl)
            val host = uri.host?.lowercase() ?: error("URL thiếu tên miền")
            require(uri.scheme.equals("https", ignoreCase = true) && host in ALLOWED_GITHUB_HOSTS) {
                "Từ chối kết nối ngoài GitHub: $host"
            }

            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 12_000
                readTimeout = 25_000
                setRequestProperty("Accept", acceptHeader)
                setRequestProperty("User-Agent", "OpenSleep-Android/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == 307 ||
                code == 308
            ) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) error("Redirect thiếu Location")
                currentUrl = URI(currentUrl).resolve(location).toString()
                continue
            }
            return conn
        }
        error("Quá nhiều chuyển hướng HTTP khi tải bản cập nhật")
    }
}