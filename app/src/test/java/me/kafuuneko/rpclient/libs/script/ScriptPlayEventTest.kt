package me.kafuuneko.rpclient.libs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 场景事件判定。
 *
 * 事件影响在场名单与转场，判错会把玩家莫名其妙赶走，
 * 所以条件、轮次、只触发一次这三种规则都要单独钉住。
 */
class ScriptPlayEventTest {

    private fun scene(vararg events: ScriptEvent) = ScriptScene(
        id = "s1",
        name = "测试场景",
        cast = listOf(ScriptCastMember("甲"), ScriptCastMember("乙")),
        events = events.toList()
    )

    @Test
    fun `no condition and no round fires immediately`() {
        val target = scene(ScriptEvent(name = "开场"))

        val hit = ScriptPlayEngine.pickEvent(target, roundCount = 0, lastReply = "")

        assertNotNull(hit)
        assertEquals("开场", hit?.name)
    }

    @Test
    fun `keyword condition waits for the reply`() {
        val target = scene(
            ScriptEvent(
                name = "提及",
                condition = ScriptCondition(ScriptConditionType.Keyword, pattern = "钥匙")
            )
        )

        assertNull(ScriptPlayEngine.pickEvent(target, roundCount = 1, lastReply = "什么也没发生"))
        assertNotNull(ScriptPlayEngine.pickEvent(target, roundCount = 1, lastReply = "你摸到了钥匙"))
    }

    @Test
    fun `round trigger fires at or after the threshold`() {
        val target = scene(ScriptEvent(name = "定时", atRound = 3))

        assertNull(ScriptPlayEngine.pickEvent(target, roundCount = 2, lastReply = ""))
        assertNotNull(ScriptPlayEngine.pickEvent(target, roundCount = 3, lastReply = ""))
        assertNotNull(ScriptPlayEngine.pickEvent(target, roundCount = 9, lastReply = ""))
    }

    @Test
    fun `once event does not fire twice`() {
        val target = scene(ScriptEvent(name = "只一次", once = true))
        val key = ScriptPlayEngine.eventKey(target, target.events.first())

        assertNotNull(ScriptPlayEngine.pickEvent(target, 0, "", firedEventKeys = emptySet()))
        assertNull(ScriptPlayEngine.pickEvent(target, 0, "", firedEventKeys = setOf(key)))
    }

    @Test
    fun `repeatable event fires again`() {
        val target = scene(ScriptEvent(name = "可重复", once = false))
        val key = ScriptPlayEngine.eventKey(target, target.events.first())

        assertNotNull(ScriptPlayEngine.pickEvent(target, 0, "", firedEventKeys = setOf(key)))
    }

    @Test
    fun `only one event fires per round`() {
        val target = scene(
            ScriptEvent(name = "第一个"),
            ScriptEvent(name = "第二个")
        )

        val hit = ScriptPlayEngine.pickEvent(target, 0, "")

        assertEquals("第一个", hit?.name)
    }

    @Test
    fun `event key falls back to scene and index`() {
        val target = scene(
            ScriptEvent(name = "无 id 的甲"),
            ScriptEvent(name = "无 id 的乙")
        )

        assertEquals("s1#0", ScriptPlayEngine.eventKey(target, target.events[0]))
        assertEquals("s1#1", ScriptPlayEngine.eventKey(target, target.events[1]))
    }

    @Test
    fun `cast removal can empty the scene`() {
        val target = scene()
        val event = ScriptEvent(removeCast = listOf("甲", "乙"))

        val remaining = ScriptPlayEngine.applyEventCast(listOf("甲", "乙"), event)

        // 零角色是合法状态：这一场只剩旁白
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun `cast add and remove combine`() {
        val event = ScriptEvent(addCast = listOf("丙"), removeCast = listOf("甲"))

        val result = ScriptPlayEngine.applyEventCast(listOf("甲", "乙"), event)

        assertEquals(listOf("乙", "丙"), result)
    }

    @Test
    fun `removal wins when same name appears in both`() {
        // 同时写进增删时按「让他退下」理解
        val event = ScriptEvent(addCast = listOf("甲"), removeCast = listOf("甲"))

        val result = ScriptPlayEngine.applyEventCast(listOf("甲", "乙"), event)

        assertEquals(listOf("乙"), result)
        assertFalse(result.contains("甲"))
    }

    @Test
    fun `json round trip keeps events`() {
        val json = """
            {
              "formatVersion": 1,
              "id": "p1",
              "name": "事件剧本",
              "scenes": [
                {
                  "id": "s1",
                  "name": "开场",
                  "events": [
                    {
                      "name": "清场",
                      "atRound": 2,
                      "removeCast": ["甲"],
                      "gotoSceneId": "s2",
                      "narration": "人都走了"
                    }
                  ]
                },
                { "id": "s2", "name": "空场" }
              ]
            }
        """.trimIndent()

        val parsed = ScriptPackCodec().decode(json)

        assertTrue(parsed is ScriptPackParseResult.Success)
        val event = (parsed as ScriptPackParseResult.Success).pack.scenes.first().events.single()
        assertEquals("清场", event.name)
        assertEquals(2, event.atRound)
        assertEquals(listOf("甲"), event.removeCast)
        assertEquals("s2", event.gotoSceneId)
        assertEquals("人都走了", event.narration)
        assertTrue(event.once)
    }

    @Test
    fun `round limit ends the scene even without a fail condition`() {
        // 之前 maxRounds 只是摆设，跑完什么都不发生，玩家能无限聊
        val scene = ScriptScene(
            id = "s1",
            name = "值班室",
            maxRounds = 3,
            failText = "天亮了，你还没撑住"
        )

        assertEquals(ScriptPlayOutcome.Continue, ScriptPlayEngine.evaluate(scene, 2, ""))
        val outcome = ScriptPlayEngine.evaluate(scene, 3, "")
        assertTrue(outcome is ScriptPlayOutcome.Failed)
        assertEquals("天亮了，你还没撑住", (outcome as ScriptPlayOutcome.Failed).reason)
    }

    @Test
    fun `round limit can hand over to a designated scene`() {
        val scene = ScriptScene(
            id = "s1",
            name = "值班室",
            maxRounds = 2,
            timeoutSceneId = "punish_bear"
        )

        val outcome = ScriptPlayEngine.evaluate(scene, 5, "")

        assertEquals("punish_bear", (outcome as ScriptPlayOutcome.Failed).gotoSceneId)
    }

    @Test
    fun `completion wins over round limit`() {
        // 同一轮既达标又到上限时按通关算，别把成功写成失败
        val scene = ScriptScene(
            id = "s1",
            name = "值班室",
            maxRounds = 3,
            completionCondition = ScriptCondition(
                type = ScriptConditionType.Keyword,
                pattern = "下班"
            )
        )

        val outcome = ScriptPlayEngine.evaluate(scene, 3, "我下班了")

        assertTrue(outcome is ScriptPlayOutcome.Cleared)
    }

    @Test
    fun `zero round limit means unlimited`() {
        val scene = ScriptScene(id = "s1", name = "自由场", maxRounds = 0)

        assertEquals(ScriptPlayOutcome.Continue, ScriptPlayEngine.evaluate(scene, 99, ""))
    }


    @Test
    fun `keyword accepts several alternatives`() {
        // 玩偶机制靠关键词触发，只认一个死词的话玩家换个说法事件就哑了
        val condition = ScriptCondition(
            type = ScriptConditionType.Keyword,
            pattern = "关门|关上|门合"
        )

        assertTrue(ScriptPlayEngine.matches(condition, 0, "你把门关上了"))
        assertTrue(ScriptPlayEngine.matches(condition, 0, "门合拢"))
        assertTrue(ScriptPlayEngine.matches(condition, 0, "关门"))
        assertFalse(ScriptPlayEngine.matches(condition, 0, "你盯着走廊"))
    }

    @Test
    fun `single keyword still works`() {
        val condition = ScriptCondition(
            type = ScriptConditionType.Keyword,
            pattern = "走廊"
        )

        assertTrue(ScriptPlayEngine.matches(condition, 0, "你盯着走廊"))
        assertFalse(ScriptPlayEngine.matches(condition, 0, "你看着厨房"))
    }

    @Test
    fun `blank alternatives are ignored`() {
        // 「关门||关上」这种写法不能因为中间空了一段就把什么都匹配上
        val condition = ScriptCondition(
            type = ScriptConditionType.Keyword,
            pattern = "关门||关上"
        )

        assertFalse(ScriptPlayEngine.matches(condition, 0, "你盯着走廊"))
        assertTrue(ScriptPlayEngine.matches(condition, 0, "你关上门"))
    }

}
