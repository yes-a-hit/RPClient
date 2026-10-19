package me.kafuuneko.rpclient.feature.scriptplay.presentation

import me.kafuuneko.rpclient.libs.script.ScriptPlayMessage
import me.kafuuneko.rpclient.libs.script.ScriptPlaySceneState

/** 剧本模式的页面状态。 */
sealed class ScriptPlayUiState {
    data object Loading : ScriptPlayUiState()

    data class Ready(
        val packName: String,
        /** 当前场景状态。 */
        val scene: ScriptPlaySceneState,
        /** 对话历史。 */
        val messages: List<ScriptPlayMessage> = emptyList(),
        /** 输入框内容。 */
        val input: String = "",
        /** 是否正在生成。 */
        val generating: Boolean = false,
        /** 本局已结束时的提示。 */
        val finishedReason: String = "",
        /** 一次性状态提示。 */
        val statusText: String = ""
    ) : ScriptPlayUiState() {
        /** 剩余轮次，0 表示不限。 */
        val remainingRounds: Int
            get() = if (scene.maxRounds <= 0) 0 else (scene.maxRounds - scene.roundCount).coerceAtLeast(0)
    }

    data object Finished : ScriptPlayUiState()
}

/** 剧本模式的用户意图。 */
sealed class ScriptPlayUiIntent {
    /** 首次进入，加载或新建这一局。 */
    data object Init : ScriptPlayUiIntent()

    /** 输入框变化。 */
    data class InputChanged(val value: String) : ScriptPlayUiIntent()

    /** 发送一条玩家发言。 */
    data object Send : ScriptPlayUiIntent()

    /** 切换到指定场景。 */
    data class SwitchScene(val sceneId: String) : ScriptPlayUiIntent()

    /** 重开当前场景，轮次归零。 */
    data object RestartScene : ScriptPlayUiIntent()

    /** 返回。 */
    data object Back : ScriptPlayUiIntent()
}

/** 剧本模式事件。 */
sealed class ScriptPlayViewEvent : me.kafuuneko.rpclient.libs.core.IViewEvent {
    /** 轻提示。 */
    data class Toast(val message: String) : ScriptPlayViewEvent()
}
