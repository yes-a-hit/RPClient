package me.kafuuneko.rpclient.libs.story

/** 解析出的一章：标题可为空，表示整篇没有可用标题结构。 */
data class NovelChapter(
    val title: String,
    val content: String
)

/**
 * 本地小说章节切分器。
 *
 * 面向「把整本 TXT/Markdown 丢进来」的场景，只负责识别标题行并切段，
 * 不做去空行、编码猜测或标点归一，避免把正文改坏。
 *
 * - 支持的标题形态：`第X章/回/节/篇/卷/话`、`Chapter N`、Markdown `#` 标题，
 *   以及 `序章`、`楔子`、`番外` 等固定词。
 * - 标题行长度上限为 [MAX_TITLE_LENGTH]，超过视为正文，避免把引用句误判为标题。
 * - 首个标题之前若存在正文，会作为无标题章节保留，由调用方补默认标题。
 */
object NovelChapterParser {
    /** 标题行允许的最大字符数。 */
    const val MAX_TITLE_LENGTH = 40

    private val chineseHeading = Regex(
        "^\\s*第\\s*[0-9零〇一二三四五六七八九十百千万两]+\\s*[章回节篇卷话](\\s|$|[：:.、·—-])"
    )

    private val englishHeading = Regex(
        "^\\s*(chapter|part|book)\\s+([0-9]+|[ivxlcdm]+)\\b",
        RegexOption.IGNORE_CASE
    )

    private val markdownHeading = Regex("^\\s*#{1,3}\\s+\\S")

    private val specialHeading = Regex(
        "^\\s*(序章|序言|序|楔子|引子|引言|前言|尾声|终章|后记|附录|番外)"
    )

    /**
     * 把整篇文本切分为章节列表。
     *
     * @param text 小说全文，允许混用 CRLF 与 LF
     * @return 按出现顺序排列的章节；空文本返回空列表
     */
    fun parse(text: String): List<NovelChapter> {
        if (text.isBlank()) return emptyList()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val chapters = mutableListOf<NovelChapter>()
        val buffer = StringBuilder()
        var currentTitle: String? = null

        // 逐行扫描：命中标题就把已累积正文封成一章，未命中则继续累积
        for (line in lines) {
            val heading = detectHeading(line)
            if (heading != null) {
                appendChapter(chapters, currentTitle, buffer)
                currentTitle = heading
            } else {
                buffer.append(line).append('\n')
            }
        }
        appendChapter(chapters, currentTitle, buffer)

        // 丢弃纯空白片段，保留标题或正文任一非空的章节
        return chapters.filter { it.title.isNotBlank() || it.content.isNotBlank() }
    }

    /**
     * 判断一行是否是章节标题。
     *
     * @return 归一到可展示的标题文本；不是标题时返回 null
     */
    fun detectHeading(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_TITLE_LENGTH) return null
        // Markdown 标题去掉井号后作为标题正文
        if (markdownHeading.containsMatchIn(trimmed)) {
            return trimmed.trimStart('#').trim().ifBlank { null }
        }
        if (chineseHeading.containsMatchIn(trimmed)) return trimmed
        if (englishHeading.containsMatchIn(trimmed)) return trimmed
        if (specialHeading.containsMatchIn(trimmed)) return trimmed
        return null
    }

    /** 把当前累积内容封成一章并清空缓冲。 */
    private fun appendChapter(
        chapters: MutableList<NovelChapter>,
        title: String?,
        buffer: StringBuilder
    ) {
        val content = buffer.toString().trim()
        buffer.setLength(0)
        if (title == null && content.isEmpty()) return
        chapters += NovelChapter(title = title?.trim().orEmpty(), content = content)
    }
}
