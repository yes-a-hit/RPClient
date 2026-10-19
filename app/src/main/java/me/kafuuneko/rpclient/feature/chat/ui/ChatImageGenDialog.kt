package me.kafuuneko.rpclient.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.presentation.ChatDialogState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.libs.imagegen.ImageGenAspect
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone

/**
 * 外接生图对话框。
 *
 * 用户输入画面描述并选择画幅后发起请求，生成结果以角色图片消息写入当前会话。
 * 组件不持有业务状态，全部通过 [state] 渲染并回传意图。
 *
 * @param state 对话框状态
 * @param onDismiss 关闭对话框的回调
 * @param emit 发送聊天意图
 */
@Composable
fun ChatImageGenDialog(
    state: ChatDialogState.ImageGen,
    onDismiss: () -> Unit,
    emit: ChatUiIntent.() -> Unit
) {
    AppDialogScaffold(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.image_gen_title),
        badgeIcon = Icons.Rounded.Image,
        badgeTone = DialogBadgeTone.Primary,
        subtitle = stringResource(R.string.image_gen_subtitle),
        confirmText = stringResource(R.string.image_gen_generate),
        confirmEnabled = state.canGenerate,
        isConfirmLoading = state.generating,
        onConfirm = { ChatUiIntent.GenerateImage.emit() },
        onDismiss = onDismiss,
        scrollableContent = true
    ) {
        OutlinedTextField(
            value = state.prompt,
            onValueChange = { ChatUiIntent.ChangeImageGenPrompt(it).emit() },
            label = { Text(stringResource(R.string.image_gen_prompt)) },
            placeholder = { Text(stringResource(R.string.image_gen_prompt_hint)) },
            minLines = 4,
            maxLines = 8,
            enabled = !state.generating,
            modifier = Modifier.fillMaxWidth()
        )
        AspectChips(
            selected = state.aspect,
            enabled = !state.generating,
            onSelect = { ChatUiIntent.ChangeImageGenAspect(it).emit() }
        )
        // 失败原因直接留在对话框内，避免生成失败后用户看不到具体错误
        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }
    }
}

/** 画幅选择行；受限于弹窗宽度采用自动换行排布。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AspectChips(
    selected: ImageGenAspect,
    enabled: Boolean,
    onSelect: (ImageGenAspect) -> Unit
) {
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
                    enabled = enabled,
                    label = { Text(stringResource(aspect.labelRes())) }
                )
            }
        }
    }
}

/** 画幅对应的本地化标签。 */
private fun ImageGenAspect.labelRes(): Int = when (this) {
    ImageGenAspect.Square -> R.string.image_gen_aspect_square
    ImageGenAspect.Portrait -> R.string.image_gen_aspect_portrait
    ImageGenAspect.Landscape -> R.string.image_gen_aspect_landscape
}
