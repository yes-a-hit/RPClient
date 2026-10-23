package me.kafuuneko.rpclient.libs.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 思维链与正文的隔离。
 *
 * 这两者拼在同一条消息里存着（`<think>…</think>` 加正文），
 * 渲染时会拆开，但编辑走的是数据库原文——不剥就等于让用户在推理过程里改字。
 */
class ThinkBlockTest {

    private val withThink = "<think>\n先看左门，再判断电量\n</think>\n\n他把门推开了。"

    @Test
    fun `detects think block`() {
        assertTrue(hasThinkBlock(withThink))
        assertFalse(hasThinkBlock("他把门推开了。"))
        assertFalse(hasThinkBlock("<think>没有闭合"))
    }

    @Test
    fun `extracts reasoning only`() {
        assertEquals("先看左门，再判断电量", extractThinkBlock(withThink))
    }

    @Test
    fun `strips reasoning leaving body`() {
        // 编辑框里只该看到这句
        assertEquals("他把门推开了。", stripThinkBlock(withThink))
    }

    @Test
    fun `strip keeps plain content untouched`() {
        assertEquals("他把门推开了。", stripThinkBlock("他把门推开了。"))
    }

    @Test
    fun `strip handles several blocks`() {
        // 有的模型会分多次输出思考段，全都要剥掉
        val multi = "<think>一</think>\n正文甲\n<think>二</think>\n正文乙"
        val stripped = stripThinkBlock(multi)
        assertFalse(stripped.contains("<think>"))
        assertTrue(stripped.contains("正文甲"))
        assertTrue(stripped.contains("正文乙"))
    }

    @Test
    fun `replace keeps original reasoning`() {
        // 用户改的是正文，推理过程不该被覆盖，也不该丢
        val replaced = replaceContentKeepingThink(withThink, "他退后一步。")
        assertEquals("先看左门，再判断电量", extractThinkBlock(replaced))
        assertEquals("他退后一步。", stripThinkBlock(replaced))
    }

    @Test
    fun `replace on plain message adds no think block`() {
        val plain = "他把门推开了。"
        val replaced = replaceContentKeepingThink(plain, "他退后一步。")
        assertFalse(hasThinkBlock(replaced))
        assertEquals("他退后一步。", replaced)
    }

    @Test
    fun `unclosed think tag is stripped entirely`() {
        // 被打断的思考：开标记之后全是没写完的推理，正文还没开始。
        // 第一版对这种「放行」，结果是半截推理永远赖在消息里，删不掉也改不了
        val broken = "<think>没闭合的推理"
        assertFalse(hasThinkBlock(broken))
        assertEquals("", stripThinkBlock(broken))
    }

    @Test
    fun `unclosed block after real content keeps the content`() {
        // 正文在前、思考被打断在后：正文要留住
        val mixed = "他说完了。\n<think>又想起一件事"
        assertEquals("他说完了。", stripThinkBlock(mixed))
    }

    @Test
    fun `compose writes both parts`() {
        val out = composeThinkContent("想了想", "说了一句")
        assertEquals("想了想", extractThinkBlock(out))
        assertEquals("说了一句", stripThinkBlock(out))
    }

    @Test
    fun `compose without think yields plain body`() {
        // 用户清空了思维链，就该真的没有思维链，而不是留个空标签
        val out = composeThinkContent("", "说了一句")
        assertFalse(hasThinkBlock(out))
        assertEquals("说了一句", out)
    }

    @Test
    fun `compose without body keeps think`() {
        // 正文清空时推理还在，不能把整条消息变成空
        val out = composeThinkContent("想了半天", "")
        assertEquals("想了半天", extractThinkBlock(out))
        assertEquals("", stripThinkBlock(out))
    }

    @Test
    fun `compose trims both sides`() {
        val out = composeThinkContent("  推理  ", "  正文  ")
        assertEquals("推理", extractThinkBlock(out))
        assertEquals("正文", stripThinkBlock(out))
    }

    @Test
    fun `edit round trip keeps edited think`() {
        // 打开带思维链的消息、改掉推理、保存，落库的应该是新推理
        val original = "<think>\n旧的推理\n</think>\n\n正文"
        val saved = composeThinkContent("新的推理", stripThinkBlock(original))
        assertEquals("新的推理", extractThinkBlock(saved))
        assertEquals("正文", stripThinkBlock(saved))
    }

}
