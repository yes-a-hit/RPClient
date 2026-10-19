package me.kafuuneko.rpclient.feature.main.presentation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.feature.main.model.MainProviderItem
import me.kafuuneko.rpclient.libs.imagegen.ImageGenAspect
import me.kafuuneko.rpclient.libs.imagegen.ImageGenSettings
import me.kafuuneko.rpclient.libs.media.ImageSendMode
import me.kafuuneko.rpclient.libs.media.ImageSendSettings
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole
import me.kafuuneko.rpclient.libs.theme.AppThemeMode

/** 全局设置页状态树，各子状态与设置页的可渲染面板一一对应。 */
data class MainSettingsState(
    /** 设置页中应用配色主题的状态。 */
    val appearanceState: MainAppearanceSettingsState,
    /** 设置页中全局用户身份区域的状态。 */
    val identityState: MainUserIdentityState,
    /** 设置页中模型配置选择和流式开关状态。 */
    val providerState: MainProviderSettingsState,
    /** 设置页中 Prompt 行为选项的状态。 */
    val promptBehaviorState: MainPromptBehaviorState,
    /** 设置页中世界书 Prompt 预算选项的状态。 */
    val worldInfoBudgetState: MainWorldInfoBudgetState,
    /** 设置页中自动摘要选项的状态。 */
    val summaryState: MainSummarySettingsState,
    /** 设置页中会话数据管理区域的状态。 */
    val chatDataManagementState: MainChatDataManagementState = MainChatDataManagementState.Idle,
    /** 设置页中调试功能区域的状态。 */
    val debugState: MainDebugSettingsState,
    /** 设置页中图片发送方式和自定义参数草稿。 */
    val imageSendState: MainImageSendState = MainImageSendState(),
    /** 设置页中外接生图服务的连接与默认参数。 */
    val imageGenState: MainImageGenSettingsState = MainImageGenSettingsState(),
    /** 设置页中已安装的助手 Skill 列表。 */
    val skillState: MainSkillSettingsState = MainSkillSettingsState()
)

/**
 * 助手 Skill 管理面板状态。
 *
 * [skills] 为空既可能表示尚未安装，也可能表示全部包文件损坏，
 * 因此界面统一展示为空态并引导用户导入。
 */
data class MainSkillSettingsState(
    /** 已安装的 Skill 列表，按名称排序。 */
    val skills: List<MainSkillItem> = emptyList()
) {
    /** 已启用的 Skill 数量，用于面板副标题。 */
    val enabledCount: Int
        get() = skills.count { it.enabled }
}

/** 单个 Skill 在管理面板中的展示项。 */
data class MainSkillItem(
    /** Skill 稳定标识。 */
    val id: String,
    /** 展示名称。 */
    val name: String,
    /** 用途说明。 */
    val description: String,
    /** 触发词摘要；常驻 Skill 使用占位标记。 */
    val triggerSummary: String,
    /** 是否参与激活判定。 */
    val enabled: Boolean,
    /** 是否常驻激活。 */
    val alwaysOn: Boolean
)

/**
 * 外接生图服务配置面板状态。
 *
 * 字段修改即时写入偏好，因此不再保留独立的保存按钮与脏标记；
 * [isConfigured] 用于提示用户当前是否具备发起请求的最低配置。
 */
data class MainImageGenSettingsState(
    /** 服务基础地址。 */
    val baseUrl: String = ImageGenSettings.DEFAULT_BASE_URL,
    /** 接口鉴权 API Key。 */
    val apiKey: String = "",
    /** 服务端模型名。 */
    val model: String = ImageGenSettings.DEFAULT_MODEL,
    /** 默认画幅。 */
    val aspect: ImageGenAspect = ImageGenAspect.Square
) {
    /** 是否已具备发起请求的最低配置。 */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        /**
         * 从已持久化配置创建面板状态。
         *
         * @param settings 解析后的生图配置
         * @return 可直接渲染的面板状态
         */
        fun from(settings: ImageGenSettings): MainImageGenSettingsState =
            MainImageGenSettingsState(
                baseUrl = settings.baseUrl,
                apiKey = settings.apiKey,
                model = settings.model,
                aspect = settings.aspect
            )
    }
}

