package me.kafuuneko.rpclient.feature.scriptplay

import java.util.UUID
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.scriptplay.presentation.ScriptPlayUiIntent
import me.kafuuneko.rpclient.feature.scriptplay.presentation.ScriptStatusItem
import me.kafuuneko.rpclient.feature.scriptplay.presentation.ScriptPlayUiState
import me.kafuuneko.rpclient.feature.scriptplay.presentation.ScriptPlayViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.script.ScriptPack
import me.kafuuneko.rpclient.libs.script.ScriptPlaySnapshot
import me.kafuuneko.rpclient.libs.script.ScriptPlayMessage
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.script.ScriptPlayLineParser
import me.kafuuneko.rpclient.libs.script.ScriptPlayOptionParser
import me.kafuuneko.rpclient.libs.script.ScriptPlayPromptBuilder
import me.kafuuneko.rpclient.libs.script.ScriptPlaySceneState
import me.kafuuneko.rpclient.libs.script.ScriptPlayStore
import me.kafuuneko.rpclient.libs.script.ScriptPlayEngine
import me.kafuuneko.rpclient.libs.script.ScriptPlayOutcome
import me.kafuuneko.rpclient.libs.script.ScriptPlaythrough
import me.kafuuneko.rpclient.libs.script.ScriptScene
import me.kafuuneko.rpclient.libs.script.ScriptStore
import me.kafuuneko.rpclient.libs.room.entity.toConfig
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 剧本模式状态持有者。
 *
 * 与群聊的差别集中在三处：
 * - 有一条明确的当前场景，场景决定世界书、出场角色与轮次上限；
 * - 每轮结束检查轮次上限并落盘，退出再进能续玩；
 * - 切换场景时保留对话历史，但轮次按场景重置。
 */
