package me.kafuuneko.rpclient.feature.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantMessageItem
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantRole
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantUiIntent
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantUiState

/**
 * 助手页面布局。
 *
 * 页面分三段：顶部状态条显示提示词来源与沙箱占用，中部是消息流，
 * 底部是输入区。工具结果以独立卡片呈现，避免和自然语言回复混在一起。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantLayout(
    uiState: AssistantUiState,
    emit: AssistantUiIntent.() -> Unit,
    onBack: () -> Unit
) {
    val state = uiState as? AssistantUiState.Ready ?: return
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assistant_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {

                    IconButton(onClick = { AssistantUiIntent.OpenPromptEditor.emit() }) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.assistant_prompt_settings))
                    }
                    IconButton(onClick = { AssistantUiIntent.ClearSession.emit() }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.assistant_clear))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // 先消耗 Scaffold 已给的系统栏内边距，再补键盘高度，
                // 否则 edge-to-edge 下会和输入法 inset 重复计算，导致留白或遮挡
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            AssistantStatusBar(state)
            AssistantMessageList(
                items = state.items,
                streaming = state.streamingReply,
                isGenerating = state.isGenerating,
                modifier = Modifier.weight(1f)
            )
            AssistantInputBar(state, emit)
        }
    }
    if (state.promptEditorVisible) {
        AssistantPromptEditor(state, emit)
    }
}

/** 顶部状态条：提示词来源与沙箱占用。 */
@Composable
private fun AssistantStatusBar(state: AssistantUiState.Ready) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "${state.promptHint} · ${state.sandboxHint}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.statusText.isNotBlank()) {
                Text(
                    text = state.statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 消息流：包含历史、工具卡片与正在流式输出的草稿。 */
@Composable
private fun AssistantMessageList(
    items: List<AssistantMessageItem>,
    streaming: String,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val total = items.size + if (streaming.isNotBlank() || isGenerating) 1 else 0

    // 新增内容时保持贴底，符合聊天阅读习惯
    LaunchedEffect(total, streaming.length) {
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (items.isEmpty() && !isGenerating) {
            item {
                Text(
                    text = stringResource(R.string.assistant_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(items) { item -> AssistantMessageBubble(item) }
        if (streaming.isNotBlank() || isGenerating) {
            item {
                AssistantMessageBubble(
                    AssistantMessageItem(
                        role = AssistantRole.Assistant,
                        content = streaming.ifBlank { stringResource(R.string.assistant_thinking) }
                    )
                )
            }
        }
    }
}

/** 单条消息气泡；工具结果使用等宽小字卡片。 */
@Composable
private fun AssistantMessageBubble(item: AssistantMessageItem) {
    val isUser = item.role == AssistantRole.User
    val isTool = item.role == AssistantRole.Tool
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer
                isTool -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            },
            shape = RoundedCornerShape(14.dp),
            tonalElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.94f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isTool) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Build,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " ${item.toolName ?: "tool"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = item.content,
                    style = if (isTool) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = if (isTool) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

/** 底部输入区：发送与停止共用同一个位置。 */
@Composable
private fun AssistantInputBar(
    state: AssistantUiState.Ready,
    emit: AssistantUiIntent.() -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column {
            if (state.attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.attachments.forEach { attachment ->
                        Text(
                            text = attachment.name,
                            modifier = Modifier
                                .clickable {
                                    AssistantUiIntent.RemoveAttachment(attachment.uri).emit()
                                }
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { AssistantUiIntent.ImportFilesClick.emit() }) {
                Icon(
                    Icons.Rounded.AttachFile,
                    contentDescription = stringResource(R.string.assistant_attach_file),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = state.input,
                onValueChange = { AssistantUiIntent.InputChanged(it).emit() },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp, max = 140.dp),
                placeholder = { Text(stringResource(R.string.assistant_input_hint)) },
                maxLines = 5
            )
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (state.isGenerating) {
                    IconButton(onClick = { AssistantUiIntent.Stop.emit() }) {
                        Icon(
                            Icons.Rounded.Stop,
                            contentDescription = stringResource(R.string.assistant_stop),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    // 空输入时按钮保留位置但降低对比度，暗示尚不可发送
                    val enabled = state.input.isNotBlank()
                    IconButton(
                        onClick = { if (enabled) AssistantUiIntent.Send.emit() },
                        enabled = enabled
                    ) {
                        Icon(
                            Icons.Rounded.Send,
                            contentDescription = stringResource(R.string.assistant_send),
                            tint = MaterialTheme.colorScheme.onPrimary.copy(
                                alpha = if (enabled) 1f else 0.45f
                            )
                        )
                    }
                }
            }
        }
        }
    }
}

/** 提示词设置弹窗：继承全局提示词或使用独立提示词。 */
@Composable
private fun AssistantPromptEditor(
    state: AssistantUiState.Ready,
    emit: AssistantUiIntent.() -> Unit
) {
    var draft by remember(state.customPrompt) { mutableStateOf(state.customPrompt) }
    AlertDialog(
        onDismissRequest = { AssistantUiIntent.ClosePromptEditor.emit() },
        title = { Text(stringResource(R.string.assistant_prompt_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.assistant_inherit_global),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.assistant_inherit_global_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.inheritGlobalPrompt,
                        onCheckedChange = { AssistantUiIntent.PromptModeChanged(it).emit() }
                    )
                }
                if (!state.inheritGlobalPrompt) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            AssistantUiIntent.CustomPromptChanged(it).emit()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp),
                        label = { Text(stringResource(R.string.assistant_custom_prompt)) },
                        maxLines = 10
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { AssistantUiIntent.ClosePromptEditor.emit() }) {
                Text(stringResource(R.string.assistant_done))
            }
        }
    )
}
