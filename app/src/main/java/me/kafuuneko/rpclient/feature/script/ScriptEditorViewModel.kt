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
import me.kafuuneko.rpclient.libs.script.ScriptOpening
import me.kafuuneko.rpclient.libs.room.entity.toConfig
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
    private val mClientFactory by inject<me.kafuuneko.rpclient.libs.llm.LLMClientFactory>()
    private val mProviderSelectionResolver by
        inject<me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver>()

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
    private val mFileRepository by inject<me.kafuuneko.rpclient.libs.room.repository.FileRepository>()

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
        val loaded = mStore.load(intent.packId)
        if (loaded != null) {
            mPack = loaded
            // 换剧本要重新读候选，否则角色与图库列表还是上一份的
            loadCandidates()
        } else if (mPack == null) {
            // 加载失败就退回第一个剧本或示例，别让界面直接关掉
            mPack = mStore.loadFirst() ?: SAMPLE_PACK
            loadCandidates()
        }
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


    /** 更新剧本级信息；开场白从多行文本拆回列表。 */

    /** 让模型写梗概与开场白草稿，结果直接落库并刷新界面。 */

    /** 用剧本里的角色设定建一张本地角色卡。 */

    /** 请求挑背景图；真正选图交给 Activity。 */
    @UiIntentObserver(ScriptEditorUiIntent.PickBackground::class)
    private fun onPickBackground(intent: ScriptEditorUiIntent.PickBackground) {
        ScriptEditorViewEvent.PickBackgroundImage(intent.sceneId).tryEmit()
    }

    /**
     * 把选中的图片导入图库并设为背景。
     *
     * 走图库而不是单独存盘：导入之后这张图在别的场景、
     * 甚至助手发图时也能复用，不用重复导入。
     */
    @UiIntentObserver(ScriptEditorUiIntent.ApplyBackground::class)
    private suspend fun onApplyBackground(intent: ScriptEditorUiIntent.ApplyBackground) {
        val uri = runCatching { android.net.Uri.parse(intent.uri) }.getOrNull() ?: return
        // 章节层上传的图最终是「进图库 + 打标签」，剧本里只留一个引用，
        // 图片本体永远不进 JSON
        val sceneName = intent.sceneId?.let { id ->
            mPack.scenes.firstOrNull { it.id == id }?.name
        }
        val image = runCatching {
            mGalleryStore.importFromUri(
                uri = uri,
                saveFile = { source ->
                    runCatching { mFileRepository.saveFile(source) }.getOrNull()
                },
                name = sceneName?.let { name -> name + " 背景" } ?: (mPack.name + " 背景"),
                // 打上场景名标签：多个章节各自换图时不会混成一张
                labels = listOf("背景") + listOfNotNull(sceneName),
                isPublic = true,
                description = "剧本《" + mPack.name + "》的背景图"
            )
        }.getOrNull() ?: return
        val key = image.name.ifBlank { image.labels.firstOrNull().orEmpty() }
        if (intent.sceneId == null) {
            mPack = mPack.copy(backgroundKey = key)
        } else {
            mPack = mPack.copy(
                scenes = mPack.scenes.map { scene ->
                    if (scene.id == intent.sceneId) {
                        // 选了图就必然不继承，否则会被剧本级覆盖
                        scene.copy(backgroundKey = key, inheritBackground = false)
                    } else {
                        scene
                    }
                }
            )
        }
        mStore.save(mPack)
        loadCandidates()
        publish(null, null)
    }

    @UiIntentObserver(ScriptEditorUiIntent.CreateCastCharacter::class)
    private suspend fun onCreateCastCharacter(intent: ScriptEditorUiIntent.CreateCastCharacter) {
        val name = intent.name.trim()
        if (name.isBlank()) return
        val existing = runCatching {
            mCharacterRepository.getAllCharacters().any { it.name == name }
        }.getOrDefault(false)
        if (existing) return
        // 从剧本里所有出现该角色的场景里找一段描述来当人设
        val description = mPack.scenes
            .flatMap { it.cast }
            .firstOrNull { it.name == name && it.description.isNotBlank() }
            ?.description
            .orEmpty()
        runCatching {
            mCharacterRepository.saveCharacter(
                me.kafuuneko.rpclient.libs.room.entity.Character(
                    name = name,
                    avatar = "",
                    characterTags = "[]",
                    description = description,
                    personality = "",
                    scenario = "",
                    firstMessages = "[]",
                    examplesOfDialogue = "",
                    postHistoryInstructions = ""
                )
            )
        }
        loadCandidates()
        publish(null, null)
    }

    /** 把角色从所有场景的出场与禁止名单里移除。 */
    @UiIntentObserver(ScriptEditorUiIntent.RemoveCastCharacter::class)
    private suspend fun onRemoveCastCharacter(intent: ScriptEditorUiIntent.RemoveCastCharacter) {
        val name = intent.name.trim()
        if (name.isBlank()) return
        mPack = mPack.copy(
            scenes = mPack.scenes.map { scene ->
                scene.copy(
                    cast = scene.cast.filterNot { it.name == name },
                    bannedCast = scene.bannedCast.filterNot { it == name }
                )
            }
        )
        mStore.save(mPack)
        publish(null, null)
    }

    @UiIntentObserver(ScriptEditorUiIntent.GeneratePackInfo::class)
    private suspend fun onGeneratePackInfo(intent: ScriptEditorUiIntent.GeneratePackInfo) {
        if (isStateOf<ScriptEditorUiState.Loading>()) return
        setGenerating("pack")
        val draft = runCatching {
            requestText(
                system = "你是剧本策划。根据下面这些信息写两份内容，用 === 分隔：\n" +
                    "第一部分是剧本梗概，150 字以内，说明世界观与玩家处境；\n" +
                    "第二部分是开场白，2 到 3 行，每行格式为「发言者|内容」，" +
                    "发言者留空表示旁白。\n只输出这两部分，不要额外解释。",
                user = buildString {
                    append("剧本名：").append(mPack.name).append('\n')
                    append("章节：").append(mPack.scenes.joinToString("、") { it.name })
                    // 有具体要求就以要求为准，没有才自由发挥
                    if (intent.hint.isNotBlank()) {
                        append("\n玩家的额外要求（优先满足）：").append(intent.hint.trim())
                    }
                }
            )
        }.getOrNull()
        if (draft.isNullOrBlank()) {
            setGenerating("")
            return
        }
        val parts = draft.split("===", limit = 2)
        val description = parts.firstOrNull().orEmpty().trim()
        val openings = parts.getOrNull(1).orEmpty().trim()
        mPack = mPack.copy(
            description = description.ifBlank { mPack.description },
            openings = openings
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { line ->
                    val separator = line.indexOf('|')
                    if (separator < 0) {
                        ScriptOpening(speaker = "", content = line)
                    } else {
                        ScriptOpening(
                            speaker = line.substring(0, separator).trim(),
                            content = line.substring(separator + 1).trim()
                        )
                    }
                }
        )
        mStore.save(mPack)
        setGenerating("")
        publish(null, null)
    }

    /** 让模型为某一章写内容草稿，追加到该章的进入提示里。 */
    @UiIntentObserver(ScriptEditorUiIntent.GenerateSceneContent::class)
    private suspend fun onGenerateSceneContent(intent: ScriptEditorUiIntent.GenerateSceneContent) {
        if (isStateOf<ScriptEditorUiState.Loading>()) return
        val scene = mPack.scenes.firstOrNull { it.id == intent.sceneId } ?: return
        setGenerating(intent.sceneId)
        val draft = runCatching {
            requestText(
                system = "你是剧本作者。根据剧本设定与本场景标题，写一段 200 字以内的场景描写，" +
                    "交代环境、在场角色的动作与开场对白。只输出正文，不要标题与解释。",
                user = buildString {
                    append("剧本：").append(mPack.name).append('\n')
                    append("梗概：").append(mPack.description).append('\n')
                    append("场景：").append(scene.name).append('\n')
                    append("在场角色：")
                        .append(scene.cast.joinToString("、") { it.name }.ifBlank { "未指定" })
                    if (intent.hint.isNotBlank()) {
                        append("\n玩家的额外要求（优先满足）：").append(intent.hint.trim())
                    }
                }
            )
        }.getOrNull()
        if (!draft.isNullOrBlank()) {
            mPack = mPack.copy(
                scenes = mPack.scenes.map { item ->
                    if (item.id == scene.id) {
                        item.copy(entryPrompt = draft.trim())
                    } else {
                        item
                    }
                }
            )
            mStore.save(mPack)
        }
        setGenerating("")
        publish(null, null)
    }

    /** 发一次不流式的文本请求，返回模型原始输出。 */
    private suspend fun requestText(system: String, user: String): String {
        val provider = mProviderSelectionResolver.requireDefaultProvider()
        val client = mClientFactory.create(provider.toConfig())
        val messages = listOf(
            me.kafuuneko.rpclient.libs.llm.model.LLMMessage(
                me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole.System,
                system
            ),
            me.kafuuneko.rpclient.libs.llm.model.LLMMessage(
                me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole.User,
                user
            )
        )
        val builder = StringBuilder()
        client.streamGenerate(
            me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest(
                messages = messages,
                model = provider.model
            )
        ).collect { event ->
            if (event is me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent.Delta) {
                builder.append(event.content)
            }
        }
        return builder.toString()
    }

    /** 标记哪一块正在生成，界面据此显示进度。 */
    private fun setGenerating(key: String) {
        val state = getOrNull<ScriptEditorUiState.Ready>() ?: return
        state.copy(generating = key).setup()
    }

    @UiIntentObserver(ScriptEditorUiIntent.UpdatePackInfo::class)
    private suspend fun onUpdatePackInfo(intent: ScriptEditorUiIntent.UpdatePackInfo) {
        val openings = intent.openingsText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                val separator = line.indexOf('|')
                if (separator < 0) {
                    ScriptOpening(speaker = "", content = line)
                } else {
                    ScriptOpening(
                        speaker = line.substring(0, separator).trim(),
                        content = line.substring(separator + 1).trim()
                    )
                }
            }
            .filter { it.content.isNotBlank() }
        mPack = mPack.copy(
            name = intent.name.ifBlank { mPack.name },
            description = intent.description.trim(),
            openings = openings,
            showCompletion = intent.showCompletion,
            shareSettings = intent.shareSettings
        )
        mStore.save(mPack)
        publish(null, null)
    }

    @UiIntentObserver(ScriptEditorUiIntent.UpdateScene::class)
    private suspend fun onUpdateScene(intent: ScriptEditorUiIntent.UpdateScene) {
        // 保存路径兜住异常：手打的名字、奇怪的轮次都可能踩到边界，
        // 崩掉会连累整页未保存的编辑
        runCatching { persistScene(intent) }.onFailure { error ->
            android.util.Log.e("ScriptEditor", "save scene failed", error)
            ScriptEditorViewEvent.ImportResult(
                success = false,
                detail = error.message.orEmpty(),
                savedPackCount = 0
            ).tryEmit()
        }
        publish(null, null)
    }

    private suspend fun persistScene(intent: ScriptEditorUiIntent.UpdateScene) {
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
                        nextSceneIds = splitNames(intent.nextSceneIds),
                        suggestedActions = intent.suggestedActions
                            .lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() },
                        inheritBackground = intent.inheritBackground
                    )
                }
            }
        )
        mStore.save(mPack)
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
            description = mPack.description,
            packBackground = mPack.backgroundKey,
            openingsText = mPack.openings.joinToString("\n") { opening ->
                if (opening.speaker.isBlank()) opening.content
                else opening.speaker + "|" + opening.content
            },
            showCompletion = mPack.showCompletion,
            shareSettings = mPack.shareSettings,
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
                    nextSceneIds = scene.nextSceneIds.joinToString("、"),
                    suggestedActions = scene.suggestedActions.joinToString("\n"),
                    inheritBackground = scene.inheritBackground
                )
            },
            editingSceneId = editingSceneId,
            editingField = editingField,
            settingsSceneId = settingsSceneId,
            characterNames = mCharacterNames,
            castInUse = mPack.scenes
                .flatMap { scene -> scene.cast.map { it.name } }
                .distinct(),
            missingBackgrounds = buildList {
                if (mPack.backgroundKey.isNotBlank()) add(mPack.backgroundKey)
                addAll(mPack.scenes.map { it.backgroundKey })
            }.filter { it.isNotBlank() }
                .distinct()
                .filterNot { it in mGalleryKeys },
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
