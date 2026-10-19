package me.kafuuneko.rpclient.libs.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分章解析测试。
 *
 * 本地小说格式高度不统一，这里固定住三类关键行为：
 * 常见标题形态能被识别、正文里的引用句不会被误判成标题、无标题文本整体成章。
 */
class NovelChapterParserTest {
    @Test
    fun `splits chinese chapter headings`() {
        val text = """
            第一章 初遇
            傍晚的风很凉。
            第二章 离去
            他没有回头。
        """.trimIndent()

        val chapters = NovelChapterParser.parse(text)

        assertEquals(2, chapters.size)
        assertEquals("第一章 初遇", chapters[0].title)
        assertTrue(chapters[0].content.contains("傍晚的风很凉"))
        assertEquals("第二章 离去", chapters[1].title)
    }

    @Test
    fun `splits english and markdown headings`() {
        val text = """
            # Prologue
            Rain.
            Chapter 2
            Wind.
        """.trimIndent()

        val chapters = NovelChapterParser.parse(text)

        assertEquals(2, chapters.size)
        assertEquals("Prologue", chapters[0].title)
        assertEquals("Chapter 2", chapters[1].title)
    }

    @Test
    fun `keeps preface before first heading`() {
        val text = """
            这是作者的絮语。
            第一章 开始
            正文。
        """.trimIndent()

        val chapters = NovelChapterParser.parse(text)

        assertEquals(2, chapters.size)
        assertEquals("", chapters[0].title)
        assertTrue(chapters[0].content.contains("絮语"))
    }

    @Test
    fun `text without heading becomes single chapter`() {
        val chapters = NovelChapterParser.parse("只有一段没有标题的正文。")

        assertEquals(1, chapters.size)
        assertEquals("", chapters[0].title)
    }

    @Test
    fun `blank text yields nothing`() {
        assertTrue(NovelChapterParser.parse("   \n  ").isEmpty())
    }

    @Test
    fun `long line mentioning a chapter is not a heading`() {
        val sentence = "他翻到第三章的时候，忽然想起那句话——所有未曾说出口的告别都藏在书页的褶皱里，再也无法展开。"

        assertNull(NovelChapterParser.detectHeading(sentence))
    }

    @Test
    fun `special headings are recognized`() {
        assertEquals("楔子", NovelChapterParser.detectHeading("楔子"))
        assertEquals("番外 一", NovelChapterParser.detectHeading("番外 一"))
    }

    @Test
    fun `numbered headings in mixed widths are recognized`() {
        assertEquals("01 起风", NovelChapterParser.detectHeading("01 起风"))
        assertEquals("1. 渡口", NovelChapterParser.detectHeading("1. 渡口"))
        assertEquals("001、归途", NovelChapterParser.detectHeading("001、归途"))
    }

    @Test
    fun `plain number line is not a heading`() {
        assertNull(NovelChapterParser.detectHeading("12"))
        assertNull(NovelChapterParser.detectHeading("2026年春天，他回来了。"))
    }

    @Test
    fun `numbered headings split into chapters`() {
        val text = """
            001 起风
            夜里下起了雨。
            002 渡口
            船夫松开缆绳。
        """.trimIndent()

        val chapters = NovelChapterParser.parse(text)

        assertEquals(2, chapters.size)
        assertEquals("001 起风", chapters[0].title)
        assertEquals("002 渡口", chapters[1].title)
    }

    @Test
    fun `volume headings group following chapters`() {
        val text = """
            第一卷 风起
            第一章 初遇
            正文一。
            第二章 别离
            正文二。
            第二卷 云涌
            第一章 重逢
            正文三。
        """.trimIndent()

        val chapters = NovelChapterParser.parse(text)

        // 卷标题不生成章节，只改变后续章节归属
        assertEquals(3, chapters.size)
        assertEquals("第一卷 风起", chapters[0].volume)
        assertEquals("第一卷 风起", chapters[1].volume)
        assertEquals("第二卷 云涌", chapters[2].volume)
        assertEquals("第一章 初遇", chapters[0].title)
    }

    @Test
    fun `english volume heading is recognized`() {
        assertEquals("Volume 2", NovelChapterParser.detectVolumeHeading("Volume 2"))
        assertNull(NovelChapterParser.detectVolumeHeading("Chapter 2"))
    }
}
