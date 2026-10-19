package me.kafuuneko.rpclient.feature.assistant.presentation

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
}
