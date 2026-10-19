package me.kafuuneko.rpclient.feature.main.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.presentation.MainDialogState
import me.kafuuneko.rpclient.feature.main.presentation.MainSkillItem
import me.kafuuneko.rpclient.feature.main.presentation.MainSkillSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup

/**
 * 助手 Skill 管理面板。
 *
 * 展示已安装能力包并支持启停与删除；导入入口放在面板底部，
 * 点击后弹出 JSON 粘贴对话框，避免在设置页内嵌套多层导航。
 *
 * @param state Skill 面板状态
 * @param emit 发送设置意图
 */
@Composable
internal fun SkillPanel(state: MainSkillSettingsState, emit: MainUiIntent.() -> Unit) {
    RpSettingsGroup {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.skill_settings_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.skill_settings_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.skill_settings_summary,
                    state.enabledCount,
                    state.skills.size
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.skills.isEmpty()) {
                Text(
                    text = stringResource(R.string.skill_settings_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.skills.forEach { skill ->
                SkillRow(skill, emit)
            }
            // 文件与目录导入走系统选择器，手写粘贴保留给快速改单条的场景
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { MainUiIntent.PickSkillFiles.emit() }) {
                    Text(stringResource(R.string.skill_import_from_file))
                }
                TextButton(onClick = { MainUiIntent.PickSkillFolder.emit() }) {
                    Text(stringResource(R.string.skill_import_from_folder))
                }
                TextButton(onClick = { MainUiIntent.OpenSkillImport.emit() }) {
                    Text(stringResource(R.string.skill_import_action))
                }
            }
        }
    }
}

/** 单个 Skill 行：名称、触发词摘要、启停开关与删除入口。 */
@Composable
private fun SkillRow(skill: MainSkillItem, emit: MainUiIntent.() -> Unit) {
    // 常驻标记需要本地化，必须在 Composable 上下文预先取好，供字符串拼接使用
    val alwaysOnLabel = stringResource(R.string.skill_always_on)
    val triggerText = if (skill.alwaysOn) alwaysOnLabel else skill.triggerSummary
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skill.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = listOf(skill.description, triggerText)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(onClick = { MainUiIntent.DeleteSkill(skill.id).emit() }) {
            Text(stringResource(R.string.delete))
        }
        Switch(
            checked = skill.enabled,
            onCheckedChange = { MainUiIntent.ToggleSkill(skill.id, it).emit() }
        )
    }
}

/**
 * Skill 导入对话框。
 *
 * 用户粘贴一个或多个 Skill 的 JSON（可带 Markdown 围栏）后提交；
 * 解析失败时保留草稿并在对话框内提示，便于就地修正。
 *
 * @param state 导入对话框状态
 * @param emit 发送设置意图
 */
@Composable
internal fun SkillImportDialog(
    state: MainDialogState.ImportSkill,
    emit: MainUiIntent.() -> Unit
) {
    AppDialogScaffold(
        onDismissRequest = { MainUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.skill_import_title),
        badgeIcon = Icons.Rounded.Extension,
        badgeTone = DialogBadgeTone.Primary,
        subtitle = stringResource(R.string.skill_import_description),
        confirmText = stringResource(R.string.skill_import_confirm),
        confirmEnabled = state.draftText.isNotBlank(),
        isConfirmLoading = state.isImporting,
        onConfirm = { MainUiIntent.ConfirmSkillImport.emit() },
        onDismiss = { MainUiIntent.DismissDialog.emit() },
        scrollableContent = true
    ) {
        OutlinedTextField(
            value = state.draftText,
            onValueChange = { MainUiIntent.ChangeSkillImportDraft(it).emit() },
            label = { Text(stringResource(R.string.skill_import_field)) },
            placeholder = { Text(stringResource(R.string.skill_import_hint)) },
            minLines = 6,
            maxLines = 12,
            enabled = !state.isImporting,
            modifier = Modifier.fillMaxWidth()
        )
        state.errorResId?.let { errorRes ->
            Text(
                text = stringResource(errorRes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
