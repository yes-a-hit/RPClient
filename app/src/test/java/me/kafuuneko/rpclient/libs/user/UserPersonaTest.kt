package me.kafuuneko.rpclient.libs.user

import me.kafuuneko.rpclient.libs.defaults.DefaultNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * user 人设的继承规则。
 *
 * 规则本身很简单，但错法很隐蔽：多拾取一次全局，
 * 用户就会发现自己改的会话级人设「不生效」；少拾取一次，
 * 全局改了老会话不动。所以在纯函数这一层钉死。
 */
class UserPersonaTest {

    @Test
    fun `local value wins over global`() {
        assertEquals("小明", pickUserName("小明", "全局名"))
        assertEquals("本地的描述", pickUserDescription("本地的描述", "全局描述"))
    }

    @Test
    fun `blank local falls back to global`() {
        // 本处空着就该跟随全局，而不是留空
        assertEquals("全局名", pickUserName("", "全局名"))
        assertEquals("全局描述", pickUserDescription("", "全局描述"))
    }

    @Test
    fun `whitespace only local also falls back`() {
        assertEquals("全局名", pickUserName("   ", "全局名"))
        assertEquals("全局描述", pickUserDescription("  ", "全局描述"))
    }

    @Test
    fun `both blank uses default name and empty description`() {
        // 全局也空时不注入人设，名字退回默认称呼
        assertEquals(DefaultNames.USER, pickUserName("", ""))
        assertEquals("", pickUserDescription("", ""))
    }

    @Test
    fun `description is trimmed`() {
        assertEquals("全局描述", pickUserDescription("", "  全局描述  "))
        assertEquals("本地", pickUserDescription("  本地  ", "全局"))
    }

    @Test
    fun `hasLocalPersona detects override`() {
        assertFalse(hasLocalPersona("", ""))
        assertFalse(hasLocalPersona("   ", "  "))
        assertTrue(hasLocalPersona("小明", ""))
        assertTrue(hasLocalPersona("", "描述"))
    }
}
