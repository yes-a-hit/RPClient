package me.kafuuneko.rpclient.feature.scriptplay.presentation

import me.kafuuneko.rpclient.libs.script.ScriptVariableKind
import me.kafuuneko.rpclient.libs.script.ScriptPlayMessage
import me.kafuuneko.rpclient.libs.script.ScriptPlaySceneState

/** 状态面板的一行。 */
data class ScriptStatusItem(
    /** 展示名，作者没填就用变量名。 */
    val label: String,
    val value: Int,
    /** 数值上限，用于画进度条；0 表示不画。 */
    val max: Int = 0,
    /** 按种类渲染：数字画条、开关显示两态文案、文本直接显示内容。 */
    val kind: ScriptVariableKind = ScriptVariableKind.Number,
    /** 文本变量的当前内容。 */
    val text: String = "",
    /** 开关两态文案。 */
    val onLabel: String = "",
    val offLabel: String = ""
)

/** 剧本模式的页面状态。 */
sealed class ScriptPlayUiState {
    data object Loading : ScriptPlayUiState()

    data class Ready(
        val packName: String,
        /** 当前场景状态。 */
        val scene: ScriptPlaySceneState,
        /** 本局是否留有检查点快照。 */
        val hasCheckpoint: Boolean = false,
        /**
         * 状态面板条目：展示名与当前值。
         *
         * 只放作者标了可见的变量——好感这类藏起来的量
         * 摊在界面上就没意思了。
         */
        val statusItems: List<ScriptStatusItem> = emptyList(),
        /** 剧本是否开启状态面板；关掉时整块不显示。 */
        val showDashboard: Boolean = true,
        /** 对话历史。 */
        val messages: List<ScriptPlayMessage> = emptyList(),
        /** 输入框内容。 */
        val input: String = "",
        /** 是否正在生成。 */
        val generating: Boolean = false,
        /** 本局已结束时的提示。 */
        val finishedReason: String = "",
        /** 正在生成玩家代写内容。 */
        val impersonating: Boolean = false,

        /** 当前场景可出场的角色名，供 GM 面板选择。 */
        val castNames: List<String> = emptyList(),

        /** GM 面板是否展开。 */
        val gmPanelVisible: Boolean = false,

        /** 本轮禁言的角色。 */
        val mutedCharacters: Set<String> = emptySet(),

        /** 本轮指定发言的角色。 */
        val forcedSpeaker: String? = null,

        /** 当前场景的背景图 UUID；空表示用默认底色。 */
        val backgroundUuid: String = "",

        /** 本轮给出的推荐行动。 */
        val options: List<String> = emptyList(),

        /** 自动推进：回复后自动选第一个推荐行动继续。 */
        val autoPlay: Boolean = false,

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

    /** 回到最近一次检查点。 */
    data object RestoreCheckpoint : ScriptPlayUiIntent()

    /** 返回。 */
    /** 选择一个推荐行动直接发送。 */
    data class SelectOption(val text: String) : ScriptPlayUiIntent()

    /** 切换自动推进。 */
    data object ToggleAutoPlay : ScriptPlayUiIntent()

    /** 让模型替玩家写一句行动，填入输入框由玩家确认。 */
    data object Impersonate : ScriptPlayUiIntent()

    /** 玩家轮空：不发话，让角色与环境自行推进一轮。 */
    data object ContinueWithoutInput : ScriptPlayUiIntent()

    /** GM 面板：打开与关闭。 */
    data object OpenGmPanel : ScriptPlayUiIntent()

    data object CloseGmPanel : ScriptPlayUiIntent()

    /** 切换某个角色的禁言状态。 */
    data class ToggleMute(val name: String) : ScriptPlayUiIntent()

    /** 指定下一轮必须发言的角色；再点一次取消。 */
    data class ForceSpeaker(val name: String) : ScriptPlayUiIntent()

    data object Back : ScriptPlayUiIntent()
}

/** 剧本模式事件。 */
sealed class ScriptPlayViewEvent : me.kafuuneko.rpclient.libs.core.IViewEvent {
    /** 轻提示。 */
    data class Toast(val message: String) : ScriptPlayViewEvent()
}
