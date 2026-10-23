package me.kafuuneko.rpclient.feature.chat.presentation

import android.net.Uri
import me.kafuuneko.rpclient.libs.imagegen.ImageGenAspect
import me.kafuuneko.rpclient.libs.media.MessageImageAction

/** 单聊页面可接收的全部用户意图和生命周期事件。 */
sealed class ChatUiIntent {
    /** 从摘要额度提示前往全局设置。 */
    data object OpenSummarySettings : ChatUiIntent()

    data object RetryImageReply : ChatUiIntent()

    data class ImageAction(val action: MessageImageAction) : ChatUiIntent()

    data class Init(val sessionId: String?) : ChatUiIntent()

    data object Resume : ChatUiIntent()

    data object LoadOlderMessages : ChatUiIntent()

    data object Back : ChatUiIntent()

    data object SendMessage : ChatUiIntent()

    data object StopGeneration : ChatUiIntent()

    data object RegenerateLast : ChatUiIntent()

    data object ContinueLast : ChatUiIntent()

    data object ImpersonateUser : ChatUiIntent()

    data class RegenerateFromMessage(val messageId: String) : ChatUiIntent()

    data class BranchFromMessage(val messageId: String) : ChatUiIntent()

    data object ShowSessionLoreDialog : ChatUiIntent()

    data object OpenWorldBookManager : ChatUiIntent()

    data class ChangeSessionLorebookDialogQuery(val value: String) : ChatUiIntent()

    data class ToggleSessionLorebookDialogEntry(val entryId: Long) : ChatUiIntent()

    data class ToggleSessionLorebookDialogGroup(val lorebookId: Long) : ChatUiIntent()

    data object ConfirmSessionLorebookSelection : ChatUiIntent()

    data object OpenCharacterEditor : ChatUiIntent()

    data class ChangeInputDraft(val value: String) : ChatUiIntent()

    data class ChangeLorebookQuery(val value: String) : ChatUiIntent()

    data class ToggleSessionLoreEntry(val entryId: Long) : ChatUiIntent()

    data class ToggleSessionLorebook(val lorebookId: Long) : ChatUiIntent()

    data object OpenChatSettings : ChatUiIntent()

    data object OpenPromptInspector : ChatUiIntent()

    /** 打开生图对话框；提示词以最近一条角色消息预填。 */
    data object OpenImageGen : ChatUiIntent()

    data class ChangeImageGenPrompt(val value: String) : ChatUiIntent()

    data class ChangeImageGenAspect(val aspect: ImageGenAspect) : ChatUiIntent()

    /** 按当前提示词请求外接生图服务，并把结果作为角色图片消息插入会话。 */
    data object GenerateImage : ChatUiIntent()

    data class CopyPromptItem(val text: String) : ChatUiIntent()

    data object CloseChatSettings : ChatUiIntent()


    data object ExportChatClick : ChatUiIntent()

    data class ExportChatResult(val uri: Uri) : ChatUiIntent()

    data object SummarizeNow : ChatUiIntent()

    data object RestorePreviousSummary : ChatUiIntent()

    data class ToggleAutoSummaryPaused(val paused: Boolean) : ChatUiIntent()

    data object CancelSummary : ChatUiIntent()


    /** 清空上下文重来：消息全清，会话与设置保留。 */
    data object ClearConversationContextClick : ChatUiIntent()

    /** 确认清空上下文。 */
    data object ConfirmClearConversationContext : ChatUiIntent()

    data object DeleteSessionClick : ChatUiIntent()

    data object ConfirmDeleteSession : ChatUiIntent()

    data object DismissDialog : ChatUiIntent()

    data class SaveTitle(val value: String) : ChatUiIntent()

    data class SaveSummary(val value: String) : ChatUiIntent()

    data class SaveUserNote(val value: String) : ChatUiIntent()

    data class SaveUserName(val value: String) : ChatUiIntent()

    data class SaveUserDescription(val value: String) : ChatUiIntent()

    data class SaveCreatorNotes(val value: String) : ChatUiIntent()

    data class CopyMessage(val messageId: String) : ChatUiIntent()

    data class StartEditMessage(val messageId: String) : ChatUiIntent()

    data class ChangeEditingMessageDraft(val value: String) : ChatUiIntent()

    /** 编辑思维链草稿。 */
    data class ChangeEditingThinkDraft(val value: String) : ChatUiIntent()

    /** 展开或收起编辑框里的思维链。 */
    data object ToggleEditingThink : ChatUiIntent()

    data object SaveEditingMessage : ChatUiIntent()

    /**
     * 保存编辑并重新回答。
     *
     * 与 [SaveEditingMessage] 的区别只在于要不要作废这条之后的回复——
     * 改错字和改内容意图不同，删不删得有用户自己决定。
     */
    data object SaveEditingMessageAndRegenerate : ChatUiIntent()

    data object CancelEditingMessage : ChatUiIntent()

    data class ToggleThinkBlock(val blockId: String) : ChatUiIntent()

    data class DeleteMessageClick(val messageId: String) : ChatUiIntent()

    data class ConfirmDeleteMessage(val messageId: String) : ChatUiIntent()

    data object OpenProviderSettings : ChatUiIntent()
}
