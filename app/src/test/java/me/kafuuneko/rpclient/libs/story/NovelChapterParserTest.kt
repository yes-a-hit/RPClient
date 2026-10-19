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
}
