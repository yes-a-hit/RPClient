package me.kafuuneko.rpclient.libs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 选项块解析测试：覆盖有闭合标记、漏写闭合、无选项三种情况。 */
class ScriptPlayOptionParserTest {
    @Test
    fun `parses options with closing marker`() {
        val reply = "你推开门。\n<<<OPTIONS\n1. 走进去\n2. 转身离开\n>>>"

        val result = ScriptPlayOptionParser.parse(reply)

        assertEquals("你推开门。", result.text)
        assertEquals(listOf("走进去", "转身离开"), result.options)
    }

    @Test
    fun `parses options without closing marker`() {
        // 模型常漏写 >>>，此时整段仍要能被剥离，不能漏进正文
        val reply = "你推开门。\n<<<OPTIONS\n1. 走进去\n2. 转身离开"

        val result = ScriptPlayOptionParser.parse(reply)

        assertEquals("你推开门。", result.text)
        assertEquals(listOf("走进去", "转身离开"), result.options)
        assertFalse(result.text.contains("OPTIONS"))
    }

    @Test
    fun `plain reply has no options`() {
        val result = ScriptPlayOptionParser.parse("只有一句旁白。")

        assertEquals("只有一句旁白。", result.text)
        assertTrue(result.options.isEmpty())
    }

    @Test
    fun `caps options to three`() {
        val reply = "<<<OPTIONS\n1. 一\n2. 二\n3. 三\n4. 四\n>>>"

        assertEquals(3, ScriptPlayOptionParser.parse(reply).options.size)
    }
}