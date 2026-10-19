package me.kafuuneko.rpclient.feature.assistant.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 助手页面的一次性事件。 */
sealed class AssistantViewEvent : IViewEvent {
    /** 轻提示。 */
    data class Toast(val message: String) : AssistantViewEvent()
}
