package me.kafuuneko.rpclient.feature.scriptadventure.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.scriptadventure.presentation.AdventurePackItem
import me.kafuuneko.rpclient.feature.scriptadventure.presentation.AdventureSaveItem
import me.kafuuneko.rpclient.feature.scriptadventure.presentation.ScriptAdventureUiIntent
import me.kafuuneko.rpclient.feature.scriptadventure.presentation.ScriptAdventureUiState
import me.kafuuneko.rpclient.ui.widgets.AppTopBar

/**
 * 剧情冒险首页。
 *
 * 上半是「继续冒险」：已有存档按进度排列；
 * 下半是「新的冒险」：可开局的剧本。
 * 编辑剧本的入口放在顶部，属调试用途，正常玩不需要进来。
 */
@Composable
fun ScriptAdventureLayout(
    uiState: ScriptAdventureUiState,
    emit: ScriptAdventureUiIntent.() -> Unit,
    onBack: () -> Unit
) {
    val state = uiState as? ScriptAdventureUiState.Ready ?: return
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.adventure_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { ScriptAdventureUiIntent.OpenLibrary.emit() }) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.adventure_open_library)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.saves.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.adventure_continue))
                }
                items(state.saves, key = { it.playId }) { save ->
                    SaveCard(
                        save = save,
                        onContinue = {
                            ScriptAdventureUiIntent.Continue(save.playId, save.packId).emit()
                        },
                        onDelete = { ScriptAdventureUiIntent.DeleteSave(save.playId).emit() }
                    )
                }
            }
            if (state.packs.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.adventure_new))
                }
                items(state.packs, key = { it.packId }) { pack ->
                    PackCard(
                    pack = pack,
                    onStart = { ScriptAdventureUiIntent.StartNew(pack.packId).emit() },
                    onPickCast = {
                        ScriptAdventureUiIntent.OpenCharacterPicker(pack.packId).emit()
                    }
                )
                }
            }
            if (state.isEmpty) {
                item {
                    Text(
                        text = stringResource(R.string.adventure_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (state.pickingForPackId != null) {
        CharacterPickerDialog(state, emit)
    }
}

/** 分组标题。 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

/** 存档卡片：显示剧本名、进度与状态。 */
@Composable
private fun SaveCard(
    save: AdventureSaveItem,
    onContinue: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onContinue),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = save.packName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (save.finished) {
                        save.endReason.ifBlank { stringResource(R.string.adventure_finished) }
                    } else {
                        stringResource(R.string.adventure_progress, save.roundCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (save.finished) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.adventure_delete_save),
                    modifier = Modifier.size(18.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 剧本卡片：点一下开新局。 */
@Composable
private fun PackCard(
    pack: AdventurePackItem,
    onStart: () -> Unit,
    onPickCast: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // 点一下直接开局；想指定在场角色时长按，别把选择器做成必经步骤
            .combinedClickable(onClick = onStart, onLongClick = onPickCast),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    text = pack.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.script_pack_meta, pack.modeLabel, pack.sceneCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 开局前挑选在场角色；不选则按剧本自带的名单。 */
@Composable
private fun CharacterPickerDialog(
    state: ScriptAdventureUiState.Ready,
    emit: ScriptAdventureUiIntent.() -> Unit
) {
    val packId = state.pickingForPackId ?: return
    AlertDialog(
        onDismissRequest = { ScriptAdventureUiIntent.DismissCharacterPicker.emit() },
        title = { Text(stringResource(R.string.adventure_pick_cast)) },
        // 明确说明可以不选：剧本自带的名单已经够用

        text = {
            if (state.characters.isEmpty()) {
                Text(stringResource(R.string.adventure_no_characters))
            } else {
                LazyColumn {
                    items(state.characters) { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { ScriptAdventureUiIntent.ToggleCharacter(name).emit() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = name in state.selectedCharacters,
                                onCheckedChange = {
                                    ScriptAdventureUiIntent.ToggleCharacter(name).emit()
                                }
                            )
                            Text(text = name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ScriptAdventureUiIntent.StartNew(packId, state.selectedCharacters.toList()).emit()
            }) {
                Text(stringResource(R.string.adventure_start))
            }
        },
        dismissButton = {
            TextButton(onClick = { ScriptAdventureUiIntent.DismissCharacterPicker.emit() }) {
                Text(stringResource(R.string.script_picker_done))
            }
        }
    )
}
