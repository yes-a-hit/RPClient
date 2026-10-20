package me.kafuuneko.rpclient.feature.assistant

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantAttachment
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantMessageItem
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantRole
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantUiIntent
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantUiState
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantViewEvent
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.assistant.AssistantImportResult
import me.kafuuneko.rpclient.libs.assistant.AssistantMessage
import me.kafuuneko.rpclient.libs.assistant.AssistantPromptBuilder
import me.kafuuneko.rpclient.libs.assistant.AssistantSessionCreateResult
import me.kafuuneko.rpclient.libs.assistant.AssistantSessionStore
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
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import android.net.Uri

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
    private val mSessionStore by inject<AssistantSessionStore>()
    private val mToolExecutor by inject<AssistantToolExecutor>()
    private val mProviderSelectionResolver by inject<LLMProviderSelectionResolver>()
    private val mClientFactory by inject<LLMClientFactory>()
    private val mFileRepository by inject<FileRepository>()
    private val mContext by inject<android.content.Context>()

    /** 当前会话 ID；空表示尚未确定，首次加载时创建或选取最新会话。 */
    private var mSessionId: String = ""

    /** 本轮待附加的图片引用，只随下一次请求发送。 */
    private var mPendingImages: List<LLMImageReference> = emptyList()

    /** 本轮已收到的部分回复；断流时用它保住已生成的内容。 */
    /** 本轮断流后的自动重试次数。 */
    private var mRetryCount = 0

    private val mPartialReply = StringBuilder()

    /** 本轮导入沙箱的文本文件名，用于在消息里补一句提示。 */
    private var mImportedFileNames: List<String> = emptyList()

    /** 会话内存副本；与磁盘保持同步。 */
    private val mMessages = mutableListOf<AssistantMessage>()

    /** 当前生成任务，供「停止」取消。 */
    private var mGenerationJob: Job? = null

    /** 读取历史会话，并把当前提示词与沙箱状态带进首帧。 */
    @UiIntentObserver(AssistantUiIntent.Init::class)
    private suspend fun onInit() {
        if (isStateOf<AssistantUiState.Ready>()) return
        // 首次进入：优先继续最近会话，没有就新建
        if (mSessionId.isBlank()) {
            ensureSession()
        }
        val stored = mSessionStore.loadSession(mSessionId)
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
    private suspend fun onSend() {
        var state = getOrNull<AssistantUiState.Ready>() ?: return
        if (state.isGenerating) {
            // 生成任务已经不在了但界面还停在「生成中」，属于断流后的残留状态。
            // 这时如果直接 return，玩家的输入会被静默吞掉，必须先把状态拉回来。
            if (mGenerationJob?.isActive == true) return
            finishGeneration()
            state = getOrNull<AssistantUiState.Ready>() ?: return
        }
        val rawText = state.input.trim()
        // 纯附件也可以发送，因此只在没有文字也没有附件时才拦下
        if (rawText.isEmpty() && state.attachments.isEmpty()) return
        mRetryCount = 0
        if (mMessages.isEmpty()) {
            AssistantViewEvent.Toast("助手已就绪").tryEmit()
        }

        // 先落用户消息，保证中断后历史仍然完整
        val prepared = buildAttachmentPayload(state.attachments, rawText, mImportedFileNames)
        mImportedFileNames = emptyList()
        mMessages += AssistantMessage(role = ROLE_USER, content = prepared.text)
        state.copy(
            items = mMessages.toItems(),
            input = "",
            attachments = emptyList(),
            isGenerating = true,
            streamingReply = "",
            statusText = ""
        ).setup()
        mGenerationJob = viewModelScope.launch { runGeneration() }
    }

    /** 中止当前生成，保留已生成的部分。 */

    /**
     * 重新生成上一条回复。
     *
     * 把最后一条玩家消息之后的内容全部丢掉再重跑，
     * 工具执行结果一并清除，避免旧结果污染新一轮。
     */
    @UiIntentObserver(AssistantUiIntent.Regenerate::class)
    private suspend fun onRegenerate() {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        if (state.isGenerating) return
        val lastUserIndex = mMessages.indexOfLast { it.role == ROLE_USER }
        if (lastUserIndex < 0) return
        while (mMessages.size > lastUserIndex + 1) {
            mMessages.removeAt(mMessages.lastIndex)
        }
        persistAndPublish()
        val current = getOrNull<AssistantUiState.Ready>() ?: return
        current.copy(isGenerating = true, streamingReply = ", statusText = ").setup()
        mGenerationJob = viewModelScope.launch { runGeneration() }
    }

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
        mSessionStore.deleteSession(mSessionId)
        ensureSession()
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


    /** 选择附件：记录 URI 与显示名，发送时才真正读取内容。 */
    /**
     * 选择文件：图片作为附件随消息发送，文本直接复制进沙箱。
     *
     * 文本塞进消息正文会迅速吃光上下文，多个 txt 一起选更是如此，
     * 所以文本一律进沙箱，由助手的 list_files / read_file 按需读取。
     */
    @UiIntentObserver(AssistantUiIntent.AttachFiles::class)
    private suspend fun onAttachFiles(intent: AssistantUiIntent.AttachFiles) {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        val images = mutableListOf<AssistantAttachment>()
        val textUris = mutableListOf<Uri>()
        intent.uris.forEach { uri ->
            val name = queryDisplayName(uri)
            if (name.isBlank()) return@forEach
            val mime = mContext.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("image/")) {
                images += AssistantAttachment(
                    uri = uri.toString(),
                    name = name,
                    isImage = true,
                    mimeType = mime,
                    byteCount = querySize(uri)
                )
            } else {
                textUris += uri
            }
        }

        // 文本立刻落进沙箱，界面上只留一条导入结果提示
        var importedNames = emptyList<String>()
        if (textUris.isNotEmpty()) {
            val result = mStore.importFiles(textUris)
            importedNames = textUris.mapNotNull { uri ->
                queryDisplayName(uri).ifBlank { null }
            }.take(result.importedCount)
            mImportedFileNames = importedNames
        }

        val merged = (state.attachments + images).distinctBy { it.uri }
        state.copy(
            attachments = merged,
            sandboxHint = mStore.sandboxSummary()
        ).setup()
    }

    /** 移除一条待发送附件。 */
    @UiIntentObserver(AssistantUiIntent.RemoveAttachment::class)
    private fun onRemoveAttachment(intent: AssistantUiIntent.RemoveAttachment) {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(attachments = state.attachments.filterNot { it.uri == intent.uri })
            .setup()
    }

    /**
     * 把附件整理成一条待发送内容。
     *
     * 文本附件直接拼进正文，图片附件转成多模态块暂存，
     * 由 [buildRequestMessages] 附加到最后一条用户消息上。
     */
    /**
     * 把附件整理成一条待发送内容。
     *
     * 图片转成多模态块随消息发送；文本文件在选取时就已经复制进沙箱，
     * 这里只补一句沙箱清单提示，不再把全文塞进正文——
     * 正文塞满多个 txt 会把上下文吃光，表现就是对话突然断掉。
     */
    private suspend fun buildAttachmentPayload(
        attachments: List<AssistantAttachment>,
        rawText: String,
        importedFileNames: List<String>
    ): PreparedPayload {
        val textParts = mutableListOf<String>()
        if (rawText.isNotBlank()) textParts += rawText
        if (importedFileNames.isNotEmpty()) {
            textParts += "（已导入 ${importedFileNames.size} 个文件到沙箱：" +
                importedFileNames.joinToString("、") + "，可用 list_files / read_file 查看）"
        }
        mPendingImages = attachments
            .filter { it.isImage }
            .mapNotNull { attachment ->
                runCatching {
                    mFileRepository.saveFile(Uri.parse(attachment.uri))
                }.getOrNull()?.let { uuid -> buildImageReference(uuid) }
            }
        return PreparedPayload(textParts.joinToString(nlLiteral().repeat(2)))
    }

    /** 查询文件显示名，缺失时退回末段。 */
    /** 查询文件字节数；查询失败时返回 0，界面不展示大小。 */
    private fun querySize(uri: Uri): Long {
        val projection = arrayOf(android.provider.OpenableColumns.SIZE)
        runCatching {
            mContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (index >= 0) return cursor.getLong(index)
                }
            }
        }
        return 0L
    }

    private fun queryDisplayName(uri: Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        runCatching {
            mContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index).orEmpty()
                }
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    /** 构造图片引用，供多模态请求使用。 */
    private suspend fun buildImageReference(uuid: String): LLMImageReference? {
        val entity = mFileRepository.getFileEntity(uuid) ?: return null
        val file = mFileRepository.getFile(uuid)
        val bitmap = mFileRepository.loadSampledBitmap(
            uuid = uuid,
            requestedWidthPx = IMAGE_MAX_EDGE,
            requestedHeightPx = IMAGE_MAX_EDGE
        )
        val reference = LLMImageReference(
            uuid = uuid,
            mimeType = entity.mimeType ?: "image/jpeg",
            width = bitmap?.width ?: 0,
            height = bitmap?.height ?: 0,
            byteCount = file?.length() ?: 0L
        )
        bitmap?.recycle()
        return reference
    }

    /** 换行字面量，避免在多行字符串里写死转义。 */
    private fun nlLiteral(): String = System.lineSeparator()

    /** 确保有可用会话：取最近一个活跃会话，没有就新建。 */
    private suspend fun ensureSession() {
        val active = mSessionStore.listSessions(includeArchived = false).firstOrNull()
        mSessionId = active?.id ?: when (val created = mSessionStore.createSession()) {
            is AssistantSessionCreateResult.Created -> created.id
            is AssistantSessionCreateResult.LimitReached -> ""
        }
    }

    /** 打开会话列表。 */
    @UiIntentObserver(AssistantUiIntent.OpenSessionList::class)
    private suspend fun onOpenSessionList() {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(
            sessionListVisible = true,
            sessions = mSessionStore.listSessions()
        ).setup()
    }

    /** 关闭会话列表。 */
    @UiIntentObserver(AssistantUiIntent.CloseSessionList::class)
    private fun onCloseSessionList() {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(sessionListVisible = false).setup()
    }

    /** 新建会话；满额时存储层会自动归档最旧的。 */
    @UiIntentObserver(AssistantUiIntent.NewSession::class)
    private suspend fun onNewSession() {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        when (val created = mSessionStore.createSession()) {
            is AssistantSessionCreateResult.Created -> {
                mSessionId = created.id
                mMessages.clear()
                state.copy(
                    items = emptyList(),
                    currentSessionId = created.id,
                    sessions = mSessionStore.listSessions(),
                    sessionListVisible = false
                ).setup()
            }

            is AssistantSessionCreateResult.LimitReached ->
                AssistantViewEvent.Toast("会话已达上限").tryEmit()
        }
    }

    /** 切换到指定会话。 */
    @UiIntentObserver(AssistantUiIntent.SwitchSession::class)
    private suspend fun onSwitchSession(intent: AssistantUiIntent.SwitchSession) {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        mSessionId = intent.sessionId
        mMessages.clear()
        mMessages.addAll(mSessionStore.loadSession(intent.sessionId))
        state.copy(
            items = mMessages.toItems(),
            currentSessionId = intent.sessionId,
            sessionListVisible = false
        ).setup()
    }

    /** 归档会话；归档当前会话时自动切到另一个。 */
    @UiIntentObserver(AssistantUiIntent.ArchiveSession::class)
    private suspend fun onArchiveSession(intent: AssistantUiIntent.ArchiveSession) {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        mSessionStore.archiveSession(intent.sessionId)
        if (intent.sessionId == mSessionId) {
            ensureSession()
            mMessages.clear()
            mMessages.addAll(mSessionStore.loadSession(mSessionId))
        }
        state.copy(
            sessions = mSessionStore.listSessions(),
            currentSessionId = mSessionId,
            items = mMessages.toItems()
        ).setup()
    }

    /** 删除会话；删除当前会话时自动切到另一个。 */
    @UiIntentObserver(AssistantUiIntent.DeleteSession::class)
    private suspend fun onDeleteSession(intent: AssistantUiIntent.DeleteSession) {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        mSessionStore.deleteSession(intent.sessionId)
        if (intent.sessionId == mSessionId) {
            mSessionId = ""
            ensureSession()
            mMessages.clear()
            mMessages.addAll(mSessionStore.loadSession(mSessionId))
        }
        state.copy(
            sessions = mSessionStore.listSessions(),
            currentSessionId = mSessionId,
            items = mMessages.toItems()
        ).setup()
    }

    /** 清空独立提示词并回到继承全局。 */
    @UiIntentObserver(AssistantUiIntent.ResetPrompt::class)
    private fun onResetPrompt() {
        AppModel.assistantSystemPrompt = ""
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(customPrompt = "", inheritGlobalPrompt = true).setup()
    }

    /** 清空沙箱并刷新占用提示。 */
    @UiIntentObserver(AssistantUiIntent.ClearSandbox::class)
    private suspend fun onClearSandbox() {
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        val removed = mStore.clearSandbox()
        state.copy(sandboxHint = mStore.sandboxSummary()).setup()
        AssistantViewEvent.Toast("已清空沙箱，移除 $removed 项").tryEmit()
    }

    /** 打开文件选择器。 */
    @UiIntentObserver(AssistantUiIntent.ImportFilesClick::class)
    private fun onImportFilesClick() {
        AssistantViewEvent.OpenFilePicker.tryEmit()
    }

    /** 把选中的文件复制进沙箱，并刷新沙箱占用提示。 */
    @UiIntentObserver(AssistantUiIntent.FilesPicked::class)
    private suspend fun onFilesPicked(intent: AssistantUiIntent.FilesPicked) {
        if (intent.uris.isEmpty()) return
        val result = runCatching { mStore.importFiles(intent.uris) }
            .getOrElse { AssistantImportResult(0, intent.uris.size) }
        val state = getOrNull<AssistantUiState.Ready>() ?: return
        state.copy(sandboxHint = mStore.sandboxSummary()).setup()
        AssistantViewEvent.ShowImportResult(
            importedCount = result.importedCount,
            failedCount = result.failedCount
        ).tryEmit()
    }

    /**
     * 执行一轮会话：生成 → 解析工具 → 回填 → 继续，直到不再有工具调用。
     *
     * 轮数上限由 [AssistantToolCatalog.MAX_TOOL_ROUNDS] 控制，避免模型反复调用同一工具。
     */
    private suspend fun runGeneration() {
        mPartialReply.clear()
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
            // 一个字都没收到就断了，多半是网络抖动，自动重试一次
            // 这样玩家不必自己再发一句来「重新启动」对话
            if (mPartialReply.isEmpty() && mRetryCount < MAX_STREAM_RETRY) {
                mRetryCount++
                mPartialReply.clear()
                runGeneration()
                return
            }
            val message = error.message ?: error::class.simpleName ?: "未知错误"
            // 已经生成的部分先落库，避免断流把整段回复一起吞掉
            val partial = mPartialReply.toString().trim()
            if (partial.isNotEmpty()) {
                mMessages += AssistantMessage(role = ROLE_ASSISTANT, content = partial)
                mPartialReply.clear()
            }
            // 失败也写进对话，避免只闪一下 Toast 就查不到原因
            mMessages += AssistantMessage(
                role = ROLE_ASSISTANT,
                content = "生成中断：$message（可直接再发一条继续，或点右上角重新生成）"
            )
            persistAndPublish()
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
        // 断流兜底：流不会自己报告「已经断了」，只能靠超时判定。
        // 超时后把已收到的内容原样返回，宁可少一段也不要整轮丢失。
        withTimeoutOrNull(STREAM_TIMEOUT_MS) {
            client.streamGenerate(request).collect { event ->
                when (event) {
                    is LLMStreamEvent.Delta -> {
                        mPartialReply.append(event.content)
                        publishStreaming(mPartialReply.toString())
                    }

                    is LLMStreamEvent.ReasoningDelta -> Unit
                    is LLMStreamEvent.Finished -> Unit
                    else -> Unit
                }
            }
        }
        return mPartialReply.toString()
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
        // 图片只随第一次请求发送，工具循环里的后续请求不再重复携带
        if (mPendingImages.isNotEmpty() && messages.isNotEmpty()) {
            val last = messages.last()
            if (last.role == LLMMessageRole.User) {
                messages[messages.lastIndex] = last.copy(
                    blocks = listOf(LLMContentBlock.Text(last.content)) +
                        mPendingImages.map { LLMContentBlock.Image(it) }
                )
            }
            mPendingImages = emptyList()
        }
        return messages
    }

    /** 落盘并刷新页面消息列表。 */
    private suspend fun persistAndPublish() {
        mSessionStore.saveSession(mSessionId, mMessages)
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
        /** 断流后最多自动重试几次，避免网络一直不通时反复消耗额度。 */
        const val MAX_STREAM_RETRY = 1

        /** 流式空闲超时：超过这个时间没有新内容就当作断流。 */
        const val STREAM_TIMEOUT_MS = 90_000L

        /** 单个文本附件读入正文的上限字符数。 */
        const val MAX_ATTACHMENT_CHARS = 20000

        /** 图片送入模型前的最长边，控制请求体积。 */
        const val IMAGE_MAX_EDGE = 1024
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"

        /** 请求携带的最近消息条数；助手场景不需要整段历史。 */
        const val MAX_CONTEXT_MESSAGES = 40
    }

    /** 携带附件的待发送内容。 */
    private data class PreparedPayload(val text: String)

}
