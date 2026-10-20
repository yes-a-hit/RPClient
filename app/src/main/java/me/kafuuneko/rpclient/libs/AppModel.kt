package me.kafuuneko.rpclient.libs

import com.chibatching.kotpref.KotprefModel
import me.kafuuneko.rpclient.libs.defaults.DefaultNames
import me.kafuuneko.rpclient.libs.defaults.normalizedUserName
import me.kafuuneko.rpclient.libs.prompt.PromptPreferences
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.theme.AppThemeMode

/**
 * 应用级持久偏好模型。
 *
 * 这里只存放跨会话的全局设置和 Prompt 模板；角色、聊天及世界书等领域数据由 Room 管理。
 */
object AppModel : KotprefModel(), PromptPreferences {
    // 应用联系邮箱。
    const val EMAIL = "kafuuneko@gmail.com"

    // 仓库地址。
    const val GITHUB_REPO = "https://github.com/KafuuNeko/RPClient"

    // 默认美元兑人民币汇率，用于把美元报价折算成人民币展示。
    const val DEFAULT_USD_TO_CNY = "7.2"

    // 定制分支作者署名，展示在关于页。
    const val UV_AUTHOR = "yes a hit"

    /** 定制分支自身的版本标识，与上游 versionName 分开演进。 */
    const val UV_VERSION = "0.54"

    /** 定制说明文档在 assets 中的文件名。 */
    const val UV_README_ASSET = "README_UV.md"

    /** 导出定制说明时建议使用的文件名。 */
    const val UV_README_FILE_NAME = "RPClient-UV-README.md"

    /** 普通生成默认最多读取的最近历史消息数。 */
    const val DEFAULT_MAX_PROMPT_HISTORY_MESSAGES = 500

    // 最后一个成功完成的业务升级步骤 versionCode；保留属性名以兼容既有偏好键。
    var lastMigratedVersionCode by intPref(default = 0, commitByDefault = true)

    // 最后一个成功清理旧存储的业务升级步骤 versionCode。
    var lastCleanedUpgradeVersionCode by intPref(default = 0, commitByDefault = true)

    // 默认主提示词，作为普通对话生成时的全局系统指令。
    const val DEFAULT_MAIN_PROMPT = """
Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.
Write one reply only. Do not decide what {{user}} says or does.
"""

    // 默认助手提示词：助手管理应用自身资源，不参与角色扮演。
    const val DEFAULT_ASSISTANT_PROMPT = """
You are the built-in assistant of RPClient, an Android roleplay chat client.
You help the user manage the app's own resources: character cards, world books, and files inside your sandbox.
Answer in the user's language, be concise and factual, and never describe file contents you have not read.
"""

    // 默认摘要提示词，用于把未摘要聊天历史压缩为会话记忆。
    const val DEFAULT_SUMMARIZE_PROMPT = """
Please summarize the following chat history into concise story memory.
Rules:
- Do not continue roleplay.
- Do not generate a new reply for {{char}} or {{user}}.
- Only summarize what already happened in the chat.
- Preserve important facts, relationship changes, promises, injuries, locations, goals, unresolved conflicts and current scene state.
- Keep it within {{words}} words.
"""

    // 默认总结记忆包装模板，避免模型把剧情记忆与角色设定混为一谈。
    const val DEFAULT_SUMMARY_INJECTION_TEMPLATE = """
Story memory:
{{summary}}
"""

    // 默认历史后指令，追加在聊天历史之后。
    const val DEFAULT_POST_HISTORY_INSTRUCTIONS = ""

    // 默认辅助提示词，随角色定义放在聊天历史之前。
    const val DEFAULT_AUXILIARY_PROMPT = ""

    // 默认扮演用户提示词，用于从用户视角生成下一条消息。
    const val DEFAULT_IMPERSONATION_PROMPT = "[Write your next reply from the point of view of {{user}}. Do not write as {{char}} or system.]"

