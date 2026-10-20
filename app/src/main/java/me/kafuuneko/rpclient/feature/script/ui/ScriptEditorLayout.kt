package me.kafuuneko.rpclient.feature.script.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import me.kafuuneko.rpclient.feature.script.presentation.ScriptEditorUiIntent
import me.kafuuneko.rpclient.feature.script.presentation.ScriptEditorUiState
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLinkField
import me.kafuuneko.rpclient.feature.script.presentation.ScriptSceneRow
import me.kafuuneko.rpclient.ui.widgets.AppTopBar

/**
 * 剧本编辑器。
 *
 * 按章节纵向铺开：每章一张卡片，内容、结局条件、在场角色、背景都在卡内直接改，
 * 不用再开弹窗。次级设置收在「更多」里，免得一屏塞不下。
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
                title = state.packName.ifBlank { stringResource(R.string.script_editor_title) },
                onBack = onBack,
                actions = {
                    IconButton(onClick = { ScriptEditorUiIntent.AddScene.emit() }) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.script_add_scene)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PackInfoCard(state = state, emit = emit)
            }
            itemsIndexed(state.rows) { index, row ->
                ChapterCard(
                    index = index,
                    row = row,
                    generating = state.generating,
                    characterNames = state.characterNames,
                    lorebookNames = state.lorebookNames,
                    galleryKeys = state.galleryKeys,
                    allScenes = state.rows,
                    emit = emit
                )
            }
            item {
                TextButton(
                    onClick = { ScriptEditorUiIntent.AddScene.emit() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.script_add_scene))
                }
            }
        }
    }

    val editingRow = state.editingRow
    val editingField = state.editingField
    if (editingRow != null && editingField != null) {
        LinkPickerDialog(
            row = editingRow,
            field = editingField,
            allScenes = state.rows,
            emit = emit
        )
    }
}

/**
 * 单章卡片。
 *
 * 默认展开内容与结局条件，其余设置收在「更多」里，
 * 免得每章都要滚很久才能看到下一章。
 */
@Composable
private fun ChapterCard(
    index: Int,
    row: ScriptSceneRow,
    generating: String,
    characterNames: List<String>,
    lorebookNames: List<String>,
    galleryKeys: List<String>,
    allScenes: List<ScriptSceneRow>,
    emit: ScriptEditorUiIntent.() -> Unit
) {
    var expanded by remember(row.id) { mutableStateOf(false) }
    var name by remember(row.id) { mutableStateOf(row.name) }
    var content by remember(row.id) { mutableStateOf(row.entryPrompt) }
    var completion by remember(row.id) { mutableStateOf(row.completionKeyword) }
    var failKeyword by remember(row.id) { mutableStateOf(row.failKeyword) }
    var failRounds by remember(row.id) { mutableStateOf(row.failRounds.toString()) }
    var rounds by remember(row.id) { mutableStateOf(row.maxRounds.toString()) }
    var background by remember(row.id) { mutableStateOf(row.backgroundKey) }
    var castNames by remember(row.id) { mutableStateOf(row.castNames) }
    var bannedNames by remember(row.id) { mutableStateOf(row.bannedNames) }
    var lorebookName by remember(row.id) { mutableStateOf(row.lorebookName) }
    var nextScenes by remember(row.id) { mutableStateOf(row.nextSceneIds) }
    var checkpoint by remember(row.id) { mutableStateOf(row.checkpoint) }
    var loop by remember(row.id) { mutableStateOf(row.loopEnabled) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 ${index + 1} 章",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (row.isEntry) {
                    Text(
                        text = " · " + stringResource(R.string.script_scene_entry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Box(modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { ScriptEditorUiIntent.DeleteScene(row.id).emit() }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.script_scene_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.script_scene_prompt),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                maxLines = 6
            )
            Text(
                text = stringResource(R.string.script_scene_end_condition),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = completion,
                onValueChange = { completion = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                singleLine = true
            )
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rounds,
                        onValueChange = { rounds = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.script_scene_rounds)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SelectorSection(
                        title = stringResource(R.string.script_scene_background),
                        options = galleryKeys,
                        selected = background.toNameSet(),
                        onToggle = { key -> background = if (background == key) "" else key }
                    )
                    SelectorSection(
                        title = stringResource(R.string.script_scene_cast),
                        options = characterNames,
                        selected = castNames.toNameSet(),
                        onToggle = { pick -> castNames = castNames.toggleName(pick) }
                    )
                    SelectorSection(
                        title = stringResource(R.string.script_scene_banned),
                        options = characterNames,
                        selected = bannedNames.toNameSet(),
                        onToggle = { pick -> bannedNames = bannedNames.toggleName(pick) }
                    )
                    SelectorSection(
                        title = stringResource(R.string.script_scene_lorebook),
                        options = lorebookNames,
                        selected = lorebookName.toNameSet(),
                        onToggle = { pick -> lorebookName = if (lorebookName == pick) "" else pick }
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
                    SelectorSection(
                        title = stringResource(R.string.script_scene_next),
                        options = allScenes.filter { it.id != row.id }.map { it.name },
                        selected = nextScenes.toNameSet(),
                        onToggle = { pick -> nextScenes = nextScenes.toggleName(pick) }
                    )
                    SwitchRow(stringResource(R.string.script_row_checkpoint), checkpoint) {
                        checkpoint = it
                    }
                    SwitchRow(stringResource(R.string.script_scene_loop), loop) { loop = it }
                }
            }
            OutlinedButton(
                onClick = {
                    ScriptEditorUiIntent.GenerateSceneContent(row.id).emit()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = generating.isBlank()
            ) {
                Text(
                    if (generating == row.id) {
                        stringResource(R.string.script_auto_filling)
                    } else {
                        stringResource(R.string.script_auto_fill_scene)
                    }
                )
            }
            TextButton(
                onClick = {
                    ScriptEditorUiIntent.UpdateScene(
                        sceneId = row.id,
                        name = name,
                        maxRounds = rounds.toIntOrNull() ?: 0,
                        entryPrompt = content,
                        checkpoint = checkpoint,
                        loopEnabled = loop,
                        isEntry = row.isEntry,
                        backgroundKey = background,
                        castNames = castNames,
                        bannedNames = bannedNames,
                        lorebookName = lorebookName,
                        failKeyword = failKeyword,
                        failRounds = failRounds.toIntOrNull() ?: 0,
                        completionKeyword = completion,
                        nextSceneIds = nextScenes
                    ).emit()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.script_scene_save))
            }
        }
    }
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

/** 一行开关。 */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 后继场景选择：列出其它场景，点选即建立链接。 */
@Composable
private fun LinkPickerDialog(
    row: ScriptSceneRow,
    field: ScriptLinkField,
    allScenes: List<ScriptSceneRow>,
    emit: ScriptEditorUiIntent.() -> Unit
) {
    val candidates = allScenes.filter { it.id != row.id }
    AlertDialog(
        onDismissRequest = { ScriptEditorUiIntent.CloseLinkPanel.emit() },
        title = {
            Text(
                when (field) {
                    ScriptLinkField.Next -> stringResource(R.string.script_scene_next)
                    ScriptLinkField.Unlock -> stringResource(R.string.script_scene_unlock)
                }
            )
        },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.script_scene_no_options))
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    candidates.forEach { scene ->
                        Text(
                            text = scene.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ScriptEditorUiIntent.ToggleLink(row.id, field, scene.id).emit()
                                }
                                .padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { ScriptEditorUiIntent.CloseLinkPanel.emit() }) {
                Text(stringResource(R.string.script_scene_save))
            }
        }
    )
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

