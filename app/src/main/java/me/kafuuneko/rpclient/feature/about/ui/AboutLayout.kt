package me.kafuuneko.rpclient.feature.about.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.about.presentation.AboutAppInfoState
import me.kafuuneko.rpclient.feature.about.presentation.AboutUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.ui.theme.AccentSkyColor
import me.kafuuneko.rpclient.ui.theme.AccentVioletColor
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator

/** 关于页入口，仅渲染展示状态，并将系统操作交回宿主。 */
@Composable
fun AboutLayout(
    uiState: AboutUiState,
    onBack: () -> Unit,
    onCopyDeveloperEmail: () -> Unit,
    onOpenRepository: () -> Unit,
    onRateApp: () -> Unit,
    onOpenFeedback: () -> Unit,
    onDownloadReadme: () -> Unit
) {
    // 装饰与正文共享背景，系统栏和内容避让仍由 Scaffold 处理。
    Box(modifier = Modifier.fillMaxSize()) {
        AboutBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { AboutTopBar(onBack) }
        ) { paddingValues ->
            AboutViewContent(
                uiState = uiState,
                onCopyDeveloperEmail = onCopyDeveloperEmail,
                onOpenRepository = onOpenRepository,
                onRateApp = onRateApp,
                onOpenFeedback = onOpenFeedback,
                onDownloadReadme = onDownloadReadme,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun AboutViewContent(
    uiState: AboutUiState,
    onCopyDeveloperEmail: () -> Unit,
    onOpenRepository: () -> Unit,
    onRateApp: () -> Unit,
    onOpenFeedback: () -> Unit,
    onDownloadReadme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    // 限制平板上的阅读宽度；整个页面滚动以容纳大字体和横屏。
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .draggableScrollIndicator(scrollState)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AboutHeroHeader(uiState.appInfo.isDevelopmentBuild)
            AboutRatingCard(onRateApp)
            AboutCustomSection(onDownloadReadme)
            AboutCommunitySection(uiState.githubRepoName, onOpenFeedback, onOpenRepository)
            AboutSupportSection(uiState.developerEmail, onCopyDeveloperEmail, onOpenRepository)
            AboutAppInfoSection(uiState.appInfo)
            AboutFooter()
        }
    }
}

/**
 * 定制版分组：展示改版作者并提供定制说明文档的导出入口。
 *
 * 说明文档随包内置在 assets 中，导出走系统文件选择器，
 * 因此不需要存储权限，也不依赖应用私有目录的可见性。
 */
@Composable
private fun AboutCustomSection(onDownloadReadme: () -> Unit) {
    AboutSection(stringResource(R.string.section_custom_build)) {
        AboutInfoRow(
            icon = Icons.Rounded.Person,
            label = stringResource(R.string.uv_author_label),
            value = AppModel.UV_AUTHOR
        )
        AboutDivider()
        AboutActionRow(
            icon = Icons.Rounded.Description,
            accent = AccentVioletColor,
            title = stringResource(R.string.uv_readme_label),
            subtitle = stringResource(R.string.uv_readme_desc),
            action = stringResource(R.string.uv_readme_download),
            actionIcon = Icons.Rounded.Download,
            onClick = onDownloadReadme
        )
    }
}

@Composable
private fun AboutCommunitySection(
    repoName: String,
    onOpenFeedback: () -> Unit,
    onOpenRepository: () -> Unit
) {
    // 统一外链尾标，保留现有反馈与仓库跳转行为。
    AboutSection(stringResource(R.string.section_community)) {
        AboutActionRow(
            icon = Icons.Rounded.BugReport,
            accent = MaterialTheme.colorScheme.secondary,
            title = stringResource(R.string.feedback_label),
            subtitle = stringResource(R.string.feedback_desc),
            action = stringResource(R.string.about_github),
            actionIcon = Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = onOpenFeedback
        )
        AboutDivider()
        AboutActionRow(
            icon = Icons.Rounded.Code,
            accent = AccentVioletColor,
            title = stringResource(R.string.github_repo_label),
            subtitle = repoName,
            action = stringResource(R.string.about_github),
            actionIcon = Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = onOpenRepository
        )
    }
}

@Composable
private fun AboutSupportSection(
    developerEmail: String,
    onCopyDeveloperEmail: () -> Unit,
    onOpenRepository: () -> Unit
) {
    // 邮箱置于副标题，尾部仅保留短操作标签，避免小屏被长地址挤压。
    AboutSection(stringResource(R.string.section_about)) {
        AboutActionRow(
            icon = Icons.Rounded.Email,
            accent = AccentSkyColor,
            title = stringResource(R.string.developer_contact),
            subtitle = developerEmail,
            action = stringResource(R.string.copy),
            actionIcon = Icons.Rounded.ContentCopy,
            pillAction = true,
            onClick = onCopyDeveloperEmail
        )
        AboutDivider()
        AboutActionRow(
            icon = Icons.Rounded.Info,
            accent = MaterialTheme.colorScheme.secondary,
            title = stringResource(R.string.open_source_license),
            subtitle = stringResource(R.string.open_source_license_desc),
            action = stringResource(R.string.visit_repo),
            onClick = onOpenRepository
        )
    }
}

@Composable
private fun AboutAppInfoSection(state: AboutAppInfoState) {
    AboutSection(stringResource(R.string.about_app_information)) {
        AboutInfoRow(Icons.Rounded.ViewInAr, stringResource(R.string.about_version), state.versionName)
        AboutDivider()
        AboutInfoRow(Icons.Rounded.Numbers, stringResource(R.string.about_build_number), state.buildNumber)
    }
}

@Composable
private fun AboutFooter() {
    // 尚无独立法律页面，沿用已有开源声明，避免出现无法打开的入口。
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.about_copyright),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(name = "Light", widthDp = 390, heightDp = 844)
@Preview(name = "Small / large text", widthDp = 320, heightDp = 740, fontScale = 1.5f)
@Composable
private fun AboutLayoutPreview() {
    // 预览固定配色以方便比较布局；暗色另有独立入口。
    AppTheme(dynamicColor = false) {
        AboutPreviewContent()
    }
}

@Preview(name = "Dark theme", widthDp = 430, heightDp = 932)
@Composable
private fun AboutDarkPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) { AboutPreviewContent() }
}

@Composable
private fun AboutPreviewContent() {
    // 示例元数据只用于预览，运行时由宿主读取安装包信息。
    AboutLayout(
        uiState = AboutUiState(
            appInfo = AboutAppInfoState("2026.2.3-dev", "20260203", true),
            githubRepoUrl = "https://github.com/KafuuNeko/RPClient",
            githubRepoName = "KafuuNeko/RPClient",
            developerEmail = "developer@example.com"
        ),
        onBack = {},
        onCopyDeveloperEmail = {},
        onOpenRepository = {},
        onRateApp = {},
        onOpenFeedback = {},
        onDownloadReadme = {}
    )
}
