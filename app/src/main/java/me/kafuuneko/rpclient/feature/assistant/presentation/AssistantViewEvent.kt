package me.kafuuneko.rpclient.feature.assistant.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 助手页面的一次性事件。 */
sealed class AssistantViewEvent : IViewEvent {
    /** 轻提示。 */
    data class Toast(val message: String) : AssistantViewEvent()

    /** 打开系统文件选择器，把文件复制进沙箱供助手读取。 */
    data object OpenFilePicker : AssistantViewEvent()

    /** 展示沙箱导入结果。 */
    data class ShowImportResult(
        val importedCount: Int,
        val failedCount: Int
    ) : AssistantViewEvent()
}
