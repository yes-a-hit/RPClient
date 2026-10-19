package me.kafuuneko.rpclient.libs.assistant

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 助手工具协议：把模型回复里的结构化调用解析成可执行动作。
 *
 * 协议刻意不使用各供应商的 function calling，因为它们对工具调用的报文格式互不兼容；
 * 这里约定模型在正文中输出 `<tool name="...">{...}</tool>`，由应用解析并回填结果。
 * 纯文本协议在任何 OpenAI Compatible / Gemini / Anthropic 端点上都能工作。
 */
object AssistantToolCatalog {
    /** 单次回复最多执行的工具轮数，避免模型自我循环。 */
    const val MAX_TOOL_ROUNDS = 5

    /** 单个文件读写上限，防止模型把整个沙箱内容塞进上下文。 */
    const val MAX_FILE_BYTES = 256 * 1024

    /** 读取文本时回填给模型的最大字符数。 */
    const val MAX_READ_CHARS = 8000

    /** 工具调用块的匹配模式；正文里允许跨行参数。 */
    val TOOL_PATTERN = Regex("""<tool\s+name="([^"]+)"\s*>(.*?)</tool>""", RegexOption.DOT_MATCHES_ALL)

    /**
     * 注入给模型的工具说明。
     *
     * 与 [INSTRUCTIONS] 一起放进 system prompt，说明可做什么、边界在哪、结果如何回填。
     */
    val INSTRUCTIONS: String = """
## 可用工具

你可以通过工具读写沙箱文件和编辑角色卡。需要操作时，在正文之外单独输出一行调用块：

    <tool name="工具名">{"参数": "值"}</tool>

- 一次回复可以连续输出多个调用块，应用会按顺序执行。
- 执行结果会以 `<tool_result name="...">...</tool_result>` 的形式回填给你，随后你可以继续回答。
- 工具失败时返回的错误信息同样会回填，请据此调整，不要重复同样的失败调用。

可用工具：

- `list_files` `{"path":"."}`：列出沙箱目录内容。
- `read_file` `{"path":"note.txt"}`：读取沙箱内文本文件。
- `write_file` `{"path":"note.txt","content":"..."}`：写入或覆盖文本文件。
- `move_file` `{"from":"a.txt","to":"archive/a.txt"}`：移动或重命名文件。
- `delete_file` `{"path":"a.txt"}`：删除文件。
- `list_characters` `{}`：列出全部角色卡（含 ID、名称、描述摘要）。
- `get_character` `{"id":1}`：读取一张角色卡的完整字段。
- `update_character` `{"id":1,"fields":{"name":"新名字","description":"..."}}`：修改角色卡字段。
- `list_worldbooks` `{}`：列出全部世界书及其条目数量。
- `create_worldbook` `{"name":"设定集"}`：新建一本空世界书，返回其 ID。
- `add_worldbook_entry` `{"worldbookId":1,"name":"条目名","keys":["关键词"],"content":"设定正文"}`：向世界书追加一条条目。
- `get_worldbook_entries` `{"worldbookId":1}`：读取某本世界书已有条目的名称与关键词。

`update_character` 仅接受这些字段：name、description、personality、scenario、firstMessages、
postHistoryInstructions、creatorNotes、systemPrompt。未提供的字段保持原值。

把零散设定整理成世界书时，先侦察再落库：用 `read_file` 看清沙子里的内容，用 `list_worldbooks`
确认目标是新建还是追加，逐条 `add_worldbook_entry` 写入，最后汇报写了几条、落在哪本世界书。

所有路径都相对沙箱根目录，不允许跳出沙箱。你不了解沙箱外部的任何内容，也不要声称了解。
""".trimIndent()

    /** 从模型正文中解析全部工具调用。 */
    fun parse(content: String): List<AssistantToolCall> {
        if (content.isBlank()) return emptyList()
        val gson = Gson()
        return TOOL_PATTERN.findAll(content).mapNotNull { match ->
            val name = match.groupValues[1].trim()
            if (name.isBlank()) return@mapNotNull null
            val rawArguments = match.groupValues[2].trim()
            val arguments = runCatching {
                gson.fromJson(rawArguments.ifBlank { "{}" }, JsonObject::class.java)
            }.getOrNull() ?: JsonObject()
            AssistantToolCall(name = name, arguments = arguments)
        }.toList()
    }

    /** 去掉正文中的调用块，只保留给用户看的自然语言部分。 */
    fun stripToolBlocks(content: String): String =
        TOOL_PATTERN.replace(content, "").trim()

    /** 按名称取参数；缺失或类型不符时返回 null。 */
    internal fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return element.asString
    }

    /** 按名称取整数参数；兼容字符串形式的数字。 */
    internal fun JsonObject.longOrNull(key: String): Long? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asLong }.getOrNull()
    }

    /** 按名称取字符串数组参数；缺失时返回空列表。 */
    internal fun JsonObject.stringList(key: String): List<String> {
        val element = get(key) ?: return emptyList()
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { item ->
            if (item.isJsonPrimitive) item.asString.trim().takeIf { it.isNotBlank() } else null
        }
    }
}

/** 一次解析出来的工具调用。 */
data class AssistantToolCall(
    val name: String,
    val arguments: JsonObject
)
