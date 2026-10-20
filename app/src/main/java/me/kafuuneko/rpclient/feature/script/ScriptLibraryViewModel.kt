package me.kafuuneko.rpclient.feature.script

import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryItem
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryTab
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryUiIntent
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryUiState
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryViewEvent
import me.kafuuneko.rpclient.feature.script.presentation.ScriptSceneItem
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.script.ScriptImportOutcome
import me.kafuuneko.rpclient.libs.script.ScriptMode
import me.kafuuneko.rpclient.libs.script.ScriptPackParseResult
import me.kafuuneko.rpclient.libs.script.ScriptPackCodec
import me.kafuuneko.rpclient.libs.script.ScriptStore
import me.kafuuneko.rpclient.libs.script.ScriptPack
import me.kafuuneko.rpclient.libs.script.ScriptScene
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 剧本库状态持有者。
 *
 * 两个标签共享同一份磁盘数据：
 * - 剧本标签按剧本聚合，点击进入编排；
 * - 场景标签把各剧本的场景摊平，便于跨剧本检查复用与入口分布。
 */
class ScriptLibraryViewModel(
    private val mInitialTab: ScriptLibraryTab
) : CoreViewModelWithEvent<ScriptLibraryUiIntent, ScriptLibraryUiState>(
    ScriptLibraryUiState.Loading
), KoinComponent {
    private val mStore by inject<ScriptStore>()
    private val mCodec = ScriptPackCodec()

    /** 读取磁盘上的全部剧本。 */
    @UiIntentObserver(ScriptLibraryUiIntent.Init::class)
    private suspend fun onInit() {
        if (isStateOf<ScriptLibraryUiState.Ready>()) return
        publish(mInitialTab)
    }

    /** 切换标签。 */
    @UiIntentObserver(ScriptLibraryUiIntent.SwitchTab::class)
    private suspend fun onSwitchTab(intent: ScriptLibraryUiIntent.SwitchTab) {
        publish(intent.tab)
    }

    /** 重命名剧本：读出来改名再写回，剧本 ID 不变。 */

    /** 打开粘贴导入。 */
    @UiIntentObserver(ScriptLibraryUiIntent.ShowPasteImport::class)
    private fun onShowPasteImport() {
        val state = getOrNull<ScriptLibraryUiState.Ready>() ?: return
        state.copy(pasteDialogVisible = true, pasteDraft = "").setup()
    }

    /** 关闭粘贴导入。 */
    @UiIntentObserver(ScriptLibraryUiIntent.HidePasteImport::class)
    private fun onHidePasteImport() {
        val state = getOrNull<ScriptLibraryUiState.Ready>() ?: return
        state.copy(pasteDialogVisible = false).setup()
    }

    @UiIntentObserver(ScriptLibraryUiIntent.PasteChanged::class)
    private fun onPasteChanged(intent: ScriptLibraryUiIntent.PasteChanged) {
        val state = getOrNull<ScriptLibraryUiState.Ready>() ?: return
        state.copy(pasteDraft = intent.text).setup()
    }

    /**
     * 导入粘贴的文本。
     *
     * 文件选择器在部分设备上不可靠，粘贴导入是兜底通道：
     * 内容与导入文件走同一套校验，不会绕过错误检查。
     */
    @UiIntentObserver(ScriptLibraryUiIntent.ConfirmPasteImport::class)
    private suspend fun onConfirmPasteImport() {
        val state = getOrNull<ScriptLibraryUiState.Ready>() ?: return
        val raw = state.pasteDraft.trim()
        if (raw.isBlank()) return
        when (val outcome = mStore.importFromText(raw)) {
            is ScriptImportOutcome.Success -> {
                ScriptLibraryViewEvent.ShowToast(
                    "已导入 ${outcome.packName}（${outcome.sceneCount} 个场景）"
                ).tryEmit()
                val current = getOrNull<ScriptLibraryUiState.Ready>() ?: return
                current.copy(pasteDialogVisible = false, pasteDraft = "").setup()
                publish(current.tab)
            }

            is ScriptImportOutcome.Failure -> {
                ScriptLibraryViewEvent.ShowToast("导入失败：${outcome.detail}").tryEmit()
            }
        }
    }

    /** 导出剧本为可分享文本，用于制作模板包。 */
    @UiIntentObserver(ScriptLibraryUiIntent.ExportPack::class)
    private suspend fun onExportPack(intent: ScriptLibraryUiIntent.ExportPack) {
        val pack = mStore.load(intent.packId) ?: return
        val json = mCodec.encode(pack)
        ScriptLibraryViewEvent.ShareText(
            name = "${pack.name}.json",
            text = json
        ).tryEmit()
    }

    @UiIntentObserver(ScriptLibraryUiIntent.RenamePack::class)
    private suspend fun onRenamePack(intent: ScriptLibraryUiIntent.RenamePack) {
        val state = getOrNull<ScriptLibraryUiState.Ready>() ?: return
        val name = intent.newName.trim()
        if (name.isBlank()) return
        mStore.load(intent.packId)?.let { pack ->
            mStore.save(pack.copy(name = name))
        }
        publish(state.tab)
    }

    /** 删除剧本与其存档文件由使用方决定，这里只删剧本定义。 */
    @UiIntentObserver(ScriptLibraryUiIntent.DeletePack::class)
    private suspend fun onDeletePack(intent: ScriptLibraryUiIntent.DeletePack) {
        val state = getOrNull<ScriptLibraryUiState.Ready>() ?: return
        mStore.delete(intent.packId)
        publish(state.tab)
    }

    /** 开始一局剧本。 */
    @UiIntentObserver(ScriptLibraryUiIntent.PlayPack::class)
    private fun onPlayPack(intent: ScriptLibraryUiIntent.PlayPack) {
        ScriptLibraryViewEvent.StartPlay(intent.packId).tryEmit()
    }

    /** 打开剧本编辑页。 */
    @UiIntentObserver(ScriptLibraryUiIntent.OpenPack::class)
    private fun onOpenPack(intent: ScriptLibraryUiIntent.OpenPack) {
        ScriptLibraryViewEvent.StartEditor(intent.packId).tryEmit()
    }

    /** 新建空剧本：只含一个场景，创建后直接进入编排。 */
    @UiIntentObserver(ScriptLibraryUiIntent.CreatePack::class)
    private suspend fun onCreatePack() {
        val state = getOrNull<ScriptLibraryUiState.Ready>() ?: return
        val packId = "script-" + System.currentTimeMillis()
        val pack = ScriptPack(
            id = packId,
            name = DEFAULT_PACK_NAME,
            startSceneId = FIRST_SCENE_ID,
            scenes = listOf(ScriptScene(id = FIRST_SCENE_ID, name = FIRST_SCENE_NAME))
        )
        mStore.save(pack)
        publish(state.tab)
        ScriptLibraryViewEvent.StartEditor(packId).tryEmit()
    }

    /** 打开文件选择器。 */
    @UiIntentObserver(ScriptLibraryUiIntent.ImportClick::class)
    private fun onImportClick() {
        ScriptLibraryViewEvent.OpenFilePicker.tryEmit()
    }

    /** 从文件导入剧本并刷新列表。 */
    @UiIntentObserver(ScriptLibraryUiIntent.ImportFromUri::class)
    private suspend fun onImportFromUri(intent: ScriptLibraryUiIntent.ImportFromUri) {
        val state = getOrNull<ScriptLibraryUiState.Ready>() ?: return
        when (val outcome = mStore.importFromUri(intent.uri)) {
            is ScriptImportOutcome.Success -> {
                publish(state.tab)
                ScriptLibraryViewEvent.ImportResult(true, outcome.packName).tryEmit()
            }

            is ScriptImportOutcome.Failure -> {
                ScriptLibraryViewEvent.ImportResult(false, outcome.detail).tryEmit()
            }
        }
    }

    /** 返回。 */
    @UiIntentObserver(ScriptLibraryUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<ScriptLibraryUiState.Finished>()) return
        ScriptLibraryUiState.Finished.setup()
    }

    /** 重新读取剧本并发布状态。 */
    private suspend fun publish(tab: ScriptLibraryTab) {
        val summaries = mStore.list()
        val packs = summaries.map { summary ->
            ScriptLibraryItem(
                packId = summary.id,
                packName = summary.name,
                sceneCount = summary.sceneCount,
                modeLabel = summary.mode.label()
            )
        }
        // 场景标签需要完整剧本，逐个读取后摊平；损坏文件由存储层过滤
        val scenes = summaries.flatMap { summary ->
            val pack = mStore.load(summary.id) ?: return@flatMap emptyList()
            val entryIds = buildSet {
                addAll(pack.startSceneIds)
                if (pack.startSceneId.isNotBlank()) add(pack.startSceneId)
                if (isEmpty() && pack.scenes.isNotEmpty()) add(pack.scenes.first().id)
            }
            pack.scenes.map { scene ->
                ScriptSceneItem(
                    packId = pack.id,
                    packName = pack.name,
                    sceneId = scene.id,
                    sceneName = scene.name,
                    isEntry = scene.id in entryIds
                )
            }
        }
        ScriptLibraryUiState.Ready(tab = tab, packs = packs, scenes = scenes).setup()
    }

    /** 模式展示名，保持与文档用词一致。 */
    private fun ScriptMode.label(): String = when (this) {
        ScriptMode.Mainline -> "主线"
        ScriptMode.Loop -> "循环"
        ScriptMode.Free -> "自由"
    }

    private companion object {
        /** 新建剧本的默认名称，用户可随时改。 */
        const val DEFAULT_PACK_NAME = "新剧本"

        /** 首个场景的固定 ID。 */
        const val FIRST_SCENE_ID = "scene-1"

        /** 首个场景的默认名称。 */
        const val FIRST_SCENE_NAME = "场景 1"
    }

    /** 解析失败时用于回退提示的空实现，避免未使用告警。 */
    private fun unusedParseGuard(result: ScriptPackParseResult): Boolean =
        result is ScriptPackParseResult.Failure
}
