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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Close
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantAttachment
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Folder
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

                    // 沙箱：看得见里面的文件，能点开能删
                    IconButton(onClick = { AssistantUiIntent.OpenSandboxBrowser.emit() }) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = stringResource(R.string.assistant_sandbox)
                        )
                    }
                    // 抽一鞭子：丢掉上一条回复重跑
                    IconButton(onClick = { AssistantUiIntent.Regenerate.emit() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.assistant_regenerate)
                        )
                    }
                    IconButton(onClick = { AssistantUiIntent.OpenPromptEditor.emit() }) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.assistant_prompt_settings))
                    }
                    IconButton(onClick = { AssistantUiIntent.OpenSessionList.emit() }) {
                        Icon(
                            Icons.Rounded.Forum,
                            contentDescription = stringResource(R.string.assistant_sessions)
                        )
                    }
                    IconButton(onClick = { AssistantUiIntent.ClearSandbox.emit() }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.assistant_clear_sandbox)
                        )
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
    if (state.sessionListVisible) {
        SessionListDialog(state, emit)
    }

    if (state.promptEditorVisible) {
        AssistantPromptEditor(state, emit)
    }

    if (state.sandboxBrowserVisible) {
        SandboxBrowserDialog(state, emit)
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
                // 模型发图时留的是 <image uuid="..." /> 标记，剥掉标记后单独渲染
                val imageUuids = IMAGE_MARKER.findAll(item.content)
                    .map { match -> match.groupValues[1] }
                    .toList()
                val visibleText = item.content.replace(IMAGE_MARKER, "").trim()
                if (visibleText.isNotEmpty()) {
                    Text(
                        text = visibleText,
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
                imageUuids.forEach { uuid ->
                    Spacer(modifier = Modifier.height(6.dp))
                    MessageImage(uuid = uuid)
                }
                // 附件在气泡下方留痕，发过什么一眼能看到
                if (!item.attachments.orEmpty().isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.attachments.orEmpty().forEach { attachment ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (attachment.isImage) {
                                        Icons.Rounded.Image
                                    } else {
                                        Icons.Rounded.Description
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = " ${attachment.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
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
                AttachmentTray(state.attachments, emit)
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
            TextButton(onClick = { AssistantUiIntent.ResetPrompt.emit() }) {
                Text(stringResource(R.string.assistant_prompt_reset))
            }
            TextButton(onClick = { AssistantUiIntent.ClosePromptEditor.emit() }) {
                Text(stringResource(R.string.assistant_done))
            }
        }
    )
}

/**
 * 待发送附件托盘。
 *
 * 图片显示缩略图、文本显示类型图标与体积，点卡片可预览内容，
 * 右上角叉号移除——对齐常见聊天客户端的附件呈现方式。
 */
@Composable
private fun AttachmentTray(
    attachments: List<AssistantAttachment>,
    emit: AssistantUiIntent.() -> Unit
) {
    var previewTarget by remember { mutableStateOf<AssistantAttachment?>(null) }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(attachments, key = { it.uri }) { attachment ->
            AttachmentCard(
                attachment = attachment,
                onPreview = { previewTarget = attachment },
                onRemove = { AssistantUiIntent.RemoveAttachment(attachment.uri).emit() }
            )
        }
    }
    previewTarget?.let { target ->
        AttachmentPreviewDialog(target) { previewTarget = null }
    }
}

/** 单个附件卡片：图片走缩略图，文本走图标。 */
@Composable
private fun AttachmentCard(
    attachment: AssistantAttachment,
    onPreview: () -> Unit,
    onRemove: () -> Unit
) {
    Box(modifier = Modifier.width(96.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPreview)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (attachment.isImage) {
                    AttachmentThumbnail(attachment.uri)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = attachment.name,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatSize(attachment.byteCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.assistant_send),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** 图片缩略图：按目标尺寸采样解码，避免大图占内存。 */
@Composable
private fun AttachmentThumbnail(uri: String) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val parsed = android.net.Uri.parse(uri)
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(parsed)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, options)
                }
                // 按最长边 256 采样，卡片只有 64dp 高
                var sample = 1
                val longest = maxOf(options.outWidth, options.outHeight)
                while (longest / sample > 256) sample *= 2
                val decodeOptions = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = context.contentResolver.openInputStream(parsed)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, decodeOptions)
                }
                bitmap?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/** 预览对话框：图片铺满，文本显示前若干行。 */
@Composable
private fun AttachmentPreviewDialog(
    attachment: AssistantAttachment,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(attachment.name) },
        text = {
            if (attachment.isImage) {
                val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, attachment.uri) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            val parsed = android.net.Uri.parse(attachment.uri)
                            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                            context.contentResolver.openInputStream(parsed)?.use {
                                android.graphics.BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                            }
                        }.getOrNull()
                    }
                }
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                } ?: Text(stringResource(R.string.assistant_attach_file))
            } else {
                val text by produceState("", attachment.uri) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(android.net.Uri.parse(attachment.uri))
                                ?.bufferedReader()?.use { reader -> reader.readText().take(4000) }
                        }.getOrNull().orEmpty()
                    }
                }
                Text(text = text, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.script_picker_done))
            }
        }
    )
}

