package me.kafuuneko.rpclient.feature.assistant

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantMessageItem
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantRole
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantUiIntent
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantUiState
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantViewEvent
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.assistant.AssistantMessage
import me.kafuuneko.rpclient.libs.assistant.AssistantPromptBuilder
import me.kafuuneko.rpclient.libs.assistant.AssistantStore
import me.kafuuneko.rpclient.libs.assistant.AssistantToolCatalog
import me.kafuuneko.rpclient.libs.assistant.AssistantToolExecutor
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.room.entity.toConfig
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 助手页状态持有者。
 *
 * 职责与调度：
 * - 维护一条文本会话，消息落盘后仍保留内存副本用于构建请求；
 * - 每轮生成后解析工具调用并执行，把结果回填再继续，直到模型不再调用工具；
 * - 提示词来源（继承全局 / 独立）读写 AppModel，切换后下一轮请求立即生效。
 */
class AssistantViewModel : CoreViewModelWithEvent<AssistantUiIntent, AssistantUiState>(
    AssistantUiState.Loading
), KoinComponent {
    private val mStore by inject<AssistantStore>()
    private val mToolExecutor by inject<AssistantToolExecutor>()
    private val mProviderSelectionResolver by inject<LLMProviderSelectionResolver>()
    private val mClientFactory by inject<LLMClientFactory>()

    /** 会话内存副本；与磁盘保持同步。 */
    private val mMessages = mutableListOf<AssistantMessage>()

    /** 当前生成任务，供「停止」取消。 */
    private var mGenerationJob: Job? = null

    /** 读取历史会话，并把当前提示词与沙箱状态带进首帧。 */
    @UiIntentObserver(AssistantUiIntent.Init::class)
    private suspend fun onInit() {
        if (isStateOf<AssistantUiState.Ready>()) return
        val stored = mStore.load()
        mMessages.clear()
        mMessages.addAll(stored)
        AssistantUiState.Ready(
            items = mMessages.toItems(),
            promptHint = AssistantPromptBuilder.describeSource(),
            sandboxHint = mStore.sandboxSummary(),
            inheritGlobalPrompt = AppModel.assistantInheritGlobalPrompt,
            customPrompt = AppModel.assistantSystemPrompt
        ).setup()
    }

    /** 输入框内容变化。 */
    @UiIntentObserver(AssistantUiIntent.InputChanged::class)
    private fun onInputChanged(intent: AssistantUiIntent.InputChanged) {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(input = intent.text).setup()
    }

    /** 提交输入并启动生成。 */
    @UiIntentObserver(AssistantUiIntent.Send::class)
    private fun onSend() {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        if (state.isGenerating) return
        val text = state.input.trim()
        if (text.isEmpty()) return
        if (mMessages.isEmpty()) {
            AssistantViewEvent.Toast("助手已就绪").tryEmit()
        }

        // 先落用户消息，保证中断后历史仍然完整
        mMessages += AssistantMessage(role = ROLE_USER, content = text)
        state.copy(
            items = mMessages.toItems(),
            input = "",
            isGenerating = true,
            streamingReply = "",
            statusText = ""
        ).setup()
        mGenerationJob = viewModelScope.launch { runGeneration() }
    }

    /** 中止当前生成，保留已生成的部分。 */
    @UiIntentObserver(AssistantUiIntent.Stop::class)
    private fun onStop() {
        mGenerationJob?.cancel()
        mGenerationJob = null
    }

    /** 清空会话历史与磁盘文件。 */
    @UiIntentObserver(AssistantUiIntent.ClearSession::class)
    private suspend fun onClearSession() {
        mGenerationJob?.cancel()
        mGenerationJob = null
        mMessages.clear()
        mStore.clear()
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(
            items = emptyList(),
            streamingReply = "",
            isGenerating = false,
            statusText = "",
            sandboxHint = mStore.sandboxSummary()
        ).setup()
    }

    /** 打开提示词编辑弹窗。 */
    @UiIntentObserver(AssistantUiIntent.OpenPromptEditor::class)
    private fun onOpenPromptEditor() {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(promptEditorVisible = true).setup()
    }

    /** 关闭提示词编辑弹窗。 */
    @UiIntentObserver(AssistantUiIntent.ClosePromptEditor::class)
    private fun onClosePromptEditor() {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(promptEditorVisible = false).setup()
    }

    /** 切换提示词来源；写回 AppModel 后同步页面提示。 */
    @UiIntentObserver(AssistantUiIntent.PromptModeChanged::class)
    private fun onPromptModeChanged(intent: AssistantUiIntent.PromptModeChanged) {
        AppModel.assistantInheritGlobalPrompt = intent.inheritGlobal
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(
            inheritGlobalPrompt = intent.inheritGlobal,
            promptHint = AssistantPromptBuilder.describeSource()
        ).setup()
    }

    /** 修改独立提示词并持久化。 */
    @UiIntentObserver(AssistantUiIntent.CustomPromptChanged::class)
    private fun onCustomPromptChanged(intent: AssistantUiIntent.CustomPromptChanged) {
        AppModel.assistantSystemPrompt = intent.text
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(customPrompt = intent.text).setup()
    }

    /**
     * 执行一轮会话：生成 → 解析工具 → 回填 → 继续，直到不再有工具调用。
     *
     * 轮数上限由 [AssistantToolCatalog.MAX_TOOL_ROUNDS] 控制，避免模型反复调用同一工具。
     */
    private suspend fun runGeneration() {
        var rounds = 0
        try {
            while (true) {
                val reply = streamReply()
                val calls = AssistantToolCatalog.parse(reply)
                val visible = AssistantToolCatalog.stripToolBlocks(reply)
                if (visible.isNotBlank()) {
                    mMessages += AssistantMessage(role = ROLE_ASSISTANT, content = visible)
                    persistAndPublish()
                }
                if (calls.isEmpty() || rounds >= AssistantToolCatalog.MAX_TOOL_ROUNDS) break
                rounds++
                for (call in calls) {
                    updateStatus("正在执行 ${call.name}")
                    val result = mToolExecutor.execute(call)
                    mMessages += AssistantMessage(
                        role = ROLE_TOOL,
                        content = result,
                        toolName = call.name
                    )
                    persistAndPublish()
                }
            }
            finishGeneration()
        } catch (cancellation: CancellationException) {
            // 用户主动停止：保留已经产出的内容
            persistAndPublish()
            finishGeneration()
            throw cancellation
        } catch (error: Exception) {
            val message = error.message ?: error::class.simpleName ?: "未知错误"
            AssistantViewEvent.Toast("生成失败：$message").tryEmit()
            finishGeneration()
        }
    }

    /** 调用当前全局模型配置，流式收集一轮完整回复。 */
    private suspend fun streamReply(): String {
        val provider = mProviderSelectionResolver.requireDefaultProvider()
        val client = mClientFactory.create(provider.toConfig())
        val request = LLMGenerationRequest(
            messages = buildRequestMessages(),
            model = provider.model
        )
        val builder = StringBuilder()
        client.streamGenerate(request).collect { event ->
            when (event) {
                is LLMStreamEvent.Delta -> {
                    builder.append(event.content)
                    publishStreaming(builder.toString())
                }

                is LLMStreamEvent.ReasoningDelta -> Unit
                is LLMStreamEvent.Finished -> Unit
                else -> Unit
            }
        }
        return builder.toString()
    }

    /** 构建请求消息：系统提示词 + 最近若干轮上下文，工具结果按文本回填。 */
    private fun buildRequestMessages(): List<LLMMessage> {
        val messages = mutableListOf<LLMMessage>()
        messages += LLMMessage(
            role = LLMMessageRole.System,
            content = AssistantPromptBuilder.buildSystemPrompt()
        )
        mMessages.takeLast(MAX_CONTEXT_MESSAGES).forEach { message ->
            messages += when (message.role) {
                ROLE_ASSISTANT -> LLMMessage(LLMMessageRole.Assistant, message.content)
                ROLE_TOOL -> LLMMessage(
                    role = LLMMessageRole.User,
                    content = "<tool_result name=\"${message.toolName ?: "unknown"}\">\n" +
                        message.content + "\n</tool_result>"
                )

                else -> LLMMessage(LLMMessageRole.User, message.content)
            }
        }
        return messages
    }

    /** 落盘并刷新页面消息列表。 */
    private suspend fun persistAndPublish() {
        mStore.save(mMessages)
        publishItems()
    }

    /** 刷新消息列表，同时清掉流式草稿。 */
    private fun publishItems() {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(items = mMessages.toItems(), sandboxHint = mStore.sandboxSummary()).setup()
    }

    /** 刷新流式输出文本。 */
    private fun publishStreaming(text: String) {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(streamingReply = text).setup()
    }

    /** 更新阶段性状态提示。 */
    private fun updateStatus(text: String) {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(statusText = text).setup()
    }

    /** 结束生成，恢复可输入状态。 */
    private fun finishGeneration() {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(
            items = mMessages.toItems(),
            isGenerating = false,
            streamingReply = "",
            statusText = "",
            sandboxHint = mStore.sandboxSummary()
        ).setup()
    }

    /** 把存储消息映射成展示条目。 */
    private fun List<AssistantMessage>.toItems(): List<AssistantMessageItem> = map { message ->
        when (message.role) {
            ROLE_USER -> AssistantMessageItem(AssistantRole.User, message.content)
            ROLE_TOOL -> AssistantMessageItem(
                role = AssistantRole.Tool,
                content = message.content,
                toolName = message.toolName
            )

            else -> AssistantMessageItem(AssistantRole.Assistant, message.content)
        }
    }

    private companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"

        /** 请求携带的最近消息条数；助手场景不需要整段历史。 */
        const val MAX_CONTEXT_MESSAGES = 40
    }
}