    // 默认新聊天边界提示词，用于区分设定区和真实聊天历史。
    const val DEFAULT_NEW_CHAT_PROMPT = "[Start a new Chat]"

    // 默认示例聊天边界提示词，用于标记示例对话块。
    const val DEFAULT_NEW_EXAMPLE_CHAT_PROMPT = "[Example Chat]"

    // 默认续写提示词，用于继续上一条助手回复。
    const val DEFAULT_CONTINUE_NUDGE_PROMPT = "[Continue your last message without repeating its original content.]"

    // 默认空消息替换文本；为空时输入框空白不会发送。
    const val DEFAULT_REPLACE_EMPTY_MESSAGE_PROMPT = ""

    // 默认世界书条目格式模板，{0} 会替换为世界书内容。
    const val DEFAULT_WORLD_INFO_FORMAT = "{0}"

    // 默认场景格式模板，{{scenario}} 会替换为角色场景。
    const val DEFAULT_SCENARIO_FORMAT = "{{scenario}}"

    // 默认性格格式模板，{{personality}} 会替换为角色性格。
    const val DEFAULT_PERSONALITY_FORMAT = "{{personality}}"

    // 默认用户人设格式模板，统一包装单聊、群聊与故事中的用户人设描述。
    const val DEFAULT_USER_PERSONA_FORMAT = "User Persona ({{user}}):\n{{persona}}"

    // 群聊生成尾部提示词，约束模型只输出当前角色的回复。
    const val DEFAULT_GROUP_NUDGE_PROMPT = """
Write only {{char}}'s next reply.
Stay in character. Do not speak for {{user}} or other group members.
Do not prefix the reply with a speaker name.
"""

    // 已有消息的群聊开始提示词，用于明确本轮参与成员。
    const val DEFAULT_NEW_GROUP_CHAT_PROMPT = "[Start a new group chat with {{group}}]"

    // 群聊摘要提示词，保留事件归属、关系状态和未解决事项。
    const val DEFAULT_GROUP_SUMMARIZE_PROMPT = """
Summarize the following group chat into concise story memory.
You are a memory summarizer, not a roleplay participant.
Rules:
- Only summarize events actually stated or confirmed in the chat.
- Preserve who said or did each important thing.
- Preserve relationships, promises, injuries, locations, goals, unresolved conflicts, current scene state and character-specific knowledge.
- Mark plans, suspicions and hypotheses as unconfirmed.
- Do not add character-card lore, world info or writing instructions as events.
- Keep it within {{words}} words.
"""

    // 默认故事模式主提示词只把当前章节作为正文，并由 Story 级记忆维持长篇连续性。
    const val DEFAULT_STORY_MAIN_PROMPT = """
You are a fiction-writing assistant editing the current chapter of a longer manuscript.
Treat Story Memory, Story Summary, Character References, and Lorebook entries as factual constraints.
Follow the requested writing operation and return only prose that can directly replace or extend the current chapter.
Do not explain your work, repeat the task, or wrap the result in a code block.
Preserve the manuscript's language, narrative voice, tense, style, and established facts unless explicitly instructed otherwise.
"""

    // 默认故事记忆模板，包装持久剧情记忆。
    const val DEFAULT_STORY_MEMORY_TEMPLATE = """
Story Memory:
{{memory}}
"""

    // 默认故事摘要模板，包装现有剧情摘要。
    const val DEFAULT_STORY_SUMMARY_TEMPLATE = """
Current Story Summary:
{{summary}}
"""

    // 默认故事摘要生成指令，指导模型将最新剧情收敛为定长摘要。
    const val DEFAULT_STORY_SUMMARIZE_PROMPT = """
Update the current story summary using the supplied story memory, current summary, and manuscript context.
Rules:
- Return the complete replacement summary, not commentary about the task.
- Only record events and facts stated or confirmed in the supplied material.
- Preserve important events, relationships, promises, injuries, locations, goals, character knowledge, unresolved threads, and the latest scene state.
- Distinguish plans, beliefs, suspicions, and unresolved questions from confirmed facts.
- Replace superseded temporary states with the latest confirmed state.
- Do not continue the story or add character-card lore, world information, writing instructions, or future plot ideas.
- Keep it within {{words}} words.
"""

