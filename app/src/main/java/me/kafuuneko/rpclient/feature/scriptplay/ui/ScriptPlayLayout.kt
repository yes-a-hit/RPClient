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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
                    IconButton(onClick = { ScriptPlayUiIntent.RestartScene.emit() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.script_play_restart)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            SceneBar(state, sceneNames, emit)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages) { message ->
                    val fromPlayer = message.speaker == PLACEHOLDER_PLAYER
                    Surface(
                        modifier = Modifier.fillMaxWidth(if (fromPlayer) 0.85f else 0.95f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (fromPlayer) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        tonalElevation = 1.dp
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = message.speaker,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
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
            }
            InputBar(state, emit)
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
}

/** 玩家发言者在存档中的标识。 */
private const val PLACEHOLDER_PLAYER = "玩家"
