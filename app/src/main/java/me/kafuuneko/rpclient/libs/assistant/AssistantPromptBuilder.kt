package me.kafuuneko.rpclient.libs.assistant

import me.kafuuneko.rpclient.libs.AppModel

/**
 * 助手 system prompt 组装。
 *
 * 提示词来源二选一：继承全局主提示词，或使用助手自己的独立提示词；
 * 无论哪种，都会追加助手身份边界与工具协议，保证工具调用格式始终明确。
 */
object AssistantPromptBuilder {
    /** 提示词宏里代表助手自身的占位名。 */
    const val ASSISTANT_NAME = "Assistant"

    /**
     * 生成当前应使用的 system prompt。
     *
     * @return 已解析宏、并拼接身份边界与工具说明的完整系统提示词
     */
    fun buildSystemPrompt(): String {
        val inheritGlobal = AppModel.assistantInheritGlobalPrompt
        val base = if (inheritGlobal) AppModel.mainPrompt else AppModel.assistantSystemPrompt
        val resolved = base
            .replace("{{user}}", AppModel.resolvedUserName)
            .replace("{{char}}", ASSISTANT_NAME)
            .trim()
        return listOf(resolved, identityBlock(), AssistantToolCatalog.INSTRUCTIONS)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    /** 提示词来源的展示文案，供页面显示当前模式。 */
    fun describeSource(): String =
        if (AppModel.assistantInheritGlobalPrompt) "继承全局主提示词" else "使用独立提示词"

    /** 身份边界：避免助手把自己当成角色扮演对象或声称拥有沙箱以外的能力。 */
    private fun identityBlock(): String = """
## 助手身份

你运行在 RPClient 内部，作为资源管理助手协助用户，而不是扮演任何角色。
- 你的权限仅限于沙箱目录内的文件，以及通过工具读取和修改角色卡。
- 世界书、聊天记录等未提供工具的数据，你只能说明无法直接访问，不要臆测其内容。
- 需要多步操作时先做完再汇报，不要把未执行的计划说成已完成。
""".trimIndent()
}
