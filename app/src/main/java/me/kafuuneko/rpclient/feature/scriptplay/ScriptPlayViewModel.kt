package me.kafuuneko.rpclient.feature.scriptplay

import java.util.UUID
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.scriptplay.presentation.ScriptPlayUiIntent
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
    private val mActiveCharacters: List<String>,
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
            sceneId = resolveStartScene(pack).id
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
        mPlay = mPlay.copy(messages = mPlay.messages + ScriptPlayMessage(PLAYER, text))
        state.copy(input = "", generating = true, messages = mPlay.messages).setup()

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
        when (val outcome = ScriptPlayEngine.evaluate(
            scene = scene,
            roundCount = mPlay.roundCount,
            lastReply = visibleReply,
            freeMode = mFreeMode
        )) {
            is ScriptPlayOutcome.Failed -> {
                mPlay = mPlay.copy(finished = true, endReason = outcome.reason)
            }

            is ScriptPlayOutcome.Cleared -> {
                // 通关后若剧本允许，自动转入自由模式继续玩
                mFreeMode = ScriptPlayEngine.shouldEnterFreeMode(pack, scene)
                mPlay = mPlay.copy(
                    finished = !mFreeMode,
                    endReason = if (mFreeMode) "" else outcome.reason
                )
                if (mFreeMode) {
                    mPlay = mPlay.copy(
                        messages = mPlay.messages + ScriptPlayMessage(SYSTEM, "通关：${outcome.reason}，进入自由模式")
                    )
                }
            }

            ScriptPlayOutcome.Continue -> Unit
        }
        mOptions = parsedReply.options
        // 指定发言是一次性的，用过就清，避免一直压着同一个角色
        mCurrentForcedSpeaker = null
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
        mPlay = mPlay.copy(
            sceneId = target.id,
            roundCount = 0,
            finished = false,
            endReason = "",
            messages = mPlay.messages + ScriptPlayMessage(SYSTEM, "进入「${target.name}」")
        )
        mSceneBackground = runCatching {
            resolveSceneBackground(currentScene(mPack ?: return) ?: return)
        }.getOrDefault("")
        mPlayStore.save(mPlay)
        publish()
    }

    /** 重开当前场景，保留历史并把轮次归零。 */
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
                remainingRounds = remaining
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

    /** 预加载剧本里出现过的角色头像。 */
    private suspend fun loadAvatarCache() {
        val names = mPlay.messages.mapNotNull { message ->
            ScriptPlayLineParser.parse(message.content)
                .mapNotNull { it.speaker }
        }.flatten().toSet()
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
            scene = ScriptPlaySceneState(
                sceneId = scene.id,
                sceneName = scene.name,
                maxRounds = scene.maxRounds,
                roundCount = mPlay.roundCount,
                nextSceneIds = scene.nextSceneIds,
                checkpoint = scene.checkpoint
            ),
            messages = mPlay.messages,
            finishedReason = mPlay.endReason,
            options = mOptions,
            backgroundUuid = mSceneBackground,
            castNames = (currentSceneOrNull()?.cast?.map { it.name }.orEmpty() +
                mActiveCharacters).distinct(),
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
    }

    /** 当前场景，取不到时返回 null。 */
    private fun currentSceneOrNull(): ScriptScene? = mPack?.let { currentScene(it) }

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
        val keyword = scene.backgroundKey.ifBlank { scene.name }
        val hits = runCatching { mGalleryStore.search(keyword) }.getOrDefault(emptyList())
        return hits.firstOrNull()?.fileUuid.orEmpty()
    }
}