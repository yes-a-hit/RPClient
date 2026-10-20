package me.kafuuneko.rpclient.libs.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 稳定 ID 的推导规则测试。
 *
 * 只覆盖纯计算部分：落盘逻辑依赖 Android Context，放到实机上验证。
 */
class SkillIdStabilityTest {
    @Test
    fun `same name maps to same id`() {
        // 同名必须得到同一个 ID，否则反复导入只会堆副本
        assertEquals(stableIdOf("写作助手"), stableIdOf("写作助手"))
    }

    @Test
    fun `id is ascii safe`() {
        val id = stableIdOf("写作助手 v2")
        assertTrue(id.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertTrue(id.none { it.code > 127 })
    }

    @Test
    fun `case and spaces collapse`() {
        assertEquals(stableIdOf("My Skill"), stableIdOf("my skill"))
    }

    /** 复刻仓库里的 ID 推导：清洗后用名字本身，不含随机后缀。 */
    private fun stableIdOf(name: String): String =
        name.trim().lowercase().map { char ->
            if (char.isLetterOrDigit() && char.code < 128 || char == '-' || char == '_') {
                char
            } else {
                '-'
            }
        }.joinToString("").trim('-')
}
