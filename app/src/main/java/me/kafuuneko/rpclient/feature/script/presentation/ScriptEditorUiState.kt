package me.kafuuneko.rpclient.feature.script.presentation

/** 表格里可选的链接字段。 */
enum class ScriptLinkField {
    /** 后继场景（主线推进）。 */
    Next,

    /** 解锁前置（前置都经历过才解锁）。 */
    Unlock
}

/** 表格里的一行场景。 */
data class ScriptSceneRow(
    val id: String,
    val name: String,
    /** 是否入口场景。 */
    val isEntry: Boolean,
    /** 是否检查点。 */
    val checkpoint: Boolean,
    /** 本场景轮次上限，0 表示不限。 */
    val maxRounds: Int,
    /** 开场文本。 */
    val entryPrompt: String = "",
    /** 是否允许循环。 */
    val loopEnabled: Boolean = false,
    /** 后继场景 ID。 */
    val nextIds: List<String> = emptyList(),
    /** 解锁前置 ID。 */
    val unlockIds: List<String> = emptyList(),
    /** 背景图检索词。 */
    val backgroundKey: String = "",
    /** 可出场角色，顿号分隔的展示串。 */
    val castNames: String = "",
    /** 禁止出场角色，顿号分隔的展示串。 */
    val bannedNames: String = "",
    /** 绑定的世界书名称。 */
    val lorebookName: String = "",
    /** 失败关键词。 */
    val failKeyword: String = "",
    /** 失败轮次上限，0 表示不限。 */
    val failRounds: Int = 0,
    /** 通关关键词。 */
    val completionKeyword: String = "",
    /** 后继场景 ID，逗号分隔的展示串。 */
    val nextSceneIds: String = "",
    /** 是否继承剧本级背景。 */
    val inheritBackground: Boolean = true,
    /** 建议行动，每行一条；留空表示交给 AI 临场决定。 */
    val suggestedActions: String = ""
)

/** 剧本编辑页状态。 */
sealed class ScriptEditorUiState {
    data object Loading : ScriptEditorUiState()

    data class Ready(
        /** 剧本名称。 */
        val packName: String,
        /** 场景表格。 */
        val rows: List<ScriptSceneRow> = emptyList(),
        /** 正在编辑链接的场景 ID。 */
        val editingSceneId: String? = null,
        /** 正在编辑哪种链接。 */
        val editingField: ScriptLinkField? = null,
        /** 正在编辑设置的场景 ID。 */
        val settingsSceneId: String? = null,
        /** 正在生成哪一块内容；空表示空闲。 */
        val generating: String = "",

        /** 剧本级背景图关键词或名称。 */
        val packBackground: String = "",

        /** 剧本梗概，同时充当全局背景设定。 */
        val description: String = "",
        /** 开场白，已按「发言者|内容」拼成多行文本。 */
        val openingsText: String = "",
        /** 成功条件是否对玩家可见。 */
        val showCompletion: Boolean = true,
        /** 设定是否允许其他玩家查看。 */
        val shareSettings: Boolean = false,
        /** 剧本里实际用到的角色名，供超管面板管理。 */
        val castInUse: List<String> = emptyList(),

        /** 剧本引用了但图库里没有的背景，需要作者或玩家补图。 */
        val missingBackgrounds: List<String> = emptyList(),

        /** 可选角色卡名称，供勾选出场与禁止名单。 */
        val characterNames: List<String> = emptyList(),
        /** 可选世界书名称。 */
        val lorebookNames: List<String> = emptyList(),
        /** 图库里可当背景用的检索词。 */
        val galleryKeys: List<String> = emptyList()
    ) : ScriptEditorUiState() {
        /** 正在编辑的场景行。 */
        val editingRow: ScriptSceneRow?
            get() = rows.firstOrNull { it.id == editingSceneId }

        /** 正在设置面板里编辑的场景行。 */
        val settingsRow: ScriptSceneRow?
            get() = rows.firstOrNull { it.id == settingsSceneId }

        /** 按 ID 取场景名称。 */
        fun nameOf(sceneId: String): String =
            rows.firstOrNull { it.id == sceneId }?.name ?: sceneId
    }

    data object Finished : ScriptEditorUiState()
}