    // 默认故事续写任务指令，要求紧接当前正文自然延伸。
    const val DEFAULT_STORY_CONTINUE_PROMPT = """
Continue the manuscript immediately after its current ending.
Do not repeat or summarize existing text. Advance the current scene naturally and return only the continuation.
"""

    // 默认故事用户续写指导包装模板。
    const val DEFAULT_STORY_CONTINUATION_GUIDANCE_PROMPT = """
Follow the user-provided continuation guidance below when writing the next passage.
Treat it as an instruction, not as manuscript text. Do not quote, repeat, explain, or mention the guidance in the output.

--- BEGIN CONTINUATION GUIDANCE ---
{{guidance}}
--- END CONTINUATION GUIDANCE ---
"""

    // 当前选中的模型配置 ID。
    var currentLLMProvider by longPref()

    // 应用配色主题，使用 AppThemeMode.persistedValue 持久化。
    var themeMode by stringPref(default = AppThemeMode.FollowSystem.persistedValue)

    // 摘要专用模型配置 ID；0 表示跟随当前全局模型配置。
    var summaryLLMProvider by longPref()

    /**
     * 角色卡生成向导专用模型配置 ID；0 表示跟随当前全局模型配置。
     *
     * 向导需要读图与结构化输出，能力要求与日常对话模型不同，因此单独选择；
     * 用户可指定一个专门的视觉模型，避免用识别精度不足的对话模型生成角色。
     */
    var characterWizardProvider by longPref()

    // 默认模型配置模板是否已完成首次初始化，防止用户删除全部配置后被自动重建。
    var llmDefaultProvidersInitialized by booleanPref(default = false)

    // OpenRouter 会话粘性使用的匿名安装 ID；不会发送原值，只发送按会话哈希后的 ID。
    var llmRoutingInstallationId by stringPref(default = "")

    // 主提示词（Main Prompt），注入每次普通对话生成的系统区。
    override var mainPrompt by stringPref(default = DEFAULT_MAIN_PROMPT)

    // 摘要提示词（Summarize Prompt），用于自动或手动总结聊天历史。
    var summarizePrompt by stringPref(default = DEFAULT_SUMMARIZE_PROMPT)

    // 全局历史后指令，追加在聊天历史之后；角色卡可通过 {{original}} 覆盖或继承该内容。
    override var postHistoryInstructions by stringPref(default = DEFAULT_POST_HISTORY_INSTRUCTIONS)

    // 辅助提示词，随角色定义和世界书固定区一起放在聊天历史之前。
    override var auxiliaryPrompt by stringPref(default = DEFAULT_AUXILIARY_PROMPT)

    // 扮演用户提示词，生成用户视角消息时作为尾部系统指令。
    override var impersonationPrompt by stringPref(default = DEFAULT_IMPERSONATION_PROMPT)

    // 新聊天边界提示词，插入在设定区和真实聊天历史之间。
    override var newChatPrompt by stringPref(default = DEFAULT_NEW_CHAT_PROMPT)

    // 示例聊天边界提示词，插入在每个示例对话块之前。
    override var newExampleChatPrompt by stringPref(default = DEFAULT_NEW_EXAMPLE_CHAT_PROMPT)

    // 续写提示词，继续最新助手回复时作为尾部系统指令。
    override var continueNudgePrompt by stringPref(default = DEFAULT_CONTINUE_NUDGE_PROMPT)

    // 空消息替换文本；输入框为空时若该字段非空，则使用它作为用户消息发送。
    var replaceEmptyMessagePrompt by stringPref(default = DEFAULT_REPLACE_EMPTY_MESSAGE_PROMPT)

    // 世界书格式模板，使用 {0} 包装已激活的世界书条目。
    override var worldInfoFormat by stringPref(default = DEFAULT_WORLD_INFO_FORMAT)

