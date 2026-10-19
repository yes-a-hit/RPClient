package me.kafuuneko.rpclient.feature.script.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryItem
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryTab
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryUiIntent
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryUiState
import me.kafuuneko.rpclient.feature.script.presentation.ScriptSceneItem
import me.kafuuneko.rpclient.ui.widgets.AppTopBar

/**
 * 剧本库：剧本与场景两个标签。
 *
 * 剧本标签按作品聚合，点进去编排流程；场景标签把各剧本的场景摊平，
 * 用于跨剧本检查入口分布与命名，两个标签共用同一份磁盘数据。
 */
@Composable
fun ScriptLibraryLayout(
    uiState: ScriptLibraryUiState,
    emit: ScriptLibraryUiIntent.() -> Unit,
    onBack: () -> Unit
) {
    val state = uiState as? ScriptLibraryUiState.Ready ?: return

    // 长按管理的目标：重命名与删除各自一个，互不干扰
    var renameTarget by remember { mutableStateOf<ScriptLibraryItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ScriptLibraryItem?>(null) }
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.script_library_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { ScriptLibraryUiIntent.CreatePack.emit() }) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.script_create_action)
                        )
                    }
                    IconButton(onClick = { ScriptLibraryUiIntent.ImportClick.emit() }) {
                        Icon(
                            Icons.Rounded.FileOpen,
                            contentDescription = stringResource(R.string.script_import_action)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == ScriptLibraryTab.Pack,
                    onClick = { ScriptLibraryUiIntent.SwitchTab(ScriptLibraryTab.Pack).emit() },
                    text = { Text(stringResource(R.string.script_tab_pack)) }
                )
                Tab(
                    selected = state.tab == ScriptLibraryTab.Scene,
                    onClick = { ScriptLibraryUiIntent.SwitchTab(ScriptLibraryTab.Scene).emit() },
                    text = { Text(stringResource(R.string.script_tab_scene)) }
                )
            }
            if (state.isEmpty) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.script_library_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.tab == ScriptLibraryTab.Pack) {
                        // 新建入口放在列表首位：进库第一眼就能开新剧本，而不是翻找右上角
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { ScriptLibraryUiIntent.CreatePack.emit() },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.script_create_action),
                                        modifier = Modifier.padding(start = 10.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        items(state.packs, key = { it.packId }) { pack ->
                            PackCard(
                                pack = pack,
                                onOpen = { ScriptLibraryUiIntent.OpenPack(pack.packId).emit() },
                                onPlay = { ScriptLibraryUiIntent.PlayPack(pack.packId).emit() },
                                onRename = { renameTarget = pack },
                                onDelete = { deleteTarget = pack }
                            )
                        }
                    } else {
                        items(state.scenes, key = { "${it.packId}/${it.sceneId}" }) { scene ->
                            SceneCard(scene) {
                                ScriptLibraryUiIntent.OpenPack(scene.packId).emit()
                            }
                        }
                    }
                }
            }
        }
    }
    renameTarget?.let { target ->
        RenamePackDialog(
            pack = target,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                ScriptLibraryUiIntent.RenamePack(target.packId, name).emit()
                renameTarget = null
            }
        )
    }
    deleteTarget?.let { target ->
        DeletePackDialog(
            pack = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                ScriptLibraryUiIntent.DeletePack(target.packId).emit()
                deleteTarget = null
            }
        )
    }
}

/** 重命名对话框：本地输入，确认后提交。 */
@Composable
private fun RenamePackDialog(
    pack: ScriptLibraryItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(pack.packId) { mutableStateOf(pack.packName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.script_pack_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text(stringResource(R.string.script_scene_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.script_picker_done))
            }
        }
    )
}

/** 删除确认对话框。 */
@Composable
private fun DeletePackDialog(
    pack: ScriptLibraryItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.script_pack_delete)) },
        text = { Text(pack.packName) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.script_pack_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.script_picker_done))
            }
        }
    )
}

/** 剧本卡片：名称、模式与场景数；短按进编排，长按管理。 */
@Composable
private fun PackCard(
    pack: ScriptLibraryItem,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    // 长按弹出重命名与删除，避免在卡片上塞满图标
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menuOpen = true }
                ),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 1.dp
        ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pack.packName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.script_pack_meta, pack.modeLabel, pack.sceneCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.script_play_start),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.script_pack_rename)) },
                onClick = {
                    menuOpen = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.script_pack_delete)) },
                onClick = {
                    menuOpen = false
                    onDelete()
                }
            )
        }
    }
}

/** 场景卡片：场景名、所属剧本与入口标记。 */
@Composable
private fun SceneCard(scene: ScriptSceneItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = scene.sceneName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (scene.isEntry) {
                        Text(
                            text = stringResource(R.string.script_row_entry),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.script_scene_meta, scene.packName, scene.sceneId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