/** 应用外观设置面板状态。 */
data class MainAppearanceSettingsState(
    /** 用户当前选择的配色主题模式。 */
    val themeMode: AppThemeMode,
    /** 聊天背景图片与遮罩强度。 */
    val backgroundState: MainChatBackgroundState = MainChatBackgroundState()
)

/**
 * 聊天背景设置状态。
 *
 * [preview] 允许为空，用于区分「未配置背景」与「已配置但文件暂时无法解码」两种情况：
 * 前者隐藏清除入口，后者仍允许用户清除失效引用。
 */
data class MainChatBackgroundState(
    /** 已解码的背景预览图；为空表示未配置背景或图片无法解码。 */
    val preview: ImageBitmap? = null,
    /** 背景遮罩不透明度（0-100），数值越高背景越淡。 */
    val dimPercent: Int = 35,
    /** 是否已保存背景图 UUID；用于在解码失败时仍提供清除入口。 */
    val hasImage: Boolean = false
)

/** 用户名称、描述和头像面板状态。 */
data class MainUserIdentityState(
    /** 当前会话或 Prompt 使用的用户名称。 */
    val userName: String,
    /** 当前会话或 Prompt 使用的用户设定。 */
    val userDescription: String,
    /** 设置页展示的用户设定摘要。 */
    val userDescriptionPreview: String,
    /** 全局用户头像的加载与编辑状态。 */
    val avatarState: MainUserAvatarState
)

/**
 * 用户头像的配置状态。
 *
 * [Configured.image] 允许为空，以保留“头像已配置但文件暂时无法解码”时的清除入口。
 */
sealed class MainUserAvatarState {
    data object None : MainUserAvatarState()
    data class Configured(val image: ImageBitmap?) : MainUserAvatarState()
}

/** 模型配置面板状态；生成参数只在至少存在一个可用模型配置时进入状态树。 */
sealed class MainProviderSettingsState {
    data object Empty : MainProviderSettingsState()

    data class Available(
        /** 当前选中的模型配置 ID。 */
        val selectedProviderId: Long,
        /** 当前页面或设置允许选择的模型配置列表。 */
        val providers: List<MainProviderItem>,
        /** 模型生成参数编辑区域的状态。 */
        val generationParametersState: MainGenerationParametersState
    ) : MainProviderSettingsState()
}

/** 当前模型配置的生成参数快照。 */
data class MainGenerationParametersState(
    /** 控制模型输出随机性的温度参数。 */
    val temperature: Float,
    /** 限制模型候选词累计概率的 Top P 参数。 */
    val topP: Float,
    /** 单次生成允许返回的最大 Token 数。 */
    val maxTokens: Int,
    /** 模型输入与输出共享的上下文 Token 上限。 */
    val contextTokens: Int
)

/** Prompt 行为面板状态。 */
data class MainPromptBehaviorState(
    /** 当前模型配置对应的 Prompt 后处理设置。 */
    val providerPostProcessingState: MainProviderPostProcessingState,
    /** 示例对话进入 Prompt 时采用的全局处理方式。 */
    val exampleDialogueBehavior: ExampleDialogueBehavior,
    /** 后续请求上下文是否包含历史消息中的推理块。 */
    val includeThinkInContext: Boolean,
    /** 普通生成最多读取的最近历史消息数，0 表示不限制。 */
    val maxHistoryMessages: Int,
    /** 上下文裁剪风险提示是否启用。 */
    val contextTrimmingAlert: Boolean,
    /** 当前会话是否启用流式生成。 */
    val streamEnabled: Boolean
)

/** 仅在存在当前模型配置时允许修改其 Prompt 后处理模式。 */
sealed class MainProviderPostProcessingState {
    data object Unavailable : MainProviderPostProcessingState()
    data class Available(
        /** 当前流程采用的处理模式。 */
        val mode: PromptPostProcessingMode
    ) : MainProviderPostProcessingState()
}

/** 世界书 Prompt 预算面板状态。 */
data class MainWorldInfoBudgetState(
    /** 世界书预算占可用上下文的百分比。 */
    val budgetPercent: Int,
    /** 世界书预算允许使用的绝对 Token 上限。 */
    val budgetCap: Int,
    /** 世界书固定预算超过可用上下文时是否提示。 */
    val overflowAlert: Boolean
)

enum class MainSummarySettingsTab {
    General,
    Conversation
}

/** 通用摘要参数与对话摘要行为面板状态。 */
data class MainSummarySettingsState(
    /** 当前选中的模型配置 ID。 */
    val selectedProviderId: Long = 0L,
    /** 当前页面或设置允许选择的模型配置列表。 */
    val providers: List<MainProviderItem> = emptyList(),
    /** 是否启用达到阈值后自动生成摘要。 */
    val autoSummaryEnabled: Boolean,
    /** 触发自动摘要所需的新增消息数量。 */
    val triggerMessageCount: Int,
    /** 自动摘要期望控制的最大字数。 */
    val wordsLimit: Int,
    /** 单次摘要请求允许处理的最大消息数。 */
    val maxMessagesPerRequest: Int,
    /** 当前设置为模型回复预留的 Token 数。 */
    val responseTokens: Int,
    /** 摘要注入 Prompt 的角色与深度设置。 */
    val injectionState: MainSummaryInjectionState,
    /** 当前界面选中的标签页。 */
    val selectedTab: MainSummarySettingsTab = MainSummarySettingsTab.General
)

/** 摘要注入位置；只有聊天历史内注入需要额外的深度和角色。 */
sealed class MainSummaryInjectionState(
    val position: SummaryInjectionPosition
) {
    data object None : MainSummaryInjectionState(SummaryInjectionPosition.None)
    data object BeforeMain : MainSummaryInjectionState(SummaryInjectionPosition.BeforeMain)
    data object AfterMain : MainSummaryInjectionState(SummaryInjectionPosition.AfterMain)

    data class InChat(
        /** 当前内容相对聊天末尾的插入或扫描深度。 */
        val depth: Int,
        /** 摘要注入聊天历史内部时采用的消息角色。 */
        val role: SummaryInjectionRole
    ) : MainSummaryInjectionState(SummaryInjectionPosition.InChat)
}

/** 图片发送面板；输入草稿与已保存配置分离，非法输入不会影响正在准备的请求。 */
data class MainImageSendState(
    val mode: ImageSendMode = ImageSendMode.Auto,
    val maxFileKiB: String = "2048",
    val maxWidth: String = "1536",
    val maxHeight: String = "1536",
    val hasChanges: Boolean = false,
    @param:StringRes val errorResId: Int? = null
) {
    companion object {
        /**
         * 从已校验偏好创建面板草稿。
         * @param settings 当前已保存参数。
         * @return 未修改的可编辑面板状态。
         */
        fun from(settings: ImageSendSettings): MainImageSendState = MainImageSendState(
            settings.mode, settings.maxFileKiB.toString(), settings.maxWidth.toString(), settings.maxHeight.toString()
        )
    }
}

internal fun SummaryInjectionPosition.toMainSummaryInjectionState(
    depth: Int,
    role: SummaryInjectionRole
): MainSummaryInjectionState {
    return when (this) {
        SummaryInjectionPosition.None -> MainSummaryInjectionState.None
        SummaryInjectionPosition.BeforeMain -> MainSummaryInjectionState.BeforeMain
        SummaryInjectionPosition.AfterMain -> MainSummaryInjectionState.AfterMain
        SummaryInjectionPosition.InChat -> MainSummaryInjectionState.InChat(
            depth = depth,
            role = role
        )
    }
}

/** 对话数据管理面板的文件读取状态。 */
sealed class MainChatDataManagementState {
    data object Idle : MainChatDataManagementState()
    data object Reading : MainChatDataManagementState()
}

/** Debug 设置面板状态。 */
data class MainDebugSettingsState(
    /** 当前对象或功能是否启用。 */
    val enabled: Boolean
)