class ScriptPlayViewModel(
    private val mPackId: String,
    /**
     * 开局时带进来的活跃角色。
     *
     * 只在进入第一场时并进名单——它是「进来时谁在」的快照，
     * 不是随场景走的成员表。切场后必须清空，否则上一场的角色
     * 会一直挂在头像条上，跳到只有一个玩偶的场景也照挂不误。
     */
    private var mActiveCharacters: List<String>,
    private val mPlayId: String
) : CoreViewModelWithEvent<ScriptPlayUiIntent, ScriptPlayUiState>(
    ScriptPlayUiState.Loading
), KoinComponent {
    private val mScriptStore by inject<ScriptStore>()
    private val mPlayStore by inject<ScriptPlayStore>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mFileRepository by inject<me.kafuuneko.rpclient.libs.room.repository.FileRepository>()
    private val mGalleryStore by inject<me.kafuuneko.rpclient.libs.gallery.GalleryStore>()
    private val mProviderSelectionResolver by inject<LLMProviderSelectionResolver>()
    private val mClientFactory by inject<LLMClientFactory>()

    private var mPack: ScriptPack? = null

    /** 通关后进入自由模式：不再判失败，可自由走场景。 */
    private var mFreeMode = false

    /** 本轮给出的候选行动。 */
    private var mOptions: List<String> = emptyList()

    /** 角色名到头像 UUID 的缓存，供界面取头像。 */
    private val mAvatarCache = mutableMapOf<String, String>()

    /** 是否自动推进。 */
    private var mAutoPlay = false

    /** 连续自动推进轮数，用于防止无人值守时无限循环。 */
    private var mAutoRounds = 0

    /** 本轮禁言名单，取自界面状态。 */
    /** 当前场景的背景图 UUID，切场时预先解析好。 */
    private var mSceneBackground = ""

    private var mCurrentMuted: Set<String> = emptySet()

    /** 本轮指定发言角色，取自界面状态。 */
    private var mCurrentForcedSpeaker: String? = null

    /** 本轮自动挑出的发言人。 */
    private var mAutoSpeaker: String? = null
    private var mPlay: ScriptPlaythrough = ScriptPlaythrough(
        id = mPlayId,
        packId = mPackId,
        packName = "",
        sceneId = ""
    )

    /** 加载剧本与存档；没有存档时从起始场景新开一局。 */
    @UiIntentObserver(ScriptPlayUiIntent.Init::class)
    private suspend fun onInit() {
        if (isStateOf<ScriptPlayUiState.Ready>()) return
        val pack = mScriptStore.load(mPackId)
        if (pack == null) {
            ScriptPlayViewEvent.Toast("剧本不存在").tryEmit()
            ScriptPlayUiState.Finished.setup()
            return
        }
        mPack = pack
        val existing = mPlayStore.load(mPlayId)
        mPlay = existing ?: ScriptPlaythrough(
            id = mPlayId,
            packId = pack.id,
            packName = pack.name,
            sceneId = resolveStartScene(pack).id,
            // 新局按剧本声明的初值铺一遍变量
            variables = ScriptPlayEngine.initialVariables(pack)
        )
        // 进场先把当前场景的背景图解析好，publish 里直接读缓存
        mSceneBackground = runCatching {
            resolveSceneBackground(currentScene(pack) ?: return)
        }.getOrDefault("")
        publish()
    }


    /** 选择推荐行动，等价于把该行动当作玩家输入发送。 */
    @UiIntentObserver(ScriptPlayUiIntent.SelectOption::class)
    private suspend fun onSelectOption(intent: ScriptPlayUiIntent.SelectOption) {
        val state = getOrNull<ScriptPlayUiState.Ready>() ?: return
        if (state.generating || state.finishedReason.isNotBlank()) return
        mAutoPlay = state.autoPlay
        state.copy(input = intent.text).setup()
        onSend()
    }

    /** 切换自动推进：开启后每轮自动选第一个推荐行动。 */
    @UiIntentObserver(ScriptPlayUiIntent.ToggleAutoPlay::class)
    private suspend fun onToggleAutoPlay() {
        val state = getOrNull<ScriptPlayUiState.Ready>() ?: return
        val next = !state.autoPlay
        mAutoPlay = next
        mAutoRounds = 0
        state.copy(autoPlay = next).setup()
        // 开启瞬间若已有候选，立即推进一轮
        if (next) {
            state.options.firstOrNull()?.let { first ->
                if (!state.generating && state.finishedReason.isBlank()) {
                    state.copy(input = first, autoPlay = true).setup()
                    onSend()
                }
            }
        }
    }

    /** 输入框变化。 */
    @UiIntentObserver(ScriptPlayUiIntent.InputChanged::class)
    private fun onInputChanged(intent: ScriptPlayUiIntent.InputChanged) {
        val state = getOrNull<ScriptPlayUiState.Ready>() ?: return
        state.copy(input = intent.value).setup()
    }

    /** 发送玩家发言并生成一段回复。 */
    @UiIntentObserver(ScriptPlayUiIntent.Send::class)
    private suspend fun onSend() {
        val state = getOrNull<ScriptPlayUiState.Ready>() ?: return
        val pack = mPack ?: return
        if (state.generating || state.finishedReason.isNotBlank()) return
        val text = state.input.trim()
        if (text.isEmpty()) return

        val scene = currentScene(pack) ?: return
        // GM 干预只作用于本轮，发送时取当前界面状态
        mCurrentMuted = state.mutedCharacters
        mCurrentForcedSpeaker = state.forcedSpeaker
        // 没人手动指定时自动安排一个，多角色场景才不会一直同一个人说
        mAutoSpeaker = if (state.forcedSpeaker.isNullOrBlank()) {
            nextAutoSpeaker(scene, state.mutedCharacters)
        } else {
            null
        }
        mPlay = mPlay.copy(messages = mPlay.messages + ScriptPlayMessage(PLAYER, text))
        // 选项用掉就清空：选完之后还挂在屏幕上会一直挡住对话
        mOptions = emptyList()
        state.copy(
            input = "",
            generating = true,
            messages = mPlay.messages,
            options = emptyList()
        ).setup()

        val reply = runCatching { requestReply(pack, scene, text) }.getOrElse { error ->
            val current = getOrNull<ScriptPlayUiState.Ready>() ?: return
            current.copy(generating = false).setup()
            ScriptPlayViewEvent.Toast("生成失败：${error.message.orEmpty()}").tryEmit()
            return
        }

        // 一轮 = 一次往返，轮次在收到回复后累加
        // 选项块从正文剥离，单独作为候选行动展示
        val parsedReply = ScriptPlayOptionParser.parse(reply)
        val visibleReply = parsedReply.text.ifBlank { reply }
        mPlay = mPlay.copy(
            messages = mPlay.messages + ScriptPlayMessage(scene.name, visibleReply),
            roundCount = mPlay.roundCount + 1
        )
        // 判定同时看玩家说了什么与模型怎么演：
        // 只看回复的话，玩家说「去厨房」而模型换个说法就判不到
        val judgedText = text + "\n" + visibleReply
        when (val outcome = ScriptPlayEngine.evaluate(
            scene = scene,
            roundCount = mPlay.roundCount,
            lastReply = judgedText,
            freeMode = mFreeMode,
            variables = mPlay.variables.orEmpty()
        )) {
            is ScriptPlayOutcome.Failed -> {
                // 配了战败场景就先演惩罚：不同来源各走各的，
                // 没配才照常收官
                val punishScene = outcome.gotoSceneId
                    .takeIf { it.isNotBlank() }
                    ?.let { id -> pack.scenes.firstOrNull { it.id == id } }
                if (punishScene != null) {
                    enterScene(punishScene, outcome.reason)
                } else {
                    mPlay = mPlay.copy(finished = true, endReason = outcome.reason)
                }
            }

            is ScriptPlayOutcome.Cleared -> {
                // 本场达标先看有没有下一场：剧本是分章的，
                // 通关一章就结束整局等于把后面几章全吞掉
                val nextScene = scene.nextSceneIds
                    .asSequence()
                    .mapNotNull { id -> pack.scenes.firstOrNull { it.id == id } }
                    .firstOrNull()
                if (nextScene != null) {
                    enterScene(nextScene, outcome.reason)
                } else {
                    // 没有后继才算真的走完；允许的话转自由模式继续玩
                    mFreeMode = ScriptPlayEngine.shouldEnterFreeMode(pack, scene)
                    mPlay = mPlay.copy(
                        finished = !mFreeMode,
                        endReason = if (mFreeMode) "" else outcome.reason
                    )
                    if (mFreeMode) {
                        mPlay = mPlay.copy(
                            messages = mPlay.messages + ScriptPlayMessage(
                                SYSTEM,
                                "通关：${outcome.reason}，进入自由模式"
                            )
                        )
                    }
                }
            }

            ScriptPlayOutcome.Continue -> Unit
        }
        // 模型给的候选优先，它没写才用作者预设兜底：
        // 预设是保险，不该压住模型随机应变的空间
        mOptions = parsedReply.options.ifEmpty { scene.suggestedActions }
        // 指定发言是一次性的，用过就清，避免一直压着同一个角色
        mCurrentForcedSpeaker = null
        // 场景事件放在判定之后：一局没结束才继续改场面
        applySceneEvent(pack, scene, judgedText)
        mPlayStore.save(mPlay)
        publish()

        // 自动推进：有候选且未结束时接着走一轮，但限制连续轮数避免失控
        if (mAutoPlay && mOptions.isNotEmpty() && mPlay.endReason.isBlank() &&
            mAutoRounds < MAX_AUTO_ROUNDS
        ) {
            mAutoRounds++
            val first = mOptions.first()
            val current = getOrNull<ScriptPlayUiState.Ready>()
            if (current != null) {
                current.copy(input = first).setup()
                onSend()
            }
        } else if (!mAutoPlay) {
            mAutoRounds = 0
        }
    }

    /** 切换到指定场景，轮次按新场景重置。 */
    @UiIntentObserver(ScriptPlayUiIntent.SwitchScene::class)
    private suspend fun onSwitchScene(intent: ScriptPlayUiIntent.SwitchScene) {
        val pack = mPack ?: return
        val target = pack.scenes.firstOrNull { it.id == intent.sceneId } ?: return
        enterScene(target)
    }

    /**
     * 进入指定场景：重置轮次、压掉旧对白、换背景。
     *
     * [note] 是给玩家的转场说明，通关自动转场时带上达成理由。
     */
    private suspend fun enterScene(target: ScriptScene, note: String = "") {
        // 换场就不再是开始时那批人，名单交给新场景自己决定
        mActiveCharacters = emptyList()
        mPlay = mPlay.copy(
            sceneId = target.id,
            roundCount = 0,
            finished = false,
            // 换场作废上一场的事件改动，新场景按自己的 cast 起步
            castOverride = null,
            endReason = "",
            // 章与章之间是天然分段点：旧场景的对白逐字带过去没有意义，
            // 只留走过的场景轨迹和上一场最后一句话，上下文就压得很小
            messages = buildList {
                val trail = mPlay.messages
                    .filter { it.speaker == SYSTEM && it.content.startsWith("进入「") }
                    .takeLast(MAX_SCENE_TRAIL)
                addAll(trail)
                mPlay.messages
                    .lastOrNull { it.speaker != PLAYER && it.speaker != SYSTEM }
                    ?.let { add(it) }
                if (note.isNotBlank()) {
                    add(ScriptPlayMessage(SYSTEM, "上一场达成：$note"))
                }
                add(ScriptPlayMessage(SYSTEM, "进入「${target.name}」"))
            }
        )
        mSceneBackground = runCatching {
            resolveSceneBackground(target)
        }.getOrDefault("")
        // 进入检查点场景时记快照，走偏了能退回来
        if (target.checkpoint) {
            mPlay = mPlay.copy(
                snapshot = ScriptPlaySnapshot(
                    sceneId = target.id,
                    roundCount = 0,
                    messages = mPlay.messages
                )
            )
        }
        mPlayStore.save(mPlay)
        // 新场景可能带来没见过的角色，头像缓存要跟着补一遍
        loadAvatarCache()
        publish()
    }


    /**
     * 处理本轮触发的场景事件。
     *
     * 一次性事件记进 firedEvents；改 cast 写 castOverride；
     * 配了 gotoSceneId 的直接转场，转场后本场景的事件不再继续判定。
     */
    private suspend fun applySceneEvent(pack: ScriptPack, scene: ScriptScene, reply: String) {
        if (mPlay.finished || mPlay.endReason.isNotBlank()) return
        val event = ScriptPlayEngine.pickEvent(
            scene = scene,
            roundCount = mPlay.roundCount,
            lastReply = reply,
            firedEventKeys = mPlay.firedEvents.orEmpty().toSet(),
            variables = mPlay.variables.orEmpty()
        ) ?: return
        val key = ScriptPlayEngine.eventKey(scene, event)
        val baseCast = mPlay.castOverride
            ?: ScriptPlayEngine.effectiveCast(scene, mPlay.variables.orEmpty())
        val nextCast = ScriptPlayEngine.applyEventCast(baseCast, event)
        val note = event.narration.ifBlank { event.name }
        mPlay = mPlay.copy(
            firedEvents = (mPlay.firedEvents.orEmpty() + key).distinct(),
            castOverride = nextCast,
            variables = ScriptPlayEngine.applyEffects(
                variables = mPlay.variables.orEmpty(),
                effects = event.effects,
                declared = pack.variables
            ),
            // 文本变量走另一条路：位置是覆盖，线索是追加
            textVariables = ScriptPlayEngine.applyTextEffects(
                variables = mPlay.textVariables.orEmpty(),
                effects = event.effects,
                declared = pack.variables
            ),
            messages = if (note.isBlank()) {
                mPlay.messages
            } else {
                mPlay.messages + ScriptPlayMessage(SYSTEM, note)
            }
        )
        val nextId = event.gotoSceneId
        if (nextId.isNotBlank()) {
            pack.scenes.firstOrNull { it.id == nextId }?.let { enterScene(it, note) }
        }
    }

    /** 重开当前场景，保留历史并把轮次归零。 */

    /** 回到最近一次检查点；没有快照时不动作。 */
    @UiIntentObserver(ScriptPlayUiIntent.RestoreCheckpoint::class)
    private suspend fun onRestoreCheckpoint() {
        val snapshot = mPlay.snapshot ?: return
        mFreeMode = false
        mOptions = emptyList()
        mPlay = mPlay.copy(
            sceneId = snapshot.sceneId,
            roundCount = snapshot.roundCount,
            messages = snapshot.messages,
            finished = false,
            endReason = "",
            snapshot = snapshot
        )
        mSceneBackground = runCatching {
            currentSceneOrNull()?.let { resolveSceneBackground(it) }.orEmpty()
        }.getOrDefault("")
        mPlayStore.save(mPlay)
        publish()
    }

    @UiIntentObserver(ScriptPlayUiIntent.RestartScene::class)
    private suspend fun onRestartScene() {
        mPlay = mPlay.copy(roundCount = 0, finished = false, endReason = "")
        mPlayStore.save(mPlay)
        publish()
    }

    /** 返回。 */
    @UiIntentObserver(ScriptPlayUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<ScriptPlayUiState.Finished>()) return
        ScriptPlayUiState.Finished.setup()
    }


    /**
     * 让模型代写一句玩家行动。
     *
     * 结果只填进输入框，不直接发送——玩家可能想改措辞再发。
     * 用的还是当前场景上下文，所以写出来的行动与局势一致。
     */
    @UiIntentObserver(ScriptPlayUiIntent.Impersonate::class)
    private suspend fun onImpersonate() {
        val state = getOrNull<ScriptPlayUiState.Ready>() ?: return
        val pack = mPack ?: return
        if (state.generating || state.impersonating) return
        val scene = currentScene(pack) ?: return
        state.copy(impersonating = true).setup()
        val draft = runCatching { requestImpersonation(pack, scene) }.getOrElse { error ->
            val current = getOrNull<ScriptPlayUiState.Ready>() ?: return
            current.copy(impersonating = false).setup()
            ScriptPlayViewEvent.Toast("代写失败：${error.message.orEmpty()}").tryEmit()
            return
        }
        val current = getOrNull<ScriptPlayUiState.Ready>() ?: return
        current.copy(input = draft.trim(), impersonating = false).setup()
    }

    /**
     * 玩家轮空：把「沉默观察」作为隐式行动推进一轮。
     *
     * 不落成玩家发言，玩家消息里看不出这句是系统补的。
     */
    @UiIntentObserver(ScriptPlayUiIntent.ContinueWithoutInput::class)
    private suspend fun onContinueWithoutInput() {
        val state = getOrNull<ScriptPlayUiState.Ready>() ?: return
        if (state.generating || state.finishedReason.isNotBlank()) return
        state.copy(input = SILENT_ACTION).setup()
        onSend()
    }

    /** 请求一段玩家代写内容。 */
    private suspend fun requestImpersonation(pack: ScriptPack, scene: ScriptScene): String =
        withContext(Dispatchers.IO) {
            val provider = mProviderSelectionResolver.requireDefaultProvider()
            val client = mClientFactory.create(provider.toConfig())
            val system = buildString {
                append("你在为玩家代笔。当前剧本《").append(pack.name).append("》，场景：")
                append(scene.name).append("。\n")
                append("根据最近几轮局势，写一句玩家此刻最自然的行动或台词，20 字以内，")
                append("只输出这句话本身，不要旁白、不要引号、不要解释。")
            }
            val messages = buildList {
                add(LLMMessage(LLMMessageRole.System, system))
                ScriptPlayPromptBuilder.buildHistory(mPlay.messages, IMPERSONATE_HISTORY)
                    .forEach { message ->
                        add(
                            LLMMessage(
                                role = if (message.speaker == PLAYER) {
                                    LLMMessageRole.User
                                } else {
                                    LLMMessageRole.Assistant
                                },
                                content = "${message.speaker}：${message.content}"
                            )
                        )
                    }
            }
            client.generate(LLMGenerationRequest(messages = messages, model = provider.model)).content
        }

    /** 调用模型生成一段场景回复。 */
    private suspend fun requestReply(pack: ScriptPack, scene: ScriptScene, text: String): String =
        withContext(Dispatchers.IO) {
            val provider = mProviderSelectionResolver.requireDefaultProvider()
            val client = mClientFactory.create(provider.toConfig())
            val remaining = if (scene.maxRounds <= 0) 0 else (scene.maxRounds - mPlay.roundCount)
            val system = ScriptPlayPromptBuilder.buildSystemPrompt(
                pack = pack,
                scene = scene,
                roundCount = mPlay.roundCount + 1,
                remainingRounds = remaining,
                // 事件改过名单就按改后的走，没人时只剩旁白
                castOverride = mPlay.castOverride ?: ScriptPlayEngine.effectiveCast(
                scene = scene,
                variables = mPlay.variables.orEmpty()
            ),
            variables = mPlay.variables.orEmpty(),
            declaredVariables = pack.variables,
            textVariables = mPlay.textVariables.orEmpty(),
            castProfiles = buildCastProfiles(scene, pack)
            )
            val messages = buildList {
                add(LLMMessage(LLMMessageRole.System, system))
                ScriptPlayPromptBuilder.buildHistory(mPlay.messages, MAX_HISTORY_MESSAGES)
                    .forEach { message ->
                        add(
                            LLMMessage(
                                role = if (message.speaker == PLAYER) {
                                    LLMMessageRole.User
                                } else {
                                    LLMMessageRole.Assistant
                                },
                                content = "${message.speaker}：${message.content}"
                            )
                        )
                    }
                add(LLMMessage(LLMMessageRole.User, text))
            }
            client.generate(
                LLMGenerationRequest(messages = messages, model = provider.model)
            ).content
        }


    /**
     * 角色名到头像文件 UUID 的映射。
     *
     * 剧本按名字引用角色，界面要头像就得回查角色卡；
     * 查不到的（临时角色）返回空，界面退化成首字气泡。
     */

    /** 加载角色头像位图；失败时返回 null，界面退化成首字气泡。 */
    suspend fun loadAvatarBitmap(uuid: String): androidx.compose.ui.graphics.ImageBitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                mFileRepository.loadSampledBitmap(
                    uuid = uuid,
                    requestedWidthPx = 96,
                    requestedHeightPx = 96
                )?.asImageBitmap()
            }.getOrNull()
        }

    fun avatarOf(name: String): String? = mAvatarCache[name]

    /**
     * 预加载剧本里出现过的角色头像。
     *
     * 名单要覆盖两层：对话里说过话的人，以及剧本里编排过的所有登场角色。
     * 只看发言者的后果是——角色进场了却因为还没开口而没有头像，
     * 头像条上只剩一个字，看起来像是没配图。
     */
    private suspend fun loadAvatarCache() {
        val names = buildSet {
            mPlay.messages.forEach { message ->
                ScriptPlayLineParser.parse(message.content)
                    .mapNotNull { it.speaker }
                    .forEach { add(it) }
            }
            mPack?.scenes?.forEach { scene ->
                scene.cast.forEach { add(it.name) }
                scene.conditionalCast.forEach { add(it.name) }
            }
        }
        if (names.isEmpty()) return
        val characters = runCatching { mCharacterRepository.getAllCharacters() }.getOrDefault(emptyList())
        characters.forEach { character ->
            if (character.name in names) {
                mAvatarCache[character.name] = character.avatar
            }
        }
    }

    /** 起始场景：多入口取第一个，否则取缺省值。 */
    private fun resolveStartScene(pack: ScriptPack): ScriptScene =
        pack.scenes.firstOrNull { it.id == pack.startSceneId } ?: pack.scenes.first()

    /** 当前场景；存档里的场景被删掉时回落到起始场景。 */
    private fun currentScene(pack: ScriptPack): ScriptScene? =
        pack.scenes.firstOrNull { it.id == mPlay.sceneId } ?: resolveStartScene(pack)

    /** 重新发布状态。 */
    private fun publish() {
        val pack = mPack ?: return
        val scene = currentScene(pack) ?: return
        // 存档里的场景名可能已被改过，这里以剧本为准
        if (mPlay.sceneId != scene.id) {
            mPlay = mPlay.copy(sceneId = scene.id)
        }
        ScriptPlayUiState.Ready(
            packName = pack.name,
            hasCheckpoint = mPlay.snapshot != null,
            statusItems = pack.variables
                .filter { variable ->
                    // 场景点名了就用点名的，没点名才按剧本级可见性
                    val listed = scene.dashboardItems
                    if (listed.isEmpty()) {
                        variable.visible
                    } else {
                        variable.name in listed
                    }
                }
                .filter { it.name.isNotBlank() }
                .map { variable ->
                    ScriptStatusItem(
                        label = variable.label.ifBlank { variable.name },
                        value = mPlay.variables.orEmpty()[variable.name] ?: variable.initial,
                        max = if (variable.max in 1..9999) variable.max else 0,
                        kind = variable.kind,
                        text = mPlay.textVariables.orEmpty()[variable.name]
                            ?: variable.text,
                        group = variable.group,
                        onLabel = variable.onLabel,
                        offLabel = variable.offLabel
                    )
                },
            showDashboard = pack.showDashboard,
            scene = ScriptPlaySceneState(
                sceneId = scene.id,
                sceneName = scene.name,
                maxRounds = scene.maxRounds,
                roundCount = mPlay.roundCount,
                // 自由漫游时列出所有已解锁场景，不再只走主线后继
                nextSceneIds = if (mFreeMode) {
                    mPack?.let { pack ->
                        ScriptPlayEngine.reachableScenes(pack, scene, mPlay.variables.orEmpty())
                            .map { it.id }
                    } ?: scene.nextSceneIds
                } else {
                    scene.nextSceneIds
                },
                checkpoint = scene.checkpoint
            ),
            messages = mPlay.messages,
            finishedReason = mPlay.endReason,
            // 没有新候选时回落到场景预设：进场景立刻就能点，
            // 不至于等模型输出格式块才出现选项
            options = mOptions.ifEmpty {
                currentSceneOrNull()?.suggestedActions.orEmpty()
            },
            backgroundUuid = mSceneBackground,
            castNames = run {
                val fromScene = mPlay.castOverride
                    ?: currentSceneOrNull()?.let { scene ->
                        ScriptPlayEngine.effectiveCast(scene, mPlay.variables.orEmpty())
                    }.orEmpty()
                val merged = (fromScene + mActiveCharacters).distinct()
                android.util.Log.d(
                    "ScriptPlay",
                    "scene=" + mPlay.sceneId + " override=" + mPlay.castOverride +
                        " active=" + mActiveCharacters + " => " + merged
                )
                merged
            },
            autoPlay = mAutoPlay
        ).setup()
    }

    private companion object {
        /** 玩家在存档里的发言者名。 */
        const val PLAYER = "玩家"

        /** 系统提示在存档里的发言者名。 */
        const val SYSTEM = "系统"

        /** 连续自动推进的最大轮数，防止无人值守时无限消耗额度。 */
        const val MAX_AUTO_ROUNDS = 5

        /** 玩家轮空时使用的隐式行动文本。 */
        const val SILENT_ACTION = "（沉默观察，让局势自然发展）"

        /** 代写时参考的历史条数。 */
        const val IMPERSONATE_HISTORY = 8

        /** 送给模型的历史条数上限。 */
        const val MAX_HISTORY_MESSAGES = 20
        /** 切场时保留的场景轨迹条数，够交代走过哪几张图就行。 */
        const val MAX_SCENE_TRAIL = 4
    }

    /** 当前场景，取不到时返回 null。 */
    private fun currentSceneOrNull(): ScriptScene? = mPack?.let { currentScene(it) }

    /**
     * 按轮转池挑本轮发言人。
     *
     * 从场景名单里优先选「用户上一句之后还没说过话」的角色，
     * 都说过一轮了再从头开始，避免整场只有一个人在讲话。
     * 禁言中的角色直接排除。
     */
    /**
     * 拼本次在场角色的档案。
     *
     * 优先用场景里写的设定，其次回查角色卡的描述与性格；
     * 两边都没有就留空，模型至少还知道名字。
     */
    private suspend fun buildCastProfiles(
        scene: ScriptScene,
        pack: ScriptPack
    ): Map<String, String> {
        val names = mPlay.castOverride
            ?: ScriptPlayEngine.effectiveCast(scene, mPlay.variables.orEmpty())
        if (names.isEmpty()) return emptyMap()
        val fromScene = scene.cast.associate { it.name to it.description }
        val fromConditional = scene.conditionalCast.associate { it.name to it.description }
        val cards = runCatching {
            mCharacterRepository.getAllCharacters().associateBy { it.name }
        }.getOrDefault(emptyMap())
        return names.associateWith { name ->
            val written = (fromScene[name] ?: fromConditional[name]).orEmpty()
            if (written.isNotBlank()) {
                written
            } else {
                // 场景没写就回查角色卡，拼性格与设定
                cards[name]?.let { card ->
                    listOf(card.description, card.personality)
                        .filter { it.isNotBlank() }
                        .joinToString("；")
                }.orEmpty()
            }
        }.filterValues { it.isNotBlank() }
    }

    private fun nextAutoSpeaker(scene: ScriptScene, muted: Set<String>): String? {
        val cast = scene.cast.map { it.name }.filterNot { it in muted }
        if (cast.isEmpty()) return null
        if (cast.size == 1) return cast.first()
        // 最近一段连续的角色发言即为本轮已说过的
        val spoken = mPlay.messages
            .takeLastWhile { it.speaker != PLAYER }
            .map { it.speaker }
            .toSet()
        val pending = cast.filterNot { it in spoken }
        val pool = pending.ifEmpty { cast }
        return pool.randomOrNull()
    }

    /** Open the GM panel. */
    @UiIntentObserver(ScriptPlayUiIntent.OpenGmPanel::class)
    private fun onOpenGmPanel() {
        val state = getOrNull<ScriptPlayUiState.Ready>() ?: return
        state.copy(gmPanelVisible = true).setup()
    }

    /** Close the GM panel. */
    @UiIntentObserver(ScriptPlayUiIntent.CloseGmPanel::class)
    private fun onCloseGmPanel() {
        val state = getOrNull<ScriptPlayUiState.Ready>() ?: return
        state.copy(gmPanelVisible = false).setup()
    }

    /** Toggle mute for one character. */
    @UiIntentObserver(ScriptPlayUiIntent.ToggleMute::class)
    private fun onToggleMute(intent: ScriptPlayUiIntent.ToggleMute) {
        val state = getOrNull<ScriptPlayUiState.Ready>() ?: return
        val next = if (intent.name in state.mutedCharacters) {
            state.mutedCharacters - intent.name
        } else {
            state.mutedCharacters + intent.name
        }
        state.copy(mutedCharacters = next).setup()
    }

    /** Force one character to speak next round; tap again to clear. */
    @UiIntentObserver(ScriptPlayUiIntent.ForceSpeaker::class)
    private fun onForceSpeaker(intent: ScriptPlayUiIntent.ForceSpeaker) {
        val state = getOrNull<ScriptPlayUiState.Ready>() ?: return
        val next = if (state.forcedSpeaker == intent.name) null else intent.name
        state.copy(forcedSpeaker = next).setup()
    }


    /**
     * 按当前场景的检索词从图库找背景图。
     *
     * 纯本地匹配，不调用模型也不生成图片：
     * 同一场景每次进来都拿同一张，玩家不会看到背景乱跳。
     */
    private suspend fun resolveSceneBackground(scene: ScriptScene): String {
        // 场景自己指定了就用它，优先于一切
        scene.backgroundKey.takeIf { it.isNotBlank() }?.let { return searchBackground(it) }
        if (scene.inheritBackground) {
            // 没指定且允许继承：用剧本级那张
            mPack?.backgroundKey?.takeIf { it.isNotBlank() }?.let { return searchBackground(it) }
            // 剧本级也没写，按场景名自动找一张，保持旧行为
            return searchBackground(scene.name)
        }
        // 关闭继承又没有自己的图：这一场明确不要背景
        return ""
    }

    /** 按关键词在图库里找一张背景，返回文件 UUID。 */
    private suspend fun searchBackground(keyword: String): String {
        if (keyword.isBlank()) return ""
        val hits = runCatching { mGalleryStore.search(keyword) }.getOrDefault(emptyList())
        return hits.firstOrNull()?.fileUuid.orEmpty()
    }
}
