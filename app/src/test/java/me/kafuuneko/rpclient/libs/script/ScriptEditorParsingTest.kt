package me.kafuuneko.rpclient.libs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编辑器的文本输入解析。
 *
 * 这些规则最容易在边界上出错：多一个空格、少一个数字、
 * 中文标点混进来。在界面上手点很难覆盖到。
 */
class ScriptEditorParsingTest {

    @Test
    fun `effect line reads variable and delta`() {
        val result = parseEffectLines("电量 -10\n好感 +2")

        assertEquals(2, result.size)
        assertEquals("电量", result[0].variable)
        assertEquals(-10, result[0].delta)
        assertEquals("好感", result[1].variable)
        assertEquals(2, result[1].delta)
    }

    @Test
    fun `effect line without number defaults to plus one`() {
        // 作者写「好感」通常就是想加一点，静默失败更让人困惑
        val result = parseEffectLines("好感")

        assertEquals(1, result.size)
        assertEquals(1, result[0].delta)
    }

    @Test
    fun `blank effect lines are dropped`() {
        val result = parseEffectLines("\n\n  \n电量 -5\n\n")

        assertEquals(1, result.size)
    }

    @Test
    fun `clue line splits on first whitespace only`() {
        // 内容里允许有空格，不能把后半句吃掉
        val result = parseClueLines("线索 厨房的刀不见了 而且在动")

        assertEquals(1, result.size)
        assertEquals("线索", result[0].variable)
        assertEquals("厨房的刀不见了 而且在动", result[0].appendText)
    }

    @Test
    fun `clue line without content is dropped`() {
        // 只有变量名没有内容，记下来也没意义
        assertTrue(parseClueLines("线索").isEmpty())
        assertTrue(parseClueLines("线索   ").isEmpty())
    }

    @Test
    fun `clue and effect use different fields`() {
        // 线索走 appendText，数值走 delta，混了会把线索冲掉
        val clue = parseClueLines("提示 左门的锁是坏的").single()
        val effect = parseEffectLines("提示 -1").single()

        assertEquals("左门的锁是坏的", clue.appendText)
        assertEquals(0, clue.delta)
        assertEquals(-1, effect.delta)
        assertEquals("", effect.appendText)
    }

    @Test
    fun `multiple clue lines all parsed`() {
        val result = parseClueLines("提示 厨房少了把刀\n提示 冰柜门缝有血")

        assertEquals(2, result.size)
        assertEquals("厨房少了把刀", result[0].appendText)
        assertEquals("冰柜门缝有血", result[1].appendText)
    }
}
