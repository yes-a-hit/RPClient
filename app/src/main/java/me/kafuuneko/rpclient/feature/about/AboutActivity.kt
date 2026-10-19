package me.kafuuneko.rpclient.feature.about

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.kafuuneko.rpclient.feature.about.presentation.AboutUiState
import me.kafuuneko.rpclient.feature.about.presentation.AboutAppInfoState
import me.kafuuneko.rpclient.feature.about.ui.AboutLayout
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreActivity

/** 关于页面宿主，负责提供版本、社区反馈和项目联系信息。 */
class AboutActivity : CoreActivity() {
    /**
     * 定制说明导出。
     *
     * 使用系统文件选择器让用户决定落盘位置，因此不需要申请存储权限；
     * 文档正文来自 assets，写入失败时只提示重试，不改变页面状态。
     */
    private val mReadmeSaver = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        uri ?: return@registerForActivityResult
        val saved = runCatching {
            contentResolver.openOutputStream(uri)?.use { output ->
                assets.open(AppModel.UV_README_ASSET).use { it.copyTo(output) }
            } != null
        }.getOrDefault(false)
        Toast.makeText(
            this,
            if (saved) R.string.uv_readme_saved else R.string.uv_readme_failed,
            Toast.LENGTH_SHORT
        ).show()
    }

    /** 提供安装包元数据，并将页面操作交给宿主执行。 */
    @Composable
    override fun ViewContent() {
        // - 读取应用包信息与静态链接，构造页面展示状态
        val uiState = remember {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            AboutUiState(
                appInfo = AboutAppInfoState(
                    versionName = packageInfo.versionName ?: getString(R.string.unknown_version),
                    buildNumber = PackageInfoCompat.getLongVersionCode(packageInfo).toString(),
                    isDevelopmentBuild = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                ),
                githubRepoUrl = AppModel.GITHUB_REPO,
                githubRepoName = "KafuuNeko/RPClient",
                developerEmail = AppModel.EMAIL,
                githubIssuesUrl = "${AppModel.GITHUB_REPO}/issues"
            )
        }

        // - 渲染关于界面并绑定外部跳转交互
        Surface(modifier = Modifier.fillMaxSize()) {
            AboutLayout(
                uiState = uiState,
                onBack = { finish() },
                onCopyDeveloperEmail = { copyDeveloperEmail(uiState.developerEmail) },
                onOpenRepository = { safeOpenUrl(uiState.githubRepoUrl) },
                onRateApp = { openGooglePlay() },
                onOpenFeedback = { safeOpenUrl(uiState.githubIssuesUrl) },
                onDownloadReadme = { mReadmeSaver.launch(AppModel.UV_README_FILE_NAME) }
            )
        }
    }

    /** 复制开发者邮箱至剪贴板并提示用户。 */
    private fun copyDeveloperEmail(email: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                getString(R.string.developer_contact),
                email
            )
        )
        Toast.makeText(
            this,
            R.string.copied_to_clipboard,
            Toast.LENGTH_SHORT
        ).show()
    }

    /** 打开 Google Play 商店评价页面，缺失对应市场应用时平滑降级至网页版。 */
    private fun openGooglePlay() {
        val pkg = packageName
        val playStoreIntent = Intent(
            Intent.ACTION_VIEW,
            "market://details?id=$pkg".toUri()
        ).apply {
            setPackage("com.android.vending")
        }
        try {
            startActivity(playStoreIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri()))
            } catch (_: ActivityNotFoundException) {
                safeOpenUrl("https://play.google.com/store/apps/details?id=$pkg")
            }
        }
    }

    /** 安全打开外部超链接，若无可用浏览器则回退至复制链接到剪贴板。 */
    private fun safeOpenUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText(getString(R.string.about), url)
            )
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }
}
