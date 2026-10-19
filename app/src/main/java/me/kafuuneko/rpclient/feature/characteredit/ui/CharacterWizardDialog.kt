package me.kafuuneko.rpclient.feature.characteredit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterProviderItem
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterWizardStep
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterWizardUiState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiIntent
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone

/**
 * 角色卡生成向导弹窗。
 *
 * 流程分为输入与预览两步：输入阶段收集少量种子信息（可附参考图），
 * 预览阶段展示模型产出的完整草稿，用户确认后才合并进角色编辑表单。
 * 组件本身不持有业务状态，全部通过 [state] 渲染并回传 Intent。
 *
 * @param state 向导状态
 * @param providers 可供选择的模型配置列表，用于指定识图专用模型
 * @param onDismiss 关闭向导的回调
 * @param emit 发送编辑意图
 */
@Composable
fun CharacterWizardDialog(
    state: CharacterWizardUiState,
    providers: List<CharacterProviderItem>,
    onDismiss: () -> Unit,
    emit: CharacterEditUiIntent.() -> Unit
) {
    if (!state.visible) return
    val isPreview = state.step == CharacterWizardStep.Preview
    AppDialogScaffold(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.character_wizard_title),
        badgeIcon = Icons.Rounded.AutoAwesome,
        badgeTone = DialogBadgeTone.Primary,
        subtitle = stringResource(
            if (isPreview) {
                R.string.character_wizard_preview_subtitle
            } else {
                R.string.character_wizard_input_subtitle
            }
        ),
        confirmText = stringResource(
            if (isPreview) R.string.character_wizard_apply else R.string.character_wizard_generate
        ),
        confirmEnabled = if (isPreview) state.draft != null else state.canGenerate,
        isConfirmLoading = state.generating,
        onConfirm = {
            if (isPreview) {
                CharacterEditUiIntent.ApplyCharacterDraft.emit()
            } else {
                CharacterEditUiIntent.GenerateCharacterDraft.emit()
            }
        },
        onDismiss = onDismiss,
        scrollableContent = true
    ) {
        if (isPreview) {
            WizardPreviewContent(state, emit)
        } else {
            WizardInputContent(state, providers, emit)
        }
        // 失败提示直接跟随内容展示，避免额外弹出 Toast 打断输入
        state.errorResId?.let { errorRes ->
            Text(
                text = stringResource(errorRes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** 输入步骤：角色名、概念、额外要求、参考图、识图模型与世界书开关。 */
@Composable
private fun WizardInputContent(
    state: CharacterWizardUiState,
    providers: List<CharacterProviderItem>,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = state.seedName,
            onValueChange = { CharacterEditUiIntent.ChangeWizardName(it).emit() },
            label = { Text(stringResource(R.string.character_wizard_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.seedConcept,
            onValueChange = { CharacterEditUiIntent.ChangeWizardConcept(it).emit() },
            label = { Text(stringResource(R.string.character_wizard_concept)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.seedRequirements,
            onValueChange = { CharacterEditUiIntent.ChangeWizardRequirements(it).emit() },
            label = { Text(stringResource(R.string.character_wizard_requirements)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        // 参考图区域：未选择时展示选择按钮，已选择时展示预览与清除入口
        val referenceImage = state.referenceImage
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (referenceImage != null) {
                Image(
                    bitmap = referenceImage,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                TextButton(
                    onClick = { CharacterEditUiIntent.ClearWizardReference.emit() }
                ) {
                    Text(stringResource(R.string.character_wizard_clear_reference))
                }
            } else {
                TextButton(
                    onClick = { CharacterEditUiIntent.PickWizardReferenceImage.emit() }
                ) {
                    Text(stringResource(R.string.character_wizard_pick_reference))
                }
            }
            Text(
                text = stringResource(R.string.character_wizard_reference_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        // 识图模型独立选择：图像理解精度与对话文风往往由不同模型各擅胜场
        WizardProviderSelector(
            selectedProviderId = state.wizardProviderId,
            providers = providers,
            onSelect = { CharacterEditUiIntent.SelectWizardProvider(it).emit() }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.character_wizard_include_lorebook),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.includeLorebook,
                onCheckedChange = {
                    CharacterEditUiIntent.ToggleWizardLorebook(it).emit()
                }
            )
        }
    }
}

/** 识图模型下拉选择器；选中 0 表示跟随全局模型。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardProviderSelector(
    selectedProviderId: Long,
    providers: List<CharacterProviderItem>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = providers.firstOrNull { it.id == selectedProviderId }
    val displayName = selected?.let { "${it.name} · ${it.model}" }
        ?: stringResource(R.string.follow_global_model)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.character_wizard_vision_model)) },
            supportingText = {
                Text(stringResource(R.string.character_wizard_vision_model_helper))
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.follow_global_model)) },
                onClick = {
                    onSelect(0L)
                    expanded = false
                }
            )
            // 仅列出已启用的配置，禁用项无法真正发起请求
            providers.filter { it.isEnabled }.forEach { provider ->
                DropdownMenuItem(
                    text = { Text("${provider.name} · ${provider.model}") },
                    onClick = {
                        onSelect(provider.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 预览步骤：按字段展示草稿摘要，并提供重新生成入口。 */
@Composable
private fun WizardPreviewContent(
    state: CharacterWizardUiState,
    emit: CharacterEditUiIntent.() -> Unit
) {
    val draft = state.draft
    if (draft == null) {
        Text(
            text = stringResource(R.string.character_wizard_empty_draft),
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WizardPreviewField(R.string.name, draft.name)
        WizardPreviewField(R.string.character_description, draft.description)
        WizardPreviewField(R.string.character_personality, draft.personality)
        WizardPreviewField(R.string.character_scenario, draft.scenario)
        WizardPreviewField(R.string.character_first_messages, draft.firstMessage)
        if (draft.tags.isNotEmpty()) {
            WizardPreviewField(
                R.string.character_tags,
                draft.tags.joinToString("、")
            )
        }
        // 感情经历只在草稿确实填写时展示，避免空白项占据版面
        if (draft.profile.hasContent) {
            val profileLines = buildList {
                if (draft.profile.romanticHistory.isNotBlank()) add(draft.profile.romanticHistory)
                if (draft.profile.boundaries.isNotBlank()) add(draft.profile.boundaries)
            }
            if (profileLines.isNotEmpty()) {
                WizardPreviewField(
                    R.string.character_backstory_section,
                    profileLines.joinToString("\n")
                )
            }
        }
        draft.lorebook?.let { lorebook ->
            // 名称留空时展示统一的未知世界书占位，避免拼出空标题
            val lorebookName = lorebook.name.ifBlank {
                stringResource(R.string.unknown_lorebook)
            }
            WizardPreviewField(
                R.string.character_wizard_field_lorebook,
                stringResource(
                    R.string.character_wizard_lorebook_summary,
                    lorebookName,
                    lorebook.entries.size
                )
            )
        }
        TextButton(
            onClick = {
                // 回到输入步骤以便调整种子后重新生成
                CharacterEditUiIntent.CloseCharacterWizard.emit()
                CharacterEditUiIntent.OpenCharacterWizard.emit()
            }
        ) {
            Text(stringResource(R.string.character_wizard_regenerate))
        }
    }
}

/** 预览中的单项字段：标题加限高正文。 */
@Composable
private fun WizardPreviewField(@StringRes titleRes: Int, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}
