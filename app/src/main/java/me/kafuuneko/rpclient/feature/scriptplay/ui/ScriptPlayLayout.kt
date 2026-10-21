package me.kafuuneko.rpclient.feature.scriptplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.automirrored.rounded.Undo
import me.kafuuneko.rpclient.libs.script.ScriptPlayLineParser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.scriptplay.presentation.ScriptPlayUiIntent
import me.kafuuneko.rpclient.feature.scriptplay.presentation.ScriptPlayUiState
import me.kafuuneko.rpclient.ui.widgets.AppTopBar

/**
 * 剧本模式界面。
 *
 * 顶部固定显示场景与剩余轮次，中部是对话，底部是输入栏；
 * 场景切换以胶囊形式贴在场景条下方，点一下即换场。
 */
@Composable
fun ScriptPlayLayout(
    uiState: ScriptPlayUiState,
    sceneNames: Map<String, String>,
    avatarOf: (String) -> String?,
    loadAvatar: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?,
    emit: ScriptPlayUiIntent.() -> Unit,
    onBack: () -> Unit
) {
    val state = uiState as? ScriptPlayUiState.Ready ?: return
    val listState = rememberLazyListState()

    // 新消息进来时滚到底部，避免玩家手动翻
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = state.packName,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { ScriptPlayUiIntent.ToggleAutoPlay.emit() }) {
                        Icon(
                            imageVector = if (state.autoPlay) {
                                Icons.Rounded.PauseCircle
                            } else {
                                Icons.Rounded.PlayCircle
                            },
                            contentDescription = stringResource(R.string.script_play_auto)
                        )
                    }
                    IconButton(onClick = { ScriptPlayUiIntent.OpenGmPanel.emit() }) {
                        Icon(
                            Icons.Rounded.Gavel,
                            contentDescription = stringResource(R.string.script_play_gm)
                        )
                    }
                    IconButton(onClick = { ScriptPlayUiIntent.RestartScene.emit() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.script_play_restart)
                        )
                    }
                    // 只有走过检查点场景才有得退，没到过就不占位置
                    if (state.hasCheckpoint) {
                        IconButton(onClick = { ScriptPlayUiIntent.RestoreCheckpoint.emit() }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = stringResource(R.string.script_play_undo)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景铺满整个版面，随场景切换实时更换；
            // 上面盖一层半透明让文字始终可读
            if (state.backgroundUuid.isNotBlank()) {
                SceneBackground(
                    uuid = state.backgroundUuid,
                    loadAvatar = loadAvatar,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
            ) {
                SceneBar(state, sceneNames, emit)
                // 在场角色：谁在场一目了然，头像横排可滑
                if (state.castNames.isNotEmpty()) {
                    CastStrip(
                        names = state.castNames,
                        muted = state.mutedCharacters,
                        avatarOf = avatarOf,
                        loadAvatar = loadAvatar
                    )
                }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages) { message ->
                    val fromPlayer = message.speaker == PLACEHOLDER_PLAYER
                    if (fromPlayer) {
                        PlayerBubble(message.content)
                    } else {
                        // 旁白与角色台词分开渲染：角色台词带头像，旁白保持素文本
                        ScriptPlayLineParser.parse(message.content).forEach { line ->
                            if (line.isNarration) {
                                NarrationBlock(line.text)
                            } else {
                                CharacterBubble(
                                    name = line.speaker.orEmpty(),
                                    text = line.text,
                                    avatarUuid = avatarOf(line.speaker.orEmpty()),
                                    loadAvatar = loadAvatar
                                )
                            }
                        }
                    }
                }
            }
            // 推荐行动：点一下即作为玩家输入发送，免去自己想说什么
            if (state.options.isNotEmpty() && state.finishedReason.isBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.options.forEach { option ->
                        Text(
                            text = option,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { ScriptPlayUiIntent.SelectOption(option).emit() }
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (state.finishedReason.isNotBlank()) {
                Text(
                    text = state.finishedReason,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                // 结束时给出口，而不是让玩家盯着一行红字
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { ScriptPlayUiIntent.RestartScene.emit() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.script_play_restart))
                    }
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.script_play_leave))
                    }
                }
            }
            InputBar(state, emit)
            }
        }
    }

    if (state.gmPanelVisible) {
        GmPanel(characters = state.castNames, state = state, emit = emit)
    }
}

/**
 * 在场角色横排。
 *
 * 让玩家随时看清「这一场有谁」，禁言中的角色压一层红色，
 * 免得点了禁言之后忘了谁被闭麦。
 */
