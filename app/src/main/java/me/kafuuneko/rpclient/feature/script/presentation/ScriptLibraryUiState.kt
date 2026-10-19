package me.kafuuneko.rpclient.feature.script.presentation

/** 剧本库的两个标签页。 */
enum class ScriptLibraryTab {
    /** 剧本：整部作品的流程编排。 */
    Pack,

    /** 场景：跨剧本的场景总览，用于复用与检查。 */
    Scene
}

/** 剧本库里的一行。 */
data class ScriptLibraryItem(
    val packId: String,
    val packName: String,
    /** 场景数量。 */
    val sceneCount: Int,
    /** 运行模式展示名。 */
    val modeLabel: String
)

/** 场景总览里的一行。 */
data class ScriptSceneItem(
    val packId: String,
    val packName: String,
    val sceneId: String,
    val sceneName: String,
    /** 入口标记。 */
    val isEntry: Boolean
)

/** 剧本库状态。 */
sealed class ScriptLibraryUiState {
    data object Loading : ScriptLibraryUiState()

    data class Ready(
        val tab: ScriptLibraryTab = ScriptLibraryTab.Pack,
        val packs: List<ScriptLibraryItem> = emptyList(),
        val scenes: List<ScriptSceneItem> = emptyList()
    ) : ScriptLibraryUiState() {
        /** 当前标签是否为空。 */
        val isEmpty: Boolean
            get() = if (tab == ScriptLibraryTab.Pack) packs.isEmpty() else scenes.isEmpty()
    }

    data object Finished : ScriptLibraryUiState()
}

/** 剧本库意图。 */
sealed class ScriptLibraryUiIntent {
    /** 首次进入，读取已有剧本。 */
    data object Init : ScriptLibraryUiIntent()

    /** 切换标签。 */
    data class SwitchTab(val tab: ScriptLibraryTab) : ScriptLibraryUiIntent()

    /** 开始一局剧本。 */
    data class PlayPack(val packId: String) : ScriptLibraryUiIntent()

    /** 重命名剧本。 */
    data class RenamePack(val packId: String, val newName: String) : ScriptLibraryUiIntent()

    /** 删除剧本。 */
    data class DeletePack(val packId: String) : ScriptLibraryUiIntent()

    /** 打开某个剧本的编辑页。 */
    data class OpenPack(val packId: String) : ScriptLibraryUiIntent()

    /** 导入剧本文件。 */
    data object ImportClick : ScriptLibraryUiIntent()

    /** 手动新建一个剧本。 */
    data object CreatePack : ScriptLibraryUiIntent()

    /** 用户选中了剧本文件。 */
    data class ImportFromUri(val uri: android.net.Uri) : ScriptLibraryUiIntent()

    /** 返回。 */
    data object Back : ScriptLibraryUiIntent()
}

/** 剧本库事件。 */
sealed class ScriptLibraryViewEvent : me.kafuuneko.rpclient.libs.core.IViewEvent {
    /** 打开系统文件选择器。 */
    data object OpenFilePicker : ScriptLibraryViewEvent()

    /** 开始或继续一局剧本。 */
    data class StartPlay(val packId: String) : ScriptLibraryViewEvent()

    /** 跳转到剧本编辑页。 */
    data class StartEditor(val packId: String) : ScriptLibraryViewEvent()

    /** 导入结果提示。 */
    data class ImportResult(val success: Boolean, val detail: String) : ScriptLibraryViewEvent()
}
