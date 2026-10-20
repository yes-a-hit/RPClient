package me.kafuuneko.rpclient.feature.assistant.presentation

/**
 * 助手页面的可持久化 UI 状态。
 *
 * 页面只有一条主线会话，因此状态里同时保存消息列表、输入框与生成态，
 * 避免把「正在流式输出的文本」塞进消息列表造成重复渲染。
 */
sealed class AssistantUiState {
    /** 首帧尚未读取会话。 */
    data object Loading : AssistantUiState()

    /** 会话可用。 */
    data class Ready(
        // 已提交的消息条目，工具结果也在其中
        val items: List<AssistantMessageItem> = emptyList(),
        // 输入框内容
        val input: String = "",
        // 是否正在生成
        val isGenerating: Boolean = false,
        // 当前轮流式输出的已生成文本
        val streamingReply: String = "",
        // 提示词来源说明
        val promptHint: String = "",
        // 沙箱占用说明
        val sandboxHint: String = "",
        // 是否继承全局主提示词
        val inheritGlobalPrompt: Boolean = true,
        // 独立提示词正文
        val customPrompt: String = "",
        // 是否展开提示词编辑弹窗
        val promptEditorVisible: Boolean = false,
        // 一次性状态提示，例如工具执行进度
        val statusText: String = "",
        /** 会话列表是否展开。 */
        val sessionListVisible: Boolean = false,
        /** 全部会话摘要。 */
        val sessions: List<me.kafuuneko.rpclient.libs.assistant.AssistantSessionSummary> = emptyList(),
        /** 当前会话 ID。 */
        val currentSessionId: String = "",
        /** 待发送附件：选中后随下一条消息一起发出。 */
    val attachments: List<AssistantAttachment> = emptyList()
    ) : AssistantUiState()

    /** 页面结束。 */
    data object Finished : AssistantUiState()
}

/** 消息条目在页面上的角色分类。 */
enum class AssistantRole {
    User,
    Assistant,
    Tool
}

/**
 * 一条展示用消息。
 *
 * [toolName] 仅对工具结果有意义，用于在界面上标出是哪个工具的输出。
 */
data class AssistantMessageItem(
    val role: AssistantRole,
    val content: String,
    val toolName: String? = null,
    /** 这条消息附带的文件，界面在气泡下方展示。 */
    val attachments: List<AssistantAttachment>? = null
)

/**
 * 一条待发送附件。
 *
 * 文本附件在发送时读入正文，图片附件转成多模态内容块，
 * 两者都不落进沙箱，避免和助手自己的文件操作混淆。
 */
data class AssistantAttachment(
    /** 原始 URI 字符串。 */
    val uri: String,
    /** 显示名。 */
    val name: String,
    /** 是否为图片。 */
    val isImage: Boolean,
    /** MIME 类型，用于选择预览方式。 */
    val mimeType: String = "",
    /** 字节数，用于展示大小。 */
    val byteCount: Long = 0
)
