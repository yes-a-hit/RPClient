package me.kafuuneko.rpclient.feature.script.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Save
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.script.presentation.ScriptEditorUiIntent
import me.kafuuneko.rpclient.feature.script.presentation.ScriptEditorUiState
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLinkField
import me.kafuuneko.rpclient.feature.script.presentation.ScriptSceneRow
import me.kafuuneko.rpclient.ui.widgets.AppTopBar

/**
 * 剧本表格编辑器。
 *
 * 每个场景一张卡片，行内列出后继与解锁前置；点击对应行打开勾选面板，
 * 勾选即建立链接、取消勾选即断开。全部关系都是可见的列表，
 * 不依赖拖拽，也不要求用户理解节点图的布局。
 */
@Composable
fun ScriptEditorLayout(
    uiState: ScriptEditorUiState,
    emit: ScriptEditorUiIntent.() -> Unit,
    onBack: () -> Unit
) {
    val state = uiState as? ScriptEditorUiState.Ready ?: return
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.script_editor_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { ScriptEditorUiIntent.AddScene.emit() }) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.script_add_scene_action)
                        )
                    }
                    IconButton(onClick = { ScriptEditorUiIntent.ImportClick.emit() }) {
                        Icon(
                            Icons.Rounded.FileOpen,
                            contentDescription = stringResource(R.string.script_import_action)
                        )
                    }
                    IconButton(onClick = { ScriptEditorUiIntent.ExportLayout.emit() }) {
                        Icon(
                            Icons.Rounded.Save,
                            contentDescription = stringResource(R.string.script_export_done)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.script_editor_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.rows, key = { it.id }) { row ->
                    SceneRowCard(state, row, emit)
                }
            }
        }
    }

    // 场景设置面板与链接面板互斥，打开其一时另一个不会出现
    val settingsRow = state.settingsRow
    if (settingsRow != null) {
        SceneSettingsDialog(
            row = settingsRow,
            characterNames = state.characterNames,
            lorebookNames = state.lorebookNames,
            galleryKeys = state.galleryKeys,
            allScenes = state.rows,
            emit = emit
        )
    }

    val editingRow = state.editingRow
    val editingField = state.editingField
    if (editingRow != null && editingField != null) {
        LinkPickerDialog(
            state = state,
            row = editingRow,
            field = editingField,
            emit = emit
        )
    }
}

