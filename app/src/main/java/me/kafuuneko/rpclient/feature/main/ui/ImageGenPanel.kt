package me.kafuuneko.rpclient.feature.main.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.presentation.MainImageGenSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.libs.imagegen.ImageGenAspect
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup

/**
 * 外接生图服务配置面板。
 *
 * 所有字段修改即时生效并写入偏好，因此不提供独立保存按钮；
 * 面板顶部说明当前是否具备发起请求的最低配置，避免用户在聊天页反复试错。
 *
 * @param state 生图配置面板状态
 * @param emit 发送设置意图
 */
@Composable
internal fun ImageGenPanel(
    state: MainImageGenSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    RpSettingsGroup {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.image_gen_settings_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.image_gen_settings_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!state.isConfigured) {
                Text(
                    text = stringResource(R.string.image_gen_not_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = { MainUiIntent.ChangeImageGenBaseUrl(it).emit() },
                label = { Text(stringResource(R.string.image_gen_base_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { MainUiIntent.ChangeImageGenApiKey(it).emit() },
                label = { Text(stringResource(R.string.image_gen_api_key)) },
                singleLine = true,
                // 密钥按密码样式展示，避免录屏或截图时意外泄露
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.model,
                onValueChange = { MainUiIntent.ChangeImageGenModel(it).emit() },
                label = { Text(stringResource(R.string.image_gen_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            AspectSelector(
                selected = state.aspect,
                onSelect = { MainUiIntent.SelectImageGenAspect(it).emit() }
            )
        }
    }
}

/** 默认画幅选择行；窄屏允许自动换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AspectSelector(selected: ImageGenAspect, onSelect: (ImageGenAspect) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.image_gen_aspect),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImageGenAspect.entries.forEach { aspect ->
                FilterChip(
                    selected = aspect == selected,
                    onClick = { onSelect(aspect) },
                    label = { Text(stringResource(aspect.settingsLabelRes())) }
                )
            }
        }
    }
}

/** @return 画幅在设置页中的本地化名称。 */
private fun ImageGenAspect.settingsLabelRes(): Int = when (this) {
    ImageGenAspect.Square -> R.string.image_gen_aspect_square
    ImageGenAspect.Portrait -> R.string.image_gen_aspect_portrait
    ImageGenAspect.Landscape -> R.string.image_gen_aspect_landscape
}
