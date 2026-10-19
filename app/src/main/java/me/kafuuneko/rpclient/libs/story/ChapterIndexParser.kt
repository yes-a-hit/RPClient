package me.kafuuneko.rpclient.libs.story

/**
 * 章节序号解析与排序。
 *
 * 小说章节的编号写法非常杂：`第12章`、`第十二章`、`第1.5章`、`01 标题`、`Chapter 3`，
 * 还有 `番外`、`后记` 这类没有序号的特殊章。这里把它们统一折算成一个可比较的数值：
 *
 * - 普通序号按数值大小比较，支持小数（`1.5` 落在 1 与 2 之间）。
 * - 序章 / 楔子 / 前言折算成负数，排在最前。
 * - 番外 / 后记 / 尾声折算成一个很大的值，排在最后。
 * - 完全解析不出序号时返回 null，由调用方决定是保留原顺序还是排到末尾。
 */
object ChapterIndexParser {
    /** 无法解析序号时的排序权重，保证它们落在所有正常章节之后。 */
    const val NO_INDEX_WEIGHT = Double.MAX_VALUE

    /** 特殊前置章（序章等）的序号。 */
    private const val PROLOGUE_INDEX = -1.0

    /** 特殊后置章（番外等）的序号。 */
    private const val APPENDIX_INDEX = Double.MAX_VALUE / 2

    private val prologueHeading = Regex("^\\s*(序章|序言|序|楔子|引子|引言|前言)")
    private val appendixHeading = Regex("^\\s*(番外|后记|尾声|终章|附录)")

    private val chineseChapter = Regex("第\\s*([零〇一二三四五六七八九十百千两]+)\\s*[章节回篇话]")
    private val arabicChapter = Regex("第\\s*(\\d+(?:\\.\\d+)?)\\s*[章节回篇话]")
    private val englishChapter = Regex(
        "(?:chapter|part|book)\\s+(\\d+(?:\\.\\d+)?)",
        RegexOption.IGNORE_CASE
    )
    private val bareNumber = Regex("^\\s*(\\d+(?:\\.\\d+)?)")

    private val chineseDigits = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )

    /**
     * 从标题或文件名中解析序号。
     *
     * @param text 章节标题、文件名或路径末段
     * @return 可比较的序号；无序号时返回 null
     */
    fun parseIndex(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (prologueHeading.containsMatchIn(trimmed)) return PROLOGUE_INDEX
        if (appendixHeading.containsMatchIn(trimmed)) return APPENDIX_INDEX

        // 第X章：先试阿拉伯数字，再试中文数字
        arabicChapter.find(trimmed)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { return it }
        chineseChapter.find(trimmed)?.groupValues?.getOrNull(1)?.let { chinese ->
            chineseToNumber(chinese)?.let { return it }
        }
        englishChapter.find(trimmed)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { return it }
        bareNumber.find(trimmed)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { return it }
        return null
    }

    /**
     * 按序号稳定排序章节。
     *
     * 只有同时满足两个条件才重排：
     * - 全部章节都能解析出序号，否则保持原文顺序——部分编号缺失的小说强行排序会把正文打乱；
     * - 章节没有被分卷，因为分卷小说每卷章号都从 1 重新开始，跨卷全局排序会打乱卷内结构。
     *
     * @param chapters 解析出的章节
     * @return 需要重排时返回新列表，否则原样返回
     */
    fun sortChapters(chapters: List<NovelChapter>): List<NovelChapter> {
        if (chapters.size < 2) return chapters
        if (chapters.any { it.volume != null }) return chapters
        val indexed = chapters.map { it to parseIndex(it.title) }
        if (indexed.any { it.second == null }) return chapters
        // sortedBy 是稳定排序，同序号章节保持原有先后
        return indexed.sortedBy { it.second }.map { it.first }
    }

    /**
     * 中文数字转数值，支持到千位。
     *
     * @param text 形如 `十二`、`二十一`、`一百零八`
     * @return 对应数值；含非数字字符时返回 null
     */
    fun chineseToNumber(text: String): Double? {
        if (text.isEmpty()) return null
        var total = 0
        var section = 0
        var current = 0
        for (char in text) {
            val digit = chineseDigits[char]
            when {
                digit != null -> current = digit
                char == '十' -> {
                    section += (if (current == 0) 1 else current) * 10
                    current = 0
                }

                char == '百' -> {
                    section += (if (current == 0) 1 else current) * 100
                    current = 0
                }

                char == '千' -> {
                    section += (if (current == 0) 1 else current) * 1000
                    current = 0
                }

                else -> return null
            }
        }
        total += section + current
        return total.toDouble()
    }
}
