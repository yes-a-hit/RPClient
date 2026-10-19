package me.kafuuneko.rpclient.feature.scriptadventure.presentation

/** 剧情冒险首页的一个区块项。 */
data class AdventurePackItem(
    val packId: String,
    val name: String,
    val sceneCount: Int,
    val modeLabel: String
)

/** 一条已有存档。 */
data class AdventureSaveItem(
    val playId: String,
    val packId: String,
    val packName: String,
    val sceneId: String,
    val roundCount: Int,
    val finished: Boolean,
    val endReason: String
)

/** 剧情冒险状态。 */
sealed class ScriptAdventureUiState {
    data object Loading : ScriptAdventureUiState()

    data class Ready(
        /** 可开局的剧本。 */
        val packs: List<AdventurePackItem> = emptyList(),
        /** 已有存档。 */
        val saves: List<AdventureSaveItem> = emptyList()
    ) : ScriptAdventureUiState() {
        /** 完全没有可玩内容时提示导入剧本。 */
        val isEmpty: Boolean
            get() = packs.isEmpty() && saves.isEmpty()
    }

    data object Finished : ScriptAdventureUiState()
}

/** 剧情冒险意图。 */
sealed class ScriptAdventureUiIntent {
    data object Init : ScriptAdventureUiIntent()

    /** 用指定剧本开新局。 */
    data class StartNew(val packId: String) : ScriptAdventureUiIntent()

    /** 继续已有存档。 */
    data class Continue(val playId: String, val packId: String) : ScriptAdventureUiIntent()

    /** 删除存档。 */
    data class DeleteSave(val playId: String) : ScriptAdventureUiIntent()

    /** 打开剧本库导入或编辑（调试入口）。 */
    data object OpenLibrary : ScriptAdventureUiIntent()

    data object Back : ScriptAdventureUiIntent()
}

/** 剧情冒险事件。 */
sealed class ScriptAdventureViewEvent : me.kafuuneko.rpclient.libs.core.IViewEvent {
    /** 进入剧本模式。 */
    data class LaunchPlay(val packId: String, val playId: String) : ScriptAdventureViewEvent()

    /** 打开剧本库。 */
    data object OpenLibrary : ScriptAdventureViewEvent()
}
