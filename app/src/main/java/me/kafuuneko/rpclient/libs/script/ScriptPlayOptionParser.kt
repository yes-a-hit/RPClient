package me.kafuuneko.rpclient.libs.script

/**
 * 从模型回复里解析推荐行动。
 *
 * 约定模型在正文末尾单独给一段选项，用 `<<<OPTIONS` 与 `>>>` 包住，
 * 每行一个候选。之所以用显式标记而不是靠自然语言猜，
 * 是为了能干净地把选项从正文里剥掉，避免它们被当成剧情重复显示。
 */
object ScriptPlayOptionParser {
    /** 选项块的正则：起止标记之间的全部内容。 */
    private val blockPattern = Regex(
        "<<<OPTIONS([\\s\\S]*?)>>>",
        RegexOption.IGNORE_CASE
    )

    /** 单个选项行：允许 `1.`、`-`、`•` 等前缀。 */
    private val linePattern = Regex("^\\s*(?:[0-9]+[.、)]|[-•*])\\s*(.+)$")

    /**
     * 解析回复。
     *
     * @return 正文（已去掉选项块）与选项列表；没有选项块时正文原样返回
     */
    fun parse(reply: String): ScriptPlayOptions {
        val block = blockPattern.find(reply) ?: return ScriptPlayOptions(reply.trim(), emptyList())
        val options = block.groupValues[1]
            .lines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                // 有编号前缀就取前缀后的内容，否则整行作为一个选项
                linePattern.find(trimmed)?.groupValues?.getOrNull(1)?.trim() ?: trimmed
            }
            .filter { it.isNotBlank() }
            .take(MAX_OPTIONS)
        val visible = reply.replace(block.value, "").trim()
        return ScriptPlayOptions(visible, options)
    }

    /** 最多展示的候选数，太多反而让人无从选择。 */
    private const val MAX_OPTIONS = 3
}

/** 解析结果。 */
data class ScriptPlayOptions(
    /** 去掉选项块后的正文。 */
    val text: String,
    /** 推荐行动列表。 */
    val options: List<String>
)
