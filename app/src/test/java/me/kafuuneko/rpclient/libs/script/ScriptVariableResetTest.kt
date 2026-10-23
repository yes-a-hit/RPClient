package me.kafuuneko.rpclient.libs.script

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 变量的重置策略。
 *
 * 默认重置，这一点不能变——现有剧本全都没写这个字段，
 * 一旦默认翻成「保留」，所有人的电量、夜数都会卡在上一轮。
 */
class ScriptVariableResetTest {

    private fun pack(vararg vars: ScriptVariable) =
        ScriptPack(id = "t", name = "t", variables = vars.toList())

    @Test
    fun `default resets to initial`() {
        val p = pack(ScriptVariable(name = "电量", initial = 100, min = 0, max = 100))
        val result = ScriptPlayEngine.initialVariables(p, previous = mapOf("电量" to 12))
        assertEquals(100, result["电量"])
    }

    @Test
    fun `opt out keeps previous value`() {
        // 跨周目保留：已扣过的电量不回来，由剧本自己决定
        val p = pack(
            ScriptVariable(
                name = "通关次数",
                initial = 0,
                resetOnRestart = false
            )
        )
        val result = ScriptPlayEngine.initialVariables(p, previous = mapOf("通关次数" to 3))
        assertEquals(3, result["通关次数"])
    }

    @Test
    fun `opt out without previous falls back to initial`() {
        // 第一次玩没有 previous，别把 null 塞进去
        val p = pack(ScriptVariable(name = "通关次数", initial = 0, resetOnRestart = false))
        val result = ScriptPlayEngine.initialVariables(p, previous = null)
        assertEquals(0, result["通关次数"])
    }

    @Test
    fun `kept value is still clamped`() {
        // 上一轮的值可能因为剧本改过而越界，沿用时要收敛
        val p = pack(
            ScriptVariable(
                name = "x",
                initial = 0,
                min = 0,
                max = 5,
                resetOnRestart = false
            )
        )
        val result = ScriptPlayEngine.initialVariables(p, previous = mapOf("x" to 99))
        assertEquals(5, result["x"])
    }

    @Test
    fun `mixed variables each follow own rule`() {
        val p = pack(
            ScriptVariable(name = "电量", initial = 100, min = 0, max = 100),
            ScriptVariable(name = "通关次数", initial = 0, resetOnRestart = false)
        )
        val result = ScriptPlayEngine.initialVariables(
            p,
            previous = mapOf("电量" to 12, "通关次数" to 3)
        )
        assertEquals(100, result["电量"])
        assertEquals(3, result["通关次数"])
    }
}
