package me.kafuuneko.rpclient.feature.assistant.presentation

import android.net.Uri

/** 助手页面的用户意图。 */
sealed class AssistantUiIntent {
    /** 首次进入页面，读取历史会话与设置。 */
    data object Init : AssistantUiIntent()

    /** 输入框内容变化。 */
    data class InputChanged(val text: String) : AssistantUiIntent()

    /** 提交当前输入并开始生成。 */
    data object Send : AssistantUiIntent()

    /** 中止当前生成。 */
    data object Stop : AssistantUiIntent()

    /** 清空会话历史。 */
    data object ClearSession : AssistantUiIntent()

    /** 打开提示词编辑弹窗。 */
    data object OpenPromptEditor : AssistantUiIntent()

    /** 关闭提示词编辑弹窗。 */
    data object ClosePromptEditor : AssistantUiIntent()

    /** 切换提示词来源并立即持久化。 */
    data class PromptModeChanged(val inheritGlobal: Boolean) : AssistantUiIntent()

    /** 修改独立提示词并立即持久化。 */
    data class CustomPromptChanged(val text: String) : AssistantUiIntent()

    /** 打开会话列表。 */
    data object OpenSessionList : AssistantUiIntent()

    /** 关闭会话列表。 */
    data object CloseSessionList : AssistantUiIntent()

    /** 新建会话；满额时自动归档最旧的。 */
    data object NewSession : AssistantUiIntent()

    /** 切换到指定会话。 */
    data class SwitchSession(val sessionId: String) : AssistantUiIntent()

    /** 归档指定会话。 */
    data class ArchiveSession(val sessionId: String) : AssistantUiIntent()

    /** 删除指定会话。 */
    data class DeleteSession(val sessionId: String) : AssistantUiIntent()

    /** 清空独立提示词，回到继承全局。 */
    data object ResetPrompt : AssistantUiIntent()

    /** 清空沙箱内的全部文件。 */
    data object ClearSandbox : AssistantUiIntent()

    /** 选择附件，随下一条消息发送。 */
    data class AttachFiles(val uris: List<Uri>) : AssistantUiIntent()

    /** 移除一条待发送附件。 */
    data class RemoveAttachment(val uri: String) : AssistantUiIntent()

    /** 打开文件选择器，把手机里的文件复制进沙箱。 */
    data object ImportFilesClick : AssistantUiIntent()

    /** 用户选中了要导入沙箱的文件。 */
    data class FilesPicked(val uris: List<Uri>) : AssistantUiIntent()
}
