package me.kafuuneko.rpclient.libs.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 章节序号解析测试。
 *
 * 小说编号写法混杂，这里固定住四类口径：
 * 中文数字、阿拉伯数字（含小数）、裸数字前缀、无序号特殊章。
 */
class ChapterIndexParserTest {
    @Test
    fun `chinese numerals convert to value`() {
        assertEquals(1.0, ChapterIndexParser.chineseToNumber("一") ?: 0.0, 0.001)
        assertEquals(12.0, ChapterIndexParser.chineseToNumber("十二") ?: 0.0, 0.001)
        assertEquals(21.0, ChapterIndexParser.chineseToNumber("二十一") ?: 0.0, 0.001)
        assertEquals(108.0, ChapterIndexParser.chineseToNumber("一百零八") ?: 0.0, 0.001)
    }

    @Test
    fun `parses index from common headings`() {
        assertEquals(12.0, ChapterIndexParser.parseIndex("第十二章 归来") ?: 0.0, 0.001)
        assertEquals(7.0, ChapterIndexParser.parseIndex("第7章 出发") ?: 0.0, 0.001)
        assertEquals(1.5, ChapterIndexParser.parseIndex("第1.5章 插曲") ?: 0.0, 0.001)
        assertEquals(3.0, ChapterIndexParser.parseIndex("003 渡口") ?: 0.0, 0.001)
        assertEquals(4.0, ChapterIndexParser.parseIndex("Chapter 4") ?: 0.0, 0.001)
    }

    @Test
    fun `prologue and appendix get special weights`() {
        val prologue = ChapterIndexParser.parseIndex("序章") ?: 0.0
        val appendix = ChapterIndexParser.parseIndex("番外 一") ?: 0.0
        assertEquals(true, prologue < 0)
        assertEquals(true, appendix > 1000)
    }

    @Test
    fun `unparsable title returns null`() {
        assertNull(ChapterIndexParser.parseIndex("写在前面的话"))
    }

    @Test
    fun `chapters are sorted by numeric index`() {
        val chapters = listOf(
            NovelChapter("第十章 尾", "c"),
            NovelChapter("第二章 中", "b"),
            NovelChapter("第1.5章 插曲", "ab"),
            NovelChapter("第一章 首", "a")
        )

        val sorted = ChapterIndexParser.sortChapters(chapters).map { it.title }

        assertEquals(listOf("第一章 首", "第1.5章 插曲", "第二章 中", "第十章 尾"), sorted)
    }

    @Test
    fun `missing index keeps original order`() {
        val chapters = listOf(
            NovelChapter("第二章", "b"),
            NovelChapter("无编号段落", "x"),
            NovelChapter("第一章", "a")
        )

        val sorted = ChapterIndexParser.sortChapters(chapters).map { it.title }

        assertEquals(listOf("第二章", "无编号段落", "第一章"), sorted)
    }
}