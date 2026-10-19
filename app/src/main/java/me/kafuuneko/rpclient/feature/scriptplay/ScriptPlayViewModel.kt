package me.kafuuneko.rpclient.feature.scriptplay

import java.util.UUID
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
    private val mPlayId: String
) : CoreViewModelWithEvent<ScriptPlayUiIntent, ScriptPlayUiState>(
    ScriptPlayUiState.Loading
), KoinComponent {
    private val mScriptStore by inject<ScriptStore>()
    private val mPlayStore by inject<ScriptPlayStore>()
    private val mProviderSelectionResolver by inject<LLMProviderSelectionResolver>()
    private val mClientFactory by inject<LLMClientFactory>()

    private var mPack: ScriptPack? = null

    /** 通关后进入自由模式：不再判失败，可自由走场景。 */
    private var mFreeMode = false
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
        publish()
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
        mPlay = mPlay.copy(messages = mPlay.messages + ScriptPlayMessage(PLAYER, text))
        state.copy(input = "", generating = true, messages = mPlay.messages).setup()

        val reply = runCatching { requestReply(pack, scene, text) }.getOrElse { error ->
            val current = getOrNull<ScriptPlayUiState.Ready>() ?: return
            current.copy(generating = false).setup()
            ScriptPlayViewEvent.Toast("生成失败：${error.message.orEmpty()}").tryEmit()
            return
        }

        // 一轮 = 一次往返，轮次在收到回复后累加
        mPlay = mPlay.copy(
            messages = mPlay.messages + ScriptPlayMessage(scene.name, reply),
            roundCount = mPlay.roundCount + 1
        )
        when (val outcome = ScriptPlayEngine.evaluate(
            scene = scene,
            roundCount = mPlay.roundCount,
            lastReply = reply,
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
        mPlayStore.save(mPlay)
        publish()
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
            finishedReason = mPlay.endReason
        ).setup()
    }

    private companion object {
        /** 玩家在存档里的发言者名。 */
        const val PLAYER = "玩家"

        /** 系统提示在存档里的发言者名。 */
        const val SYSTEM = "系统"

        /** 送给模型的历史条数上限。 */
        const val MAX_HISTORY_MESSAGES = 20
    }
}
