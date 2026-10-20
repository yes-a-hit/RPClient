package me.kafuuneko.rpclient.feature.scriptadventure

import me.kafuuneko.rpclient.feature.scriptadventure.presentation.AdventurePackItem
import me.kafuuneko.rpclient.feature.scriptadventure.presentation.AdventureSaveItem
import me.kafuuneko.rpclient.feature.scriptadventure.presentation.ScriptAdventureUiIntent
import me.kafuuneko.rpclient.feature.scriptadventure.presentation.ScriptAdventureUiState
import me.kafuuneko.rpclient.feature.scriptadventure.presentation.ScriptAdventureViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.script.ScriptMode
import me.kafuuneko.rpclient.libs.script.ScriptPlayStore
import me.kafuuneko.rpclient.libs.script.ScriptStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 剧情冒险首页状态持有者。
 *
 * 与剧本库的区别：库里是「编辑剧本」，这里是「进去玩」。
 * 所以页面上半是已有存档（继续玩），下半是可开局的剧本（开新局）。
 */
class ScriptAdventureViewModel :
    CoreViewModelWithEvent<ScriptAdventureUiIntent, ScriptAdventureUiState>(
        ScriptAdventureUiState.Loading
    ), KoinComponent {
    private val mScriptStore by inject<ScriptStore>()
    private val mPlayStore by inject<ScriptPlayStore>()
    private val mCharacterRepository by inject<me.kafuuneko.rpclient.libs.room.repository.CharacterRepository>()

    /** 读取剧本与存档。 */
    @UiIntentObserver(ScriptAdventureUiIntent.Init::class)
    private suspend fun onInit() {
        publish()
    }


    /** 打开角色选择器。 */
    @UiIntentObserver(ScriptAdventureUiIntent.OpenCharacterPicker::class)
    private fun onOpenCharacterPicker(intent: ScriptAdventureUiIntent.OpenCharacterPicker) {
        val state = getOrNull<ScriptAdventureUiState.Ready>() ?: return
        state.copy(pickingForPackId = intent.packId, selectedCharacters = emptySet()).setup()
    }

    /** 勾选或取消在场角色。 */
    @UiIntentObserver(ScriptAdventureUiIntent.ToggleCharacter::class)
    private fun onToggleCharacter(intent: ScriptAdventureUiIntent.ToggleCharacter) {
        val state = getOrNull<ScriptAdventureUiState.Ready>() ?: return
        val next = if (intent.name in state.selectedCharacters) {
            state.selectedCharacters - intent.name
        } else {
            state.selectedCharacters + intent.name
        }
        state.copy(selectedCharacters = next).setup()
    }

    /** 关闭选择器。 */
    @UiIntentObserver(ScriptAdventureUiIntent.DismissCharacterPicker::class)
    private fun onDismissCharacterPicker() {
        val state = getOrNull<ScriptAdventureUiState.Ready>() ?: return
        state.copy(pickingForPackId = null, selectedCharacters = emptySet()).setup()
    }

    /** 用指定剧本开新局。 */
    @UiIntentObserver(ScriptAdventureUiIntent.StartNew::class)
    private fun onStartNew(intent: ScriptAdventureUiIntent.StartNew) {
        // 存档 ID 留空，由剧本模式页按时间戳生成，避免覆盖旧局
        ScriptAdventureViewEvent.LaunchPlay(intent.packId, "", intent.characters).tryEmit()
    }

    /** 继续已有存档。 */
    @UiIntentObserver(ScriptAdventureUiIntent.Continue::class)
    private fun onContinue(intent: ScriptAdventureUiIntent.Continue) {
        ScriptAdventureViewEvent.LaunchPlay(intent.packId, intent.playId).tryEmit()
    }

    /** 删除存档并刷新列表。 */
    @UiIntentObserver(ScriptAdventureUiIntent.DeleteSave::class)
    private suspend fun onDeleteSave(intent: ScriptAdventureUiIntent.DeleteSave) {
        mPlayStore.delete(intent.playId)
        publish()
    }

    /** 打开剧本库（导入与编辑都走那里）。 */
    @UiIntentObserver(ScriptAdventureUiIntent.OpenLibrary::class)
    private fun onOpenLibrary() {
        ScriptAdventureViewEvent.OpenLibrary.tryEmit()
    }

    @UiIntentObserver(ScriptAdventureUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<ScriptAdventureUiState.Finished>()) return
        ScriptAdventureUiState.Finished.setup()
    }

    /** 重新读取并发布。 */
    private suspend fun publish() {
        val packs = mScriptStore.list().map { summary ->
            AdventurePackItem(
                packId = summary.id,
                name = summary.name,
                sceneCount = summary.sceneCount,
                modeLabel = summary.mode.label()
            )
        }
        val saves = mPlayStore.list().map { play ->
            AdventureSaveItem(
                playId = play.id,
                packId = play.packId,
                packName = play.packName,
                sceneId = play.sceneId,
                roundCount = play.roundCount,
                finished = play.finished,
                endReason = play.endReason
            )
        }
        val characters = runCatching {
            mCharacterRepository.getAllCharacters().map { it.name }
        }.getOrDefault(emptyList())
        ScriptAdventureUiState.Ready(
            packs = packs,
            saves = saves,
            characters = characters
        ).setup()
    }

    /** 模式展示名。 */
    private fun ScriptMode.label(): String = when (this) {
        ScriptMode.Mainline -> "主线"
        ScriptMode.Loop -> "循环"
        ScriptMode.Free -> "自由"
    }
}
