package me.kafuuneko.rpclient.libs.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** user 人设渲染：性别进来之后不能把「只填性别」的情形丢掉。 */
class UserPersonaRendererTest {

    @Test
    fun `gender only still renders`() {
        val out = renderUserPersonaTemplate(
            template = "",
            userName = "小明",
            userDescription = "",
            characterName = "阿强",
            userGender = "男"
        )
        assertTrue(out.isNotBlank())
        assertTrue(out.contains("男"))
    }

    @Test
    fun `nothing set renders empty`() {
        val out = renderUserPersonaTemplate(
            template = "",
            userName = "小明",
            userDescription = "",
            characterName = "阿强",
            userGender = ""
        )
        assertEquals("", out)
    }

    @Test
    fun `template placeholder receives gender too`() {
        val out = renderUserPersonaTemplate(
            template = "关于 {{user}}：{{persona}}",
            userName = "小明",
            userDescription = "喜欢猫",
            characterName = "阿强",
            userGender = "女"
        )
        assertTrue(out.contains("喜欢猫"))
        assertTrue(out.contains("女"))
        assertTrue(out.contains("小明"))
    }
}
