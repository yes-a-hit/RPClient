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

    /** 选择附件，随下一条消息发送。 */
    data class AttachFiles(val uris: List<Uri>) : AssistantUiIntent()

    /** 移除一条待发送附件。 */
    data class RemoveAttachment(val uri: String) : AssistantUiIntent()

    /** 打开文件选择器，把手机里的文件复制进沙箱。 */
    data object ImportFilesClick : AssistantUiIntent()

    /** 用户选中了要导入沙箱的文件。 */
    data class FilesPicked(val uris: List<Uri>) : AssistantUiIntent()
}
