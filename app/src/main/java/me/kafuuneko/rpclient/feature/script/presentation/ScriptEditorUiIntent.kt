package me.kafuuneko.rpclient.feature.script.presentation

/** 剧本编辑页的用户意图。 */
sealed class ScriptEditorUiIntent {
    /** 首次进入，加载剧本。 */
    data object Init : ScriptEditorUiIntent()

    /** 打开指定已保存剧本；进入时由宿主传入。 */
    data class OpenPack(val packId: String) : ScriptEditorUiIntent()

    /** 打开文件选择器导入剧本。 */
    data object ImportClick : ScriptEditorUiIntent()

    /** 用户选中了剧本文件。 */
    data class ImportFromUri(val uri: android.net.Uri) : ScriptEditorUiIntent()

    /** 追加一个空场景。 */
    data object AddScene : ScriptEditorUiIntent()

    /** 打开链接选择面板。 */
    data class EditLinks(val sceneId: String, val field: ScriptLinkField) : ScriptEditorUiIntent()

    /** 选中或取消一个链接目标。 */
    data class ToggleLink(
        val sceneId: String,
        val field: ScriptLinkField,
        val targetId: String
    ) : ScriptEditorUiIntent()

    /** 关闭链接选择面板。 */
    data object CloseLinkPanel : ScriptEditorUiIntent()

    /** 把当前剧本导出为 JSON 文本并复制到剪贴板。 */
    data object ExportLayout : ScriptEditorUiIntent()

    /** 返回上一页。 */
    data object Back : ScriptEditorUiIntent()

    /** 打开场景设置面板。 */
    data class EditScene(val sceneId: String) : ScriptEditorUiIntent()

    /** 关闭场景设置面板。 */
    data object CloseSceneEditor : ScriptEditorUiIntent()

    /** 提交场景设置：名称、轮次上限、开场文本与三个开关。 */
    data class UpdateScene(
        val sceneId: String,
        val name: String,
        val maxRounds: Int,
        val entryPrompt: String,
        val checkpoint: Boolean,
        val loopEnabled: Boolean,
        val isEntry: Boolean
    ) : ScriptEditorUiIntent()

    /** 删除场景。 */
    data class DeleteScene(val sceneId: String) : ScriptEditorUiIntent()

}

/** 剧本编辑页的一次性事件。 */
sealed class ScriptEditorViewEvent : me.kafuuneko.rpclient.libs.core.IViewEvent {
    /** 打开系统文件选择器。 */
    data object OpenFilePicker : ScriptEditorViewEvent()

    /** 导入结果提示。 */
    data class ImportResult(val success: Boolean, val detail: String, val savedPackCount: Int) : ScriptEditorViewEvent()

    /** 请求宿主把文本写入剪贴板。 */
    data class CopyToClipboard(val text: String) : ScriptEditorViewEvent()
}