    // 场景格式模板，使用 {{scenario}} 包装角色场景文本。
    override var scenarioFormat by stringPref(default = DEFAULT_SCENARIO_FORMAT)

    // 性格格式模板，使用 {{personality}} 包装角色性格文本。
    override var personalityFormat by stringPref(default = DEFAULT_PERSONALITY_FORMAT)

    // 用户人设格式模板，使用 {{persona}} 包装人设正文，并可用 {{user}} 引用用户名。
    override var userPersonaFormat by stringPref(default = DEFAULT_USER_PERSONA_FORMAT)

    // 全局群聊生成尾部提示词，可由具体群聊会话覆盖。
    override var groupNudgePrompt by stringPref(default = DEFAULT_GROUP_NUDGE_PROMPT)

    // 全局群聊开始提示词，可由具体群聊会话覆盖。
    override var newGroupChatPrompt by stringPref(default = DEFAULT_NEW_GROUP_CHAT_PROMPT)

    // 群聊专用摘要提示词。
    var groupSummarizePrompt by stringPref(default = DEFAULT_GROUP_SUMMARIZE_PROMPT)

    // 连续故事正文的主指令、上下文模板、摘要指令和续写任务。
    var storyMainPrompt by stringPref(default = DEFAULT_STORY_MAIN_PROMPT)
    var storyMemoryTemplate by stringPref(default = DEFAULT_STORY_MEMORY_TEMPLATE)
    var storySummaryTemplate by stringPref(default = DEFAULT_STORY_SUMMARY_TEMPLATE)
    var storySummarizePrompt by stringPref(default = DEFAULT_STORY_SUMMARIZE_PROMPT)
    var storyContinuationGuidancePrompt by stringPref(
        default = DEFAULT_STORY_CONTINUATION_GUIDANCE_PROMPT
    )
    var storyContinuePrompt by stringPref(default = DEFAULT_STORY_CONTINUE_PROMPT)

    // 全部图片发送参数以一个值保存，避免并发请求读到混合配置。
    var imageSendSettings by stringPref(default = "auto:2048:1536:1536")

    // 聊天背景图片，存储文件 UUID；为空时聊天页使用主题纯色背景。
    var chatBackgroundImage by stringPref(default = "")

    // 聊天背景遮罩不透明度（0-100）。数值越高背景越淡，用于保证正文与气泡的可读性。
    var chatBackgroundDim by intPref(default = 35)

    /**
     * 外接生图服务的完整配置，以 JSON 串整体保存。
     *
     * 生图与对话是两条独立链路，接口地址、鉴权与模型名通常并不相同，
     * 因此不复用 [currentLLMProvider] 的配置，而是单独持久化一份。
     */
    var imageGenSettings by stringPref(default = "")

    /**
     * 示例 Skill 是否已写入过。
     *
     * 用于避免用户主动删除示例后又被自动重建；只在首次读取 Skill 列表时写入一次。
     */
    var skillStarterInitialized by booleanPref(default = false)

    /**
     * 助手是否继承全局主提示词。
     *
     * 继承时直接沿用 [mainPrompt]，关闭后改用 [assistantSystemPrompt]，
     * 便于给助手一套与角色扮演完全无关的独立指令。
     */
    var assistantInheritGlobalPrompt by booleanPref(default = true)

    // 助手独立系统提示词；仅在 [assistantInheritGlobalPrompt] 为 false 时生效。
    var assistantSystemPrompt by stringPref(default = DEFAULT_ASSISTANT_PROMPT)

    /**
     * 模型报价快照，按 JSON 保存「模型 ID → 单价」。
     *
     * 报价来自 OpenRouter 公开模型列表，离线时继续使用上次结果，
     * 因此不需要在应用内硬编码一份会过期的价格表。
     */
    var pricingSnapshotJson by stringPref(default = "")

    // 报价快照的抓取时间戳（毫秒），用于判断是否过期。
    var pricingFetchedAt by longPref(default = 0L)

