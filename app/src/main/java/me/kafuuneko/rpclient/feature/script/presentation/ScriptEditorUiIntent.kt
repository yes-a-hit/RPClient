package me.kafuuneko.rpclient.feature.script.presentation

import me.kafuuneko.rpclient.libs.script.ScriptVariableKind

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

    /** 导出成 .json 文件；先弹系统保存框。 */
    data object ExportToFileClick : ScriptEditorUiIntent()

    /** 用户选好保存位置后写入。 */
    data class ExportToFile(val uri: android.net.Uri) : ScriptEditorUiIntent()

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
        val isEntry: Boolean,
        /** 背景图检索词，对应图库标签或名称。 */
        val backgroundKey: String = "",
        /** 可出场角色名，顿号或逗号分隔。 */
        val castNames: String = "",
        /** 禁止出场角色名。 */
        val bannedNames: String = "",
        /** 绑定的世界书名称，留空表示不绑定。 */
        val lorebookName: String = "",
        /** 失败关键词，命中即失败。 */
        val failKeyword: String = "",
        /** 失败轮次上限，0 表示不限制。 */
        val failRounds: Int = 0,
        /** 通关关键词，命中即通关。 */
        val completionKeyword: String = "",
        /** 后继场景 ID，逗号分隔。 */
        val nextSceneIds: String = "",
        val suggestedActions: String = "",
        /** 是否继承剧本级背景。 */
        val inheritBackground: Boolean = true
    ) : ScriptEditorUiIntent()

    /** 删除场景。 */
    /**
     * 让模型写梗概与开场白。
     *
     * [hint] 是玩家的具体要求，写清了就照做，留空才由模型自由发挥。
     */
    data class GeneratePackInfo(val hint: String = "") : ScriptEditorUiIntent()

    /** 让模型为某一章写内容；[hint] 同样优先于自由发挥。 */
    data class GenerateSceneContent(
        val sceneId: String,
        val hint: String = ""
    ) : ScriptEditorUiIntent()

    /**
     * 更新剧本级信息。
     *
     * 开场白按「发言者|内容」每行一条的纯文本编辑，
     * 保存时拆成结构化列表，JSON 里是 openings 数组。
     */
    data class UpdatePackInfo(
        val name: String,
        val description: String,
        val openingsText: String,
        val showCompletion: Boolean,
        val shareSettings: Boolean,
        /** 剧本级背景，留空表示不设置。 */
        val backgroundKey: String = ""
    ) : ScriptEditorUiIntent()


    /**
     * 按剧本里的角色设定创建本地角色卡。
     *
     * 剧本声明的 cast 若在本地找不到同名角色卡，就用描述建一张，
     * 省得玩家自己开着编辑器一个个补。
     */
    /**
     * 请求为某处挑一张背景图。
     *
     * [sceneId] 为空表示剧本级全局背景，否则是那一章。
     */
    /** 新增一个剧本变量，默认名按序号生成。 */
    /** 给指定场景加一条事件。 */
    data class AddEvent(val sceneId: String) : ScriptEditorUiIntent()

    /** 打开某条事件的编辑框；[eventId] 为空表示新建。 */
    data class OpenEventEditor(val sceneId: String, val eventId: String) : ScriptEditorUiIntent()

    /** 关闭事件编辑框。 */
    data object CloseEventEditor : ScriptEditorUiIntent()

    /**
     * 保存事件。
     *
     * 数值从界面传进来时都还是字符串，由 ViewModel 统一转换，
     * 免得到处写 toIntOrNull。
     */
    data class SaveEvent(
        val sceneId: String,
        val eventId: String,
        val name: String,
        val atRound: String,
        val keyword: String,
        val chance: String,
        val addCast: String,
        val removeCast: String,
        val gotoSceneId: String,
        val narration: String,
        val once: Boolean,
        /** 变量副作用，每行一条，格式「变量名 +2」或「变量名 -1」。 */
        val effectsText: String,
        /**
         * 线索追加，每行一条「变量名 内容」。
         *
         * 和数值副作用分开写：线索是攒的，写混在一行里
         * 既难解析也看不出哪条是线索。
         */
        val clueText: String = "" 
    ) : ScriptEditorUiIntent()

    /** 删除一条事件。 */
    data class DeleteEvent(val sceneId: String, val eventId: String) : ScriptEditorUiIntent()

    /** 开关剧本级状态面板。 */
    data class ToggleDashboard(val enabled: Boolean) : ScriptEditorUiIntent()

    /** 勾选或取消某场景面板里的一栏。 */
    data class ToggleSceneDashboardItem(
        val sceneId: String,
        val variableName: String
    ) : ScriptEditorUiIntent()

    data object AddVariable : ScriptEditorUiIntent()

    /** 修改某个变量的定义。 */
    data class UpdateVariable(
        val originalName: String,
        val name: String,
        val label: String,
        val initial: Int,
        val min: Int,
        val max: Int,
        val visible: Boolean,
        /** 变量类型，决定面板怎么渲染。 */
        val kind: ScriptVariableKind = ScriptVariableKind.Number,
        /** 文本变量的初始内容。 */
        val text: String = "",
        /** 开关的两态文案。 */
        val onLabel: String = "",
        val offLabel: String = ""
    ) : ScriptEditorUiIntent()

    /** 删除某个变量。 */
    data class DeleteVariable(val name: String) : ScriptEditorUiIntent()

    data class PickBackground(val sceneId: String?) : ScriptEditorUiIntent()

    /** 选好图之后回来的 URI。 */
    data class ApplyBackground(val sceneId: String?, val uri: String) : ScriptEditorUiIntent()

    data class CreateCastCharacter(val name: String) : ScriptEditorUiIntent()

    /** 把某个角色从剧本的所有名单里移除。 */
    data class RemoveCastCharacter(val name: String) : ScriptEditorUiIntent()

    data class DeleteScene(val sceneId: String) : ScriptEditorUiIntent()

}

/** 剧本编辑页的一次性事件。 */
sealed class ScriptEditorViewEvent : me.kafuuneko.rpclient.libs.core.IViewEvent {
    /** 打开系统文件选择器。 */
    data object OpenFilePicker : ScriptEditorViewEvent()

    /** 打开图片选择器；[sceneId] 为空表示剧本级背景。 */
    data class PickBackgroundImage(val sceneId: String?) : ScriptEditorViewEvent()

    /** 导入结果提示。 */
    data class ImportResult(val success: Boolean, val detail: String, val savedPackCount: Int) : ScriptEditorViewEvent()

    /** 请求宿主把文本写入剪贴板。 */
    data class CopyToClipboard(val text: String) : ScriptEditorViewEvent()

    /** 请求宿主弹出保存框；建议文件名一并带回。 */
    data class OpenExportTarget(val suggestedName: String) : ScriptEditorViewEvent()

    /** 文件导出的结果。 */
    data class ExportResult(val success: Boolean, val detail: String) :
        ScriptEditorViewEvent()
}