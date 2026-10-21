package me.kafuuneko.rpclient.libs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 条件到场。
 *
 * 同一场景在不同状态下遇到不同的人，剧本才有「世界在动」的感觉，
 * 也才不必为每种组合抄一份场景。
 */
class ScriptConditionalCastTest {

    private val engine = ScriptPlayEngine

    private fun scene(
        cast: List<ScriptCastMember> = emptyList(),
        conditional: List<ScriptCastRule> = emptyList(),
        banned: List<String> = emptyList()
    ) = ScriptScene(
        id = "s1",
        name = "大堂",
        cast = cast,
        conditionalCast = conditional,
        bannedCast = banned
    )

    @Test
    fun `resident cast always shows up`() {
        val target = scene(cast = listOf(ScriptCastMember("老陈")))

        assertEquals(listOf("老陈"), engine.effectiveCast(target))
    }

    @Test
    fun `conditional guest appears when threshold is met`() {
        val target = scene(
            cast = listOf(ScriptCastMember("老陈")),
            conditional = listOf(
                ScriptCastRule(
                    name = "苏眠",
                    condition = ScriptCondition(
                        type = ScriptConditionType.Variable,
                        variable = "信任",
                        threshold = 3
                    )
                )
            )
        )

        val early = engine.effectiveCast(target, mapOf("信任" to 2))
        val late = engine.effectiveCast(target, mapOf("信任" to 3))

        assertEquals(listOf("老陈"), early)
        assertEquals(listOf("老陈", "苏眠"), late)
    }

    @Test
    fun `rule without condition always arrives`() {
        val target = scene(conditional = listOf(ScriptCastRule(name = "临时访客")))

        assertTrue(engine.effectiveCast(target).contains("临时访客"))
    }

    @Test
    fun `banned cast wins over conditional arrival`() {
        val target = scene(
            conditional = listOf(
                ScriptCastRule(
                    name = "苏眠",
                    condition = ScriptCondition(
                        type = ScriptConditionType.Variable,
                        variable = "信任",
                        threshold = 1
                    )
                )
            ),
            banned = listOf("苏眠")
        )

        assertTrue(engine.effectiveCast(target, mapOf("信任" to 9)).isEmpty())
    }

    @Test
    fun `duplicates collapse`() {
        val target = scene(
            cast = listOf(ScriptCastMember("老陈")),
            conditional = listOf(ScriptCastRule(name = "老陈"))
        )

        assertEquals(listOf("老陈"), engine.effectiveCast(target))
    }

    @Test
    fun `scene can end up with nobody`() {
        // 常驻角色都禁止后就是零角色场景，只剩旁白
        val target = scene(
            cast = listOf(ScriptCastMember("老陈")),
            banned = listOf("老陈")
        )

        assertTrue(engine.effectiveCast(target).isEmpty())
    }

    @Test
    fun `keyword condition can gate an arrival`() {
        val target = scene(
            conditional = listOf(
                ScriptCastRule(
                    name = "警官",
                    condition = ScriptCondition(
                        type = ScriptConditionType.Keyword,
                        pattern = "报警"
                    )
                )
            )
        )

        assertTrue(engine.effectiveCast(target, lastReply = "我掏出手机").isEmpty())
        assertEquals(listOf("警官"), engine.effectiveCast(target, lastReply = "我决定报警"))
    }

    @Test
    fun `json keeps conditional cast`() {
        val json = """
            {
              "formatVersion": 1,
              "id": "p1",
              "name": "公寓",
              "variables": [ { "name": "信任", "initial": 0 } ],
              "scenes": [
                {
                  "id": "hall",
                  "name": "走廊",
                  "cast": [ { "name": "老陈" } ],
                  "conditionalCast": [
                    {
                      "name": "苏眠",
                      "description": "怕生",
                      "condition": {
                        "type": "Variable",
                        "variable": "信任",
                        "threshold": 3
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = ScriptPackCodec().decode(json)

        assertTrue("解析失败：$parsed", parsed is ScriptPackParseResult.Success)
        val scene = (parsed as ScriptPackParseResult.Success).pack.scenes.single()
        val rule = scene.conditionalCast.single()

        assertEquals("苏眠", rule.name)
        assertEquals("怕生", rule.description)
        assertEquals("信任", rule.condition?.variable)
        assertEquals(3, rule.condition?.threshold)
    }
}