@Composable
private fun CastStrip(
    names: List<String>,
    muted: Set<String>,
    avatarOf: (String) -> String?,
    loadAvatar: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        names.forEach { name ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    CharacterAvatar(
                        name = name,
                        avatarUuid = avatarOf(name),
                        loadAvatar = loadAvatar
                    )
                    if (name in muted) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.42f)
                                )
                        )
                    }
                }
                Text(
                    text = name,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


/** 场景条：当前场景、轮次进度与可切换场景。 */
@Composable
private fun SceneBar(
    state: ScriptPlayUiState.Ready,
    sceneNames: Map<String, String>,
    emit: ScriptPlayUiIntent.() -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.scene.sceneName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                val progress = if (state.scene.maxRounds <= 0) {
                    stringResource(R.string.script_play_unlimited)
                } else {
                    stringResource(
                        R.string.script_play_progress,
                        state.scene.roundCount,
                        state.scene.maxRounds
                    )
                }
                Text(
                    text = progress,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.scene.nextSceneIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.scene.nextSceneIds.forEach { sceneId ->
                        Text(
                            text = sceneNames[sceneId] ?: sceneId,
                            modifier = Modifier
                                .clickable { ScriptPlayUiIntent.SwitchScene(sceneId).emit() }
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

/** 输入栏。 */
@Composable
private fun InputBar(
    state: ScriptPlayUiState.Ready,
    emit: ScriptPlayUiIntent.() -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { ScriptPlayUiIntent.Impersonate.emit() },
                enabled = !state.generating && !state.impersonating
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.script_play_impersonate),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 轮空：不发话，让局势自己往前走
            IconButton(
                onClick = { ScriptPlayUiIntent.ContinueWithoutInput.emit() },
                enabled = !state.generating && state.finishedReason.isBlank()
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.script_play_skip),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = state.input,
                onValueChange = { ScriptPlayUiIntent.InputChanged(it).emit() },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 140.dp),
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
                val enabled = state.input.isNotBlank() && !state.generating
                IconButton(
                    onClick = { if (enabled) ScriptPlayUiIntent.Send.emit() },
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
    if (state.gmPanelVisible) {
        GmPanel(characters = state.castNames, state = state, emit = emit)
    }
}

/** 玩家发言者在存档中的标识。 */
private const val PLACEHOLDER_PLAYER = "玩家"

/**
 * 场景背景图。
 *
 * 图片来自图库的本地匹配，不触发生成，所以切场来回走也不会换图、不会花钱。
 * 加载失败时整块不占位，避免留一块空白。
 */
@Composable
private fun SceneBackground(
    uuid: String,
    loadAvatar: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uuid) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadAvatar(uuid) }.getOrNull()
        }
    }
    val image = bitmap ?: return
    Image(
        bitmap = image,
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
}

/**
 * GM 面板：禁言某个角色，或指定下一轮必须发言的人。
 *
 * 这两个开关只作用于下一轮，避免一直压着同一批角色的嘴。
 */
@Composable
private fun GmPanel(
    characters: List<String>,
    state: ScriptPlayUiState.Ready,
    emit: ScriptPlayUiIntent.() -> Unit
) {
    AlertDialog(
        onDismissRequest = { ScriptPlayUiIntent.CloseGmPanel.emit() },
        title = { Text(stringResource(R.string.script_play_gm)) },
        text = {
            if (characters.isEmpty()) {
                Text(stringResource(R.string.adventure_no_characters))
            } else {
                Column {
                    characters.forEach { name ->
                        val muted = name in state.mutedCharacters
                        val forced = state.forcedSpeaker == name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            // 禁言：本轮闭嘴
                            Text(
                                text = stringResource(R.string.script_play_gm_mute),
                                modifier = Modifier
                                    .clickable { ScriptPlayUiIntent.ToggleMute(name).emit() }
                                    .background(
                                        color = if (muted) {
                                            MaterialTheme.colorScheme.errorContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // 指定发言：本轮必须出声
                            Text(
                                text = stringResource(R.string.script_play_gm_speak),
                                modifier = Modifier
                                    .clickable { ScriptPlayUiIntent.ForceSpeaker(name).emit() }
                                    .background(
                                        color = if (forced) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { ScriptPlayUiIntent.CloseGmPanel.emit() }) {
                Text(stringResource(R.string.script_picker_done))
            }
        }
    )
}

/** 玩家气泡：右对齐、主色底。 */
@Composable
private fun PlayerBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** 旁白：无头像、无角色名，弱化显示，与台词区分开。 */
@Composable
private fun NarrationBlock(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 角色台词：左侧头像 + 名字 + 气泡。 */
@Composable
private fun CharacterBubble(
    name: String,
    text: String,
    avatarUuid: String?,
    loadAvatar: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        CharacterAvatar(name = name, avatarUuid = avatarUuid, loadAvatar = loadAvatar)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** 角色头像：有角色卡就加载图片，没有则用名字首字占位。 */
@Composable
private fun CharacterAvatar(
    name: String,
    avatarUuid: String?,
    loadAvatar: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, avatarUuid) {
        value = withContext(Dispatchers.IO) {
            val uuid = avatarUuid
            if (uuid.isNullOrBlank()) return@withContext null
            runCatching { loadAvatar(uuid) }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