    // 报价快照来源地址，便于界面注明出处。
    var pricingSourceUrl by stringPref(default = "")

    /**
     * 美元兑人民币汇率。
     *
     * 内置人民币价目覆盖不到的模型仍按 OpenRouter 美元报价取价，
     * 这里保存折算汇率，避免在计费代码里写死一个会过期的数字。
     */
    var usdToCnyRate by stringPref(default = DEFAULT_USD_TO_CNY)

    /** 解析后的汇率；空值或非法输入回落到默认值。 */
    val resolvedUsdToCnyRate: Double
        get() = usdToCnyRate.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: DEFAULT_USD_TO_CNY.toDouble()

    // 是否启用流式响应。
    var streamEnabled by booleanPref(default = true)

    // Prompt 宏中的用户名称，对应 {{user}}。
    var userName by stringPref(default = DefaultNames.USER)

    /** 始终可安全用于展示、持久化和 Prompt 宏的全局用户名。 */
    val resolvedUserName: String
        get() = userName.normalizedUserName()

    // 用户头像，存储的是 File UUID。
    var userAvatar by stringPref(default = "")

    // 用户人格描述，对应 {{persona}} 或 persona description。
    var userDescription by stringPref(default = "")

    // 摘要目标字数限制，对应摘要提示词中的 {{words}}。
    var summaryWordsLimit by intPref(default = 500)

    // 是否启用自动摘要。
    var autoSummaryEnabled by booleanPref(default = true)

    // 自动摘要触发消息数，未摘要消息达到该数量后触发。
    var summaryTriggerMessageCount by intPref(default = 20)

    // 单次摘要最多纳入的未摘要消息数；0 表示由上下文预算自动裁剪。
    var summaryMaxMessagesPerRequest by intPref(default = 0)

    // 摘要请求的最大输出 token 数。
    var summaryResponseTokens by intPref(default = 1000)

    // 总结记忆注入常规聊天 Prompt 时使用的包装模板。
    override var summaryInjectionTemplate by stringPref(default = DEFAULT_SUMMARY_INJECTION_TEMPLATE)

    // 摘要注入位置使用 SummaryInjectionPosition.persistedValue 持久化。
    override var summaryInjectionPosition by intPref(
        default = SummaryInjectionPosition.default.persistedValue
    )

    // 摘要位于聊天内时，从聊天末尾向前计算的插入深度。
    override var summaryInjectionDepth by intPref(default = 2)

    // 摘要位于聊天内时使用的消息角色，对应 SummaryInjectionRole.persistedValue。
    override var summaryInjectionRole by intPref(default = 0)

    // 世界书占输入 Prompt 预算的百分比。
    override var worldInfoBudgetPercent by intPref(default = 25)

    // 世界书固定 Token 上限；0 表示仅使用上下文百分比，不额外设上限。
    override var worldInfoBudgetCap by intPref(default = 0)

    // 世界书候选条目因独立预算未进入 Prompt 时是否提示用户。
    var worldInfoOverflowAlert by booleanPref(default = true)

    // Prompt 内容因上下文预算被正常裁剪时是否提示用户。
    var contextTrimmingAlert by booleanPref(default = false)

    // 示例对话的全局保留策略，使用 ExampleDialogueBehavior.persistedValue 持久化。
    var exampleDialogueBehavior by intPref(default = ExampleDialogueBehavior.default.persistedValue)

    // 是否把已保存消息中的 <think>...</think> 思考块继续纳入后续上下文。
    override var includeThinkInContext by booleanPref(default = false)

    // 普通生成最多读取的最近历史消息数；0 表示不限制并保持旧版行为。
    var maxPromptHistoryMessages by intPref(default = DEFAULT_MAX_PROMPT_HISTORY_MESSAGES)

    // 是否启用调试模式；开启后记录原始 LLM 请求和响应 JSON。
    var debugModeEnabled by booleanPref(default = false)

}
