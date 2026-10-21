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
        /** 角色卡名称，供开局时挑选在场角色。 */
        val characters: List<String> = emptyList(),
        /** 正在为哪个剧本挑角色；null 表示选择器关闭。 */
        val pickingForPackId: String? = null,
        /** 已勾选的在场角色。 */
        val selectedCharacters: Set<String> = emptySet(),
        /** 进行中的存档；「继续冒险」只列这些。 */
        val saves: List<AdventureSaveItem> = emptyList(),
        /**
         * 已结束的存档。
         *
         * 通关或失败的局仍要能重看与删除，但混在继续列表里
         * 会让玩家分不清哪条还能接着玩，所以单独一段并默认折叠。
         */
        val finishedSaves: List<AdventureSaveItem> = emptyList(),
        /** 已结束分组是否展开。 */
        val finishedExpanded: Boolean = false
    ) : ScriptAdventureUiState() {
        /** 完全没有可玩内容时提示导入剧本。 */
        val isEmpty: Boolean
            get() = packs.isEmpty() && saves.isEmpty() && finishedSaves.isEmpty()
    }

    data object Finished : ScriptAdventureUiState()
}

/** 剧情冒险意图。 */
sealed class ScriptAdventureUiIntent {
    data object Init : ScriptAdventureUiIntent()

    /** 为指定剧本打开角色选择器。 */
    data class OpenCharacterPicker(val packId: String) : ScriptAdventureUiIntent()

    /** 勾选或取消一个在场角色。 */
    data class ToggleCharacter(val name: String) : ScriptAdventureUiIntent()

    /** 关闭角色选择器。 */
    data object DismissCharacterPicker : ScriptAdventureUiIntent()

    /** 用指定剧本开新局。 */
    data class StartNew(val packId: String, val characters: List<String> = emptyList()) : ScriptAdventureUiIntent()

    /** 继续已有存档。 */
    data class Continue(val playId: String, val packId: String) : ScriptAdventureUiIntent()

    /** 删除存档。 */
    data class DeleteSave(val playId: String) : ScriptAdventureUiIntent()

    /** 打开剧本库导入或编辑（调试入口）。 */
    data object OpenLibrary : ScriptAdventureUiIntent()

    /** 直接跳到编辑器改这个剧本，不用先绕去剧本库。 */
    data class OpenEditor(val packId: String) : ScriptAdventureUiIntent()

    /** 展开或收起「已结束」的存档分组。 */
    data object ToggleFinishedSaves : ScriptAdventureUiIntent()

    data object Back : ScriptAdventureUiIntent()
}

/** 剧情冒险事件。 */
sealed class ScriptAdventureViewEvent : me.kafuuneko.rpclient.libs.core.IViewEvent {
    /** 进入剧本模式。 */
    data class LaunchPlay(
        val packId: String,
        val playId: String,
        val characters: List<String> = emptyList()
    ) : ScriptAdventureViewEvent()

    /** 打开剧本库。 */
    data object OpenLibrary : ScriptAdventureViewEvent()

    /** 打开剧本编辑页。 */
    data class OpenEditor(val packId: String) : ScriptAdventureViewEvent()
}
