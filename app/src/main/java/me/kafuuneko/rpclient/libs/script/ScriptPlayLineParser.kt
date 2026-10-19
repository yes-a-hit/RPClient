package me.kafuuneko.rpclient.libs.script

/** 模型回复中的一段内容。 */
data class ScriptPlayLine(
    /** 发言者名称；旁白为 null。 */
    val speaker: String?,
    val text: String
) {
    /** 是否为旁白。 */
    val isNarration: Boolean
        get() = speaker == null
}

/**
 * 把剧本模式的回复切成「旁白」与「角色台词」两类段落。
 *
 * 约定模型这样写：
 * ```
 * 【旁白】走廊尽头的灯闪了一下。
 * 【柳如烟】「你被裁了。」
 * ```
 * 这样界面就能给角色台词配头像气泡，旁白保持素文本——
 * 全塞成一个气泡会让"谁在说话"完全看不出来。
 */
object ScriptPlayLineParser {
    /** 段首标记：`【名字】`，名字不超过 20 字，避免把正文里的书名号当标记。 */
    private val markerPattern = Regex("【([^】\\n]{1,20})】")

    /** 旁白的别名集合。 */
    private val narrationNames = setOf("旁白", "场景", "系统", "背景")

    /**
     * 解析一条回复。
     *
     * 没有识别到任何标记时，整段作为旁白返回，
     * 保证旧存档与不规范输出也能正常显示。
     */
    fun parse(reply: String): List<ScriptPlayLine> {
        val text = reply.trim()
        if (text.isEmpty()) return emptyList()

        val matches = markerPattern.findAll(text).toList()
        if (matches.isEmpty()) {
            return listOf(ScriptPlayLine(speaker = null, text = text))
        }

        val lines = mutableListOf<ScriptPlayLine>()
        // 首个标记之前的零散文本也当旁白，避免丢内容
        val leading = text.substring(0, matches.first().range.first).trim()
        if (leading.isNotEmpty()) lines += ScriptPlayLine(speaker = null, text = leading)

        matches.forEachIndexed { index, match ->
            val name = match.groupValues[1].trim()
            val start = match.range.last + 1
            val end = if (index + 1 < matches.size) {
                matches[index + 1].range.first
            } else {
                text.length
            }
            val body = text.substring(start, end).trim()
            if (body.isEmpty()) return@forEachIndexed
            lines += ScriptPlayLine(
                speaker = if (name in narrationNames) null else name,
                text = body
            )
        }
        return lines
    }
}