/**
 * 剧本信息卡。
 *
 * 梗概同时充当全局背景：剧情里跨章节统一的设定写这里，
 * 具体情节留给各章节。开场白按「发言者|内容」每行一条，
 * 不留发言者即旁白。
 */
@Composable
private fun PackInfoCard(
    state: ScriptEditorUiState.Ready,
    emit: ScriptEditorUiIntent.() -> Unit
) {
    var name by remember(state.packName) { mutableStateOf(state.packName) }
    var description by remember(state.description) { mutableStateOf(state.description) }
    var openings by remember(state.openingsText) { mutableStateOf(state.openingsText) }
    var showCompletion by remember(state.showCompletion) { mutableStateOf(state.showCompletion) }
    var shareSettings by remember(state.shareSettings) { mutableStateOf(state.shareSettings) }
    var expanded by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.script_pack_info),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Box(modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (expanded) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.script_pack_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.script_pack_description),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    maxLines = 4
                )
                Text(
                    text = stringResource(R.string.script_pack_openings),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = openings,
                    onValueChange = { openings = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    maxLines = 5
                )
                OutlinedButton(
                    onClick = { ScriptEditorUiIntent.GeneratePackInfo.emit() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.generating.isBlank()
                ) {
                    Text(
                        if (state.generating == "pack") {
                            stringResource(R.string.script_auto_filling)
                        } else {
                            stringResource(R.string.script_auto_fill_pack)
                        }
                    )
                }
                SwitchRow(
                    stringResource(R.string.script_pack_show_completion),
                    showCompletion
                ) { showCompletion = it }
                SwitchRow(
                    stringResource(R.string.script_pack_share),
                    shareSettings
                ) { shareSettings = it }
                TextButton(
                    onClick = {
                        ScriptEditorUiIntent.UpdatePackInfo(
                            name = name,
                            description = description,
                            openingsText = openings,
                            showCompletion = showCompletion,
                            shareSettings = shareSettings
                        ).emit()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.script_scene_save))
                }
            }
        }
    }
}
