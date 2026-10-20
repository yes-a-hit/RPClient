package me.kafuuneko.rpclient.feature.script

import me.kafuuneko.rpclient.feature.script.presentation.ScriptEditorUiIntent
import me.kafuuneko.rpclient.feature.script.presentation.ScriptEditorUiState
import me.kafuuneko.rpclient.feature.script.presentation.ScriptEditorViewEvent
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLinkField
import me.kafuuneko.rpclient.feature.script.presentation.ScriptSceneRow
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.script.ScriptCondition
import me.kafuuneko.rpclient.libs.script.ScriptConditionType
import me.kafuuneko.rpclient.libs.script.ScriptCastMember
import me.kafuuneko.rpclient.libs.script.ScriptLorebook
import me.kafuuneko.rpclient.libs.script.ScriptPack
import me.kafuuneko.rpclient.libs.script.ScriptPackCodec
import me.kafuuneko.rpclient.libs.script.ScriptScene
import me.kafuuneko.rpclient.libs.script.ScriptMode
import me.kafuuneko.rpclient.libs.script.ScriptImportOutcome
import me.kafuuneko.rpclient.libs.script.ScriptStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 剧本表格编辑状态持有者。
 *
 * 以链接为核心：每个场景一行，行内展示后继与解锁前置，
 * 点开后在面板里勾选目标场景即可增删关系，不需要拖拽或手写 JSON。
 * 所有改动都落在内存剧本包上，导出时一次性编码。
 */
