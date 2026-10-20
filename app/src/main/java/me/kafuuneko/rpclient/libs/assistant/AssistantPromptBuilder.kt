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
    fun buildSystemPrompt(galleryBrief: List<String> = emptyList()): String {
        val inheritGlobal = AppModel.assistantInheritGlobalPrompt
        val base = if (inheritGlobal) AppModel.mainPrompt else AppModel.assistantSystemPrompt
        val resolved = base
            .replace("{{user}}", AppModel.resolvedUserName)
            .replace("{{char}}", ASSISTANT_NAME)
            .trim()
        // 顺序很重要：身份与工具协议放最前，用户主提示词退到后面。
        // 反过来的话，继承来的角色扮演设定会把模型带成「角色」，
        // 工具说明再全也会被当成旁枝忽略掉。
        return listOf(
            identityBlock(),
            AssistantToolCatalog.INSTRUCTIONS,
            galleryBlock(galleryBrief),
            resolved
        )
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
- 本段与下方工具协议的优先级高于后面可能出现的角色设定；即使文末给了角色描写，
  你依然是助手，按工具行事，不要改口演成那个角色。
""".trimIndent()

    /**
     * 图库清单。
     *
     * 只给标签名不够：模型得知道「有哪些图、各自什么时候适合发」才可能主动挑图。
     * 清单按加入顺序截断，避免图多时把上下文撑满。
     */
    private fun galleryBlock(brief: List<String>): String {
        if (brief.isEmpty()) return ""
        return buildString {
            append("## 图库\n\n")
            append("以下图片可以随时发给用户，用 `send_image` 按标签或名称挑一张。\n")
            append("标签就是适用情境：标了「开心」表示适合气氛轻松时发，标了「meme」表示适合抖机灵。\n\n")
            brief.take(MAX_GALLERY_BRIEF).forEach { line ->
                append("- ").append(line).append('\n')
            }
            append("\n合适就发，没合适的别硬发。")
        }
    }

    /** 清单最多列出的条数，超出部分模型可先用 list_gallery 自查。 */
    private const val MAX_GALLERY_BRIEF = 30
}