/** 单个场景的表格行。 */
@Composable
private fun SceneRowCard(
    state: ScriptEditorUiState.Ready,
    row: ScriptSceneRow,
    emit: ScriptEditorUiIntent.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (row.isEntry) {
                    Text(
                        text = stringResource(R.string.script_row_entry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (row.checkpoint) {
                    Text(
                        text = stringResource(R.string.script_row_checkpoint),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(
                    onClick = { ScriptEditorUiIntent.EditScene(row.id).emit() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.script_scene_settings),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.script_row_id, row.id),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinkRow(
                label = stringResource(R.string.script_row_next),
                values = row.nextIds.map(state::nameOf),
                emptyText = stringResource(R.string.script_row_none),
                onClick = { ScriptEditorUiIntent.EditLinks(row.id, ScriptLinkField.Next).emit() }
            )
            LinkRow(
                label = stringResource(R.string.script_row_unlock),
                values = row.unlockIds.map(state::nameOf),
                emptyText = stringResource(R.string.script_row_none),
                onClick = { ScriptEditorUiIntent.EditLinks(row.id, ScriptLinkField.Unlock).emit() }
            )
        }
    }
}

/** 一行链接：左侧标签，右侧已选目标，点击进入选择。 */
@Composable
private fun LinkRow(
    label: String,
    values: List<String>,
    emptyText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(end = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (values.isEmpty()) emptyText else values.joinToString("、"),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (values.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 链接选择面板：勾选即建立关系。 */
@Composable
private fun LinkPickerDialog(
    state: ScriptEditorUiState.Ready,
    row: ScriptSceneRow,
    field: ScriptLinkField,
    emit: ScriptEditorUiIntent.() -> Unit
) {
    val selected = when (field) {
        ScriptLinkField.Next -> row.nextIds
        ScriptLinkField.Unlock -> row.unlockIds
    }
    val title = when (field) {
        ScriptLinkField.Next -> R.string.script_picker_next_title
        ScriptLinkField.Unlock -> R.string.script_picker_unlock_title
    }
    AlertDialog(
        onDismissRequest = { ScriptEditorUiIntent.CloseLinkPanel.emit() },
        title = { Text(stringResource(title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                state.rows.filter { it.id != row.id }.forEach { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ScriptEditorUiIntent.ToggleLink(row.id, field, candidate.id).emit()
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = candidate.id in selected,
                            onCheckedChange = {
                                ScriptEditorUiIntent.ToggleLink(row.id, field, candidate.id).emit()
                            }
                        )
                        Text(
                            text = candidate.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { ScriptEditorUiIntent.CloseLinkPanel.emit() }) {
                Text(stringResource(R.string.script_picker_done))
            }
        }
    )
}

@Composable
private fun SceneSettingsDialog(
    row: ScriptSceneRow,
    characterNames: List<String>,
    lorebookNames: List<String>,
    galleryKeys: List<String>,
    allScenes: List<ScriptSceneRow>,
    emit: ScriptEditorUiIntent.() -> Unit
) {
    var name by remember(row.id) { mutableStateOf(row.name) }
    var rounds by remember(row.id) { mutableStateOf(row.maxRounds.toString()) }
    var prompt by remember(row.id) { mutableStateOf(row.entryPrompt) }
    var checkpoint by remember(row.id) { mutableStateOf(row.checkpoint) }
    var loop by remember(row.id) { mutableStateOf(row.loopEnabled) }
    var isEntry by remember(row.id) { mutableStateOf(row.isEntry) }
    var background by remember(row.id) { mutableStateOf(row.backgroundKey) }
    var castNames by remember(row.id) { mutableStateOf(row.castNames) }
    var bannedNames by remember(row.id) { mutableStateOf(row.bannedNames) }
    var lorebookName by remember(row.id) { mutableStateOf(row.lorebookName) }
    var failKeyword by remember(row.id) { mutableStateOf(row.failKeyword) }
    var failRounds by remember(row.id) { mutableStateOf(row.failRounds.toString()) }
    var completionKeyword by remember(row.id) { mutableStateOf(row.completionKeyword) }
    var nextScenes by remember(row.id) { mutableStateOf(row.nextSceneIds) }

    // 勾选式编辑：把已选的展示串拆成集合，勾一下再拼回去
    val selectedCast = remember(row.id, castNames) { castNames.toNameSet() }
    val selectedBanned = remember(row.id, bannedNames) { bannedNames.toNameSet() }
    val selectedNext = remember(row.id, nextScenes) { nextScenes.toNameSet() }

    AlertDialog(
        onDismissRequest = { ScriptEditorUiIntent.CloseSceneEditor.emit() },
        title = { Text(stringResource(R.string.script_scene_settings)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.script_scene_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rounds,
                    onValueChange = { rounds = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.script_scene_rounds)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.script_scene_prompt)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // 背景：从图库已有标签里点选，点同一个再取消
                SelectorSection(
                    title = stringResource(R.string.script_scene_background),
                    options = galleryKeys,
                    selected = background.toNameSet(),
                    onToggle = { key ->
                        background = if (background == key) "" else key
                    }
                )

                // 可出场角色：从角色卡勾选
                SelectorSection(
                    title = stringResource(R.string.script_scene_cast),
                    options = characterNames,
                    selected = selectedCast,
                    onToggle = { name2 ->
                        castNames = castNames.toggleName(name2)
                    }
                )

                // 禁止出场角色
                SelectorSection(
                    title = stringResource(R.string.script_scene_banned),
                    options = characterNames,
                    selected = selectedBanned,
                    onToggle = { name2 ->
                        bannedNames = bannedNames.toggleName(name2)
                    }
                )

                // 世界书：单选
                SelectorSection(
                    title = stringResource(R.string.script_scene_lorebook),
                    options = lorebookNames,
                    selected = lorebookName.toNameSet(),
                    onToggle = { picked ->
                        lorebookName = if (lorebookName == picked) "" else picked
                    }
                )

                OutlinedTextField(
                    value = failKeyword,
                    onValueChange = { failKeyword = it },
                    label = { Text(stringResource(R.string.script_scene_fail_keyword)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = failRounds,
                    onValueChange = { failRounds = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.script_scene_fail_rounds)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = completionKeyword,
                    onValueChange = { completionKeyword = it },
                    label = { Text(stringResource(R.string.script_scene_completion)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 后继场景：从其它场景里勾
                SelectorSection(
                    title = stringResource(R.string.script_scene_next),
                    options = allScenes.filter { it.id != row.id }.map { it.name },
                    selected = selectedNext,
                    onToggle = { target ->
                        nextScenes = nextScenes.toggleName(target)
                    }
                )

                SwitchRow(stringResource(R.string.script_row_checkpoint), checkpoint) { checkpoint = it }
                SwitchRow(stringResource(R.string.script_scene_loop), loop) { loop = it }
                SwitchRow(stringResource(R.string.script_scene_entry), isEntry) { isEntry = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ScriptEditorUiIntent.UpdateScene(
                    sceneId = row.id,
                    name = name,
                    maxRounds = rounds.toIntOrNull() ?: 0,
                    entryPrompt = prompt,
                    checkpoint = checkpoint,
                    loopEnabled = loop,
                    isEntry = isEntry,
                    backgroundKey = background,
                    castNames = castNames,
                    bannedNames = bannedNames,
                    lorebookName = lorebookName,
                    failKeyword = failKeyword,
                    failRounds = failRounds.toIntOrNull() ?: 0,
                    completionKeyword = completionKeyword,
                    nextSceneIds = nextScenes
                ).emit()
            }) { Text(stringResource(R.string.script_scene_save)) }
        },
        dismissButton = {
            TextButton(onClick = { ScriptEditorUiIntent.DeleteScene(row.id).emit() }) {
                Text(stringResource(R.string.script_scene_delete))
            }
        }
    )
}

/** 一组可勾选的候选，勾选态用主色底标出。 */
@Composable
private fun SelectorSection(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (options.isEmpty()) {
            Text(
                text = stringResource(R.string.script_scene_no_options),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                options.forEach { option ->
                    val picked = option in selected
                    Text(
                        text = option,
                        modifier = Modifier
                            .clickable { onToggle(option) }
                            .background(
                                color = if (picked) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/** 把顿号分隔的展示串拆成集合。 */
private fun String.toNameSet(): Set<String> = this
    .split('、', ',', '，')
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .toSet()

/** 勾选或取消一个名字，返回重新拼好的展示串。 */
private fun String.toggleName(name: String): String {
    val current = toNameSet().toMutableSet()
    if (!current.add(name)) current.remove(name)
    return current.joinToString("、")
}

/** 一行开关。 */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