/** 人类可读的体积。 */
private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}

/**
 * 会话列表对话框。
 *
 * 展示全部会话与消息条数，可新建、切换、归档与删除；
 * 顶部标注当前占用额度，超限时由存储层自动归档最旧的会话。
 */
@Composable
private fun SessionListDialog(
    state: AssistantUiState.Ready,
    emit: AssistantUiIntent.() -> Unit
) {
    AlertDialog(
        onDismissRequest = { AssistantUiIntent.CloseSessionList.emit() },
        title = {
            Text(
                text = stringResource(R.string.assistant_sessions) + " " +
                    stringResource(R.string.assistant_session_count, state.sessions.count { !it.archived })
            )
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.sessions, key = { it.id }) { session ->
                    val current = session.id == state.currentSessionId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { AssistantUiIntent.SwitchSession(session.id).emit() }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (session.archived) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = stringResource(R.string.assistant_session_meta, session.messageCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!session.archived) {
                            IconButton(
                                onClick = { AssistantUiIntent.ArchiveSession(session.id).emit() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Archive,
                                    contentDescription = stringResource(R.string.assistant_session_archive),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { AssistantUiIntent.DeleteSession(session.id).emit() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.assistant_session_delete),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { AssistantUiIntent.NewSession.emit() }) {
                Text(stringResource(R.string.assistant_session_new))
            }
        },
        dismissButton = {
            TextButton(onClick = { AssistantUiIntent.CloseSessionList.emit() }) {
                Text(stringResource(R.string.script_picker_done))
            }
        }
    )
}


/** 沙箱浏览器：列出文件，可删除；文本可长按查看内容。 */
@Composable
private fun SandboxBrowserDialog(
    state: AssistantUiState.Ready,
    emit: AssistantUiIntent.() -> Unit
) {
    AlertDialog(
        onDismissRequest = { AssistantUiIntent.CloseSandboxBrowser.emit() },
        title = { Text(stringResource(R.string.assistant_sandbox)) },
        text = {
            if (state.sandboxFiles.isEmpty()) {
                Text(stringResource(R.string.assistant_sandbox_empty))
            } else {
                LazyColumn {
                    items(state.sandboxFiles) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                            ) {
                                Text(
                                    text = file.path,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    text = formatSize(file.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    AssistantUiIntent.DeleteSandboxFile(file.path).emit()
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { AssistantUiIntent.CloseSandboxBrowser.emit() }) {
                Text(stringResource(R.string.script_picker_done))
            }
        }
    )
}

/** 模型发图时写在正文里的标记。 */
private val IMAGE_MARKER = Regex("<image\\s+uuid=\"([^\"]+)\"[^>]*/?>")

/** 消息里的图片：按 UUID 从文件库取样加载。 */
@Composable
private fun MessageImage(uuid: String) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uuid) {
        val loaded: androidx.compose.ui.graphics.ImageBitmap? = withContext(Dispatchers.IO) {
            runCatching {
                val repository = org.koin.core.context.GlobalContext.get()
                    .get<me.kafuuneko.rpclient.libs.room.repository.FileRepository>()
                repository.loadSampledBitmap(uuid, 720, 720)?.asImageBitmap()
            }.getOrNull()
        }
        value = loaded
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Text(
            text = "（图片加载失败）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