class ScriptEditorViewModel : CoreViewModelWithEvent<ScriptEditorUiIntent, ScriptEditorUiState>(
    ScriptEditorUiState.Loading
), KoinComponent {
    private val mCodec = ScriptPackCodec()

    private val mStore by inject<ScriptStore>()

    /** 角色卡名称缓存，供设置面板勾选。 */
    private var mCharacterNames: List<String> = emptyList()

    /** 世界书名称缓存。 */
    private var mLorebookNames: List<String> = emptyList()

    /** 图库检索词缓存，作为背景候选项。 */
    private var mGalleryKeys: List<String> = emptyList()
    private val mCharacterRepository by
        inject<me.kafuuneko.rpclient.libs.room.repository.CharacterRepository>()
    private val mLorebookRepository by
        inject<me.kafuuneko.rpclient.libs.room.repository.LorebookRepository>()
    private val mGalleryStore by inject<me.kafuuneko.rpclient.libs.gallery.GalleryStore>()

    /** 当前编辑的剧本包。 */
    private var mPack: ScriptPack = SAMPLE_PACK

    /** 加载已保存的剧本；没有已保存剧本时回落到示例。 */
    @UiIntentObserver(ScriptEditorUiIntent.Init::class)
    private suspend fun onInit() {
        loadCandidates()
        if (isStateOf<ScriptEditorUiState.Ready>()) return
        mPack = mStore.loadFirst() ?: SAMPLE_PACK
        publish(null, null)
    }

    /** 打开指定已保存剧本。 */
    @UiIntentObserver(ScriptEditorUiIntent.OpenPack::class)
    private suspend fun onOpenPack(intent: ScriptEditorUiIntent.OpenPack) {
        mPack = mStore.load(intent.packId) ?: mPack
        publish(null, null)
    }

    /** 打开文件选择器。 */
    @UiIntentObserver(ScriptEditorUiIntent.ImportClick::class)
    private fun onImportClick() {
        ScriptEditorViewEvent.OpenFilePicker.tryEmit()
    }

    /** 从选中的文件导入剧本，成功后就地装载到表格。 */
    @UiIntentObserver(ScriptEditorUiIntent.ImportFromUri::class)
    private suspend fun onImportFromUri(intent: ScriptEditorUiIntent.ImportFromUri) {
        when (val outcome = mStore.importFromUri(intent.uri)) {
            is ScriptImportOutcome.Success -> {
                mStore.load(outcome.packId)?.let { mPack = it }
                publish(null, null)
                ScriptEditorViewEvent.ImportResult(
                    success = true,
                    detail = outcome.packName,
                    savedPackCount = outcome.savedPackCount
                ).tryEmit()
            }

            is ScriptImportOutcome.Failure -> {
                ScriptEditorViewEvent.ImportResult(
                    success = false,
                    detail = outcome.detail,
                    savedPackCount = 0
                ).tryEmit()
            }
        }
    }

    /** 追加一个空场景并持久化，便于从零搭建剧本。 */
    @UiIntentObserver(ScriptEditorUiIntent.AddScene::class)
    private suspend fun onAddScene() {
        val nextIndex = mPack.scenes.size + 1
        val sceneId = "scene-$nextIndex"
        mPack = mPack.copy(
            scenes = mPack.scenes + ScriptScene(id = sceneId, name = "场景 $nextIndex")
        )
        mStore.save(mPack)
        publish(null, null)
    }

    /** 打开链接选择面板。 */
    @UiIntentObserver(ScriptEditorUiIntent.EditLinks::class)
    private fun onEditLinks(intent: ScriptEditorUiIntent.EditLinks) {
        if (isStateOf<ScriptEditorUiState.Ready>()) {
            publish(intent.sceneId, intent.field)
        }
    }

    /** 勾选或取消一个链接目标，改动立即写回内存剧本。 */
    @UiIntentObserver(ScriptEditorUiIntent.ToggleLink::class)
    private suspend fun onToggleLink(intent: ScriptEditorUiIntent.ToggleLink) {
        if (intent.targetId == intent.sceneId) return
        mPack = mPack.copy(
            scenes = mPack.scenes.map { scene ->
                if (scene.id != intent.sceneId) {
                    scene
                } else {
                    when (intent.field) {
                        ScriptLinkField.Next -> scene.copy(
                            nextSceneIds = scene.nextSceneIds.toggled(intent.targetId)
                        )

                        ScriptLinkField.Unlock -> scene.copy(
                            unlockedBy = scene.unlockedBy.toggled(intent.targetId)
                        )
                    }
                }
            }
        )
        mStore.save(mPack)
        publish(intent.sceneId, intent.field)
    }

    /** 关闭链接选择面板。 */
    @UiIntentObserver(ScriptEditorUiIntent.CloseLinkPanel::class)
    private suspend fun onCloseLinkPanel() {
        publish(null, null)
    }


    /** 打开场景设置面板。 */
    @UiIntentObserver(ScriptEditorUiIntent.EditScene::class)
    private fun onEditScene(intent: ScriptEditorUiIntent.EditScene) {
        val state = getOrNull<ScriptEditorUiState.Ready>() ?: return
        state.copy(settingsSceneId = intent.sceneId).setup()
    }

    /** 关闭场景设置面板。 */
    @UiIntentObserver(ScriptEditorUiIntent.CloseSceneEditor::class)
    private fun onCloseSceneEditor() {
        val state = getOrNull<ScriptEditorUiState.Ready>() ?: return
        state.copy(settingsSceneId = null).setup()
    }

    /** 提交场景设置并落盘。设置的入口会顶掉原入口，保证单一入口语义。 */


    /**
     * 加载设置面板的候选数据。
     *
     * 角色卡、世界书、图库都来自各自的仓储，一次读齐后缓存，
     * 免得每次打开对话框都去查库。
     */
    private suspend fun loadCandidates() {
        mCharacterNames = runCatching {
            mCharacterRepository.getAllCharacters().map { it.name }
        }.getOrDefault(emptyList())
        mLorebookNames = runCatching {
            mLorebookRepository.getAllLorebooks().map { it.name }
        }.getOrDefault(emptyList())
        mGalleryKeys = runCatching {
            mGalleryStore.list()
                .flatMap { image -> image.labels + image.name }
                .distinct()
        }.getOrDefault(emptyList())
    }

    /** 把顿号、逗号或空格分隔的名字拆成列表，去重保序。 */
    private fun splitNames(raw: String): List<String> = raw
        .split('、', ',', '，', ' ', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    /** 由关键词与轮次上限组装失败条件；都为空表示不判失败。 */
    private fun buildFailConditions(intent: ScriptEditorUiIntent.UpdateScene): List<ScriptCondition> {
        val conditions = mutableListOf<ScriptCondition>()
        val keyword = intent.failKeyword.trim()
        if (keyword.isNotBlank()) {
            conditions += ScriptCondition(
                type = ScriptConditionType.Keyword,
                pattern = keyword
            )
        }
        if (intent.failRounds > 0) {
            conditions += ScriptCondition(
                type = ScriptConditionType.RoundLimit,
                threshold = intent.failRounds
            )
        }
        return conditions
    }

    @UiIntentObserver(ScriptEditorUiIntent.UpdateScene::class)
    private suspend fun onUpdateScene(intent: ScriptEditorUiIntent.UpdateScene) {
        mPack = mPack.copy(
            startSceneId = if (intent.isEntry) intent.sceneId else mPack.startSceneId,
            startSceneIds = if (intent.isEntry) emptyList() else mPack.startSceneIds,
            scenes = mPack.scenes.map { scene ->
                if (scene.id != intent.sceneId) {
                    scene
                } else {
                    scene.copy(
                        name = intent.name.ifBlank { scene.name },
                        maxRounds = intent.maxRounds.coerceAtLeast(0),
                        entryPrompt = intent.entryPrompt,
                        checkpoint = intent.checkpoint,
                        loopEnabled = intent.loopEnabled,
                        backgroundKey = intent.backgroundKey.trim(),
                        cast = splitNames(intent.castNames).map { name ->
                            ScriptCastMember(name = name)
                        },
                        bannedCast = splitNames(intent.bannedNames),
                        lorebook = intent.lorebookName.trim()
                            .takeIf { it.isNotBlank() }
                            ?.let { ScriptLorebook(name = it) },
                        failConditions = buildFailConditions(intent),
                        completionCondition = intent.completionKeyword.trim()
                            .takeIf { it.isNotBlank() }
                            ?.let { keyword ->
                                ScriptCondition(
                                    type = ScriptConditionType.Keyword,
                                    pattern = keyword
                                )
                            },
                        nextSceneIds = splitNames(intent.nextSceneIds)
                    )
                }
            }
        )
        mStore.save(mPack)
        publish(null, null)
    }

    /** 删除场景；最后一个场景不允许删除，避免剧本变成空壳。 */
    @UiIntentObserver(ScriptEditorUiIntent.DeleteScene::class)
    private suspend fun onDeleteScene(intent: ScriptEditorUiIntent.DeleteScene) {
        if (mPack.scenes.size <= 1) {
            publish(null, null)
            return
        }
        mPack = mPack.copy(
            scenes = mPack.scenes.filterNot { it.id == intent.sceneId }
        )
        mStore.save(mPack)
        publish(null, null)
    }
    /** 导出当前剧本，交由宿主写入剪贴板。 */
    @UiIntentObserver(ScriptEditorUiIntent.ExportLayout::class)
    private suspend fun onExportLayout() {
        val state = getOrNull<ScriptEditorUiState.Ready>() ?: return
        ScriptEditorViewEvent.CopyToClipboard(mCodec.encode(mPack)).emitAndAwait()
        publish(state.editingSceneId, state.editingField)
    }

    /** 返回上一页。 */
    @UiIntentObserver(ScriptEditorUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<ScriptEditorUiState.Finished>()) return
        ScriptEditorUiState.Finished.setup()
    }

    /** 重建表格并发布状态。 */
    private fun publish(
        editingSceneId: String?,
        editingField: ScriptLinkField?,
        settingsSceneId: String? = null
    ) {
        val entryIds = buildSet {
            addAll(mPack.startSceneIds)
            if (mPack.startSceneId.isNotBlank()) add(mPack.startSceneId)
            if (isEmpty() && mPack.scenes.isNotEmpty()) add(mPack.scenes.first().id)
        }
        ScriptEditorUiState.Ready(
            packName = mPack.name,
            rows = mPack.scenes.map { scene ->
                ScriptSceneRow(
                    id = scene.id,
                    name = scene.name,
                    isEntry = scene.id in entryIds,
                    checkpoint = scene.checkpoint,
                    maxRounds = scene.maxRounds,
                    entryPrompt = scene.entryPrompt,
                    loopEnabled = scene.loopEnabled,
                    nextIds = scene.nextSceneIds,
                    unlockIds = scene.unlockedBy,
                    backgroundKey = scene.backgroundKey,
                    castNames = scene.cast.joinToString("、") { it.name },
                    bannedNames = scene.bannedCast.joinToString("、"),
                    lorebookName = scene.lorebook?.name.orEmpty(),
                    failKeyword = scene.failConditions
                        .firstOrNull { it.type == ScriptConditionType.Keyword }
                        ?.pattern
                        .orEmpty(),
                    failRounds = scene.failConditions
                        .firstOrNull { it.type == ScriptConditionType.RoundLimit }
                        ?.threshold
                        ?: 0,
                    completionKeyword = scene.completionCondition
                        ?.takeIf { it.type == ScriptConditionType.Keyword }
                        ?.pattern
                        .orEmpty(),
                    nextSceneIds = scene.nextSceneIds.joinToString("、")
                )
            },
            editingSceneId = editingSceneId,
            editingField = editingField,
            settingsSceneId = settingsSceneId,
            characterNames = mCharacterNames,
            lorebookNames = mLorebookNames,
            galleryKeys = mGalleryKeys
        ).setup()
    }

    /** 切换列表中的某一项，保持原有顺序。 */
    private fun List<String>.toggled(target: String): List<String> =
        if (target in this) filterNot { it == target } else this + target

    private companion object {
        /** 内置示例：一条主线加一条支线，便于直接点选链接。 */
        val SAMPLE_PACK = ScriptPack(
            id = "table-sample",
            name = "表格示例",
            mode = ScriptMode.Mainline,
            startSceneId = "a-start",
            scenes = listOf(
                ScriptScene(id = "a-start", name = "序章", checkpoint = true, maxRounds = 20),
                ScriptScene(id = "b-start", name = "支线起点", unlockedBy = listOf("a-start")),
                ScriptScene(id = "a-mid", name = "主线中段", nextSceneIds = listOf("final")),
                ScriptScene(id = "final", name = "汇合", unlockedBy = listOf("a-mid", "b-start"))
            )
        )
    }
}
