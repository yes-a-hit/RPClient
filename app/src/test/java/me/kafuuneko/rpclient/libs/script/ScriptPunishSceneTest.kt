package me.kafuuneko.rpclient.libs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 战败场景。
 *
 * 失败不该直接弹结束，而是先演一段惩罚，演完才收官。
 * 不同来源要能指向不同的惩罚场景——每个角色有自己的收场方式。
 */
class ScriptPunishSceneTest {

    private val engine = ScriptPlayEngine

    @Test
    fun `failed carries the punish scene id`() {
        val scene = ScriptScene(
            id = "night",
            name = "值班",
            failConditions = listOf(
                ScriptCondition(
                    type = ScriptConditionType.Variable,
                    variable = "电量",
                    threshold = 0,
                    description = "停电了",
                    failSceneId = "punish_bear"
                )
            )
        )

        val outcome = engine.evaluate(scene, 1, "", variables = mapOf("电量" to 0))

        assertTrue(outcome is ScriptPlayOutcome.Failed)
        val failed = outcome as ScriptPlayOutcome.Failed
        assertEquals("停电了", failed.reason)
        assertEquals("punish_bear", failed.gotoSceneId)
    }

    @Test
    fun `failed without punish scene id ends the run`() {
        val scene = ScriptScene(
            id = "night",
            name = "值班",
            failConditions = listOf(
                ScriptCondition(
                    type = ScriptConditionType.Keyword,
                    pattern = "放弃",
                    description = "你放弃了"
                )
            )
        )

        val outcome = engine.evaluate(scene, 1, "我放弃")

        assertTrue(outcome is ScriptPlayOutcome.Failed)
        assertEquals("", (outcome as ScriptPlayOutcome.Failed).gotoSceneId)
    }

    @Test
    fun `different failures lead to different punish scenes`() {
        val scene = ScriptScene(
            id = "night",
            name = "值班",
            failConditions = listOf(
                ScriptCondition(
                    type = ScriptConditionType.Keyword,
                    pattern = "断电",
                    failSceneId = "punish_bear"
                ),
                ScriptCondition(
                    type = ScriptConditionType.Keyword,
                    pattern = "厨房",
                    failSceneId = "punish_chicken"
                )
            )
        )

        val bear = engine.evaluate(scene, 1, "机器断电了")
        val chicken = engine.evaluate(scene, 1, "走进厨房")

        assertEquals(
            "punish_bear",
            (bear as ScriptPlayOutcome.Failed).gotoSceneId
        )
        assertEquals(
            "punish_chicken",
            (chicken as ScriptPlayOutcome.Failed).gotoSceneId
        )
    }

    @Test
    fun `punish scene finishes the run when it has no next scene`() {
        // 惩罚场景不接后继，达标即收官，正是「演完才 Game Over」
        val punish = ScriptScene(
            id = "punish_bear",
            name = "摇曲柄",
            nextSceneIds = emptyList(),
            completionCondition = ScriptCondition(
                type = ScriptConditionType.Variable,
                variable = "充能",
                threshold = 3,
                description = "熊爷满意了"
            )
        )
        val pack = ScriptPack(
            id = "p1",
            name = "夜班",
            scenes = listOf(punish),
            freeModeAfterClear = false
        )

        val outcome = engine.evaluate(punish, 1, "", variables = mapOf("充能" to 3))

        assertTrue(outcome is ScriptPlayOutcome.Cleared)
        // 允许自由模式时也得能正常收官
        val entersFreeMode = engine.shouldEnterFreeMode(pack, punish)
        assertFalse(entersFreeMode)
    }

    @Test
    fun `punish scene can extend the deadline with events`() {
        // 惩罚场景里玩家做事，靠事件累加变量，攒够才放人
        val punish = ScriptScene(
            id = "punish_chicken",
            name = "做披萨",
            events = listOf(
                ScriptEvent(
                    name = "又一张单子",
                    condition = ScriptCondition(
                        type = ScriptConditionType.Keyword,
                        pattern = "披萨"
                    ),
                    effects = listOf(ScriptEffect(variable = "进度", delta = 1)),
                    once = false
                )
            )
        )

        val event = engine.pickEvent(punish, 1, "我揉起面团做披萨")
        val next = engine.applyEffects(
            variables = mapOf("进度" to 1),
            effects = event!!.effects
        )

        assertEquals("又一张单子", event.name)
        assertEquals(2, next["进度"])
    }

    @Test
    fun `punish scene reachable only through failSceneId`() {
        val json = """
            {
              "formatVersion": 1,
              "id": "p1",
              "name": "夜班",
              "scenes": [
                {
                  "id": "night",
                  "name": "保安室",
                  "failConditions": [
                    {
                      "type": "Keyword",
                      "pattern": "断电",
                      "description": "停电了",
                      "failSceneId": "punish"
                    }
                  ]
                },
                {
                  "id": "punish",
                  "name": "摇曲柄",
                  "completionCondition": { "type": "Keyword", "pattern": "好了" }
                }
              ]
            }
        """.trimIndent()

        val parsed = ScriptPackCodec().decode(json)

        // 只靠 failSceneId 连接的场景不该被判成孤岛
        assertTrue("解析失败：$parsed", parsed is ScriptPackParseResult.Success)
        val condition = (parsed as ScriptPackParseResult.Success)
            .pack.scenes.first().failConditions.first()
        assertEquals("punish", condition.failSceneId)
    }

    @Test
    fun `demo night shift wires three punish scenes`() {
        // 示范剧本：三条失败路径各接一个惩罚场景，惩罚演完才收官
        val json = javaClass.classLoader!!.getResourceAsStream("demo-night-shift.json")!!
            .bufferedReader()
            .readText()

        val parsed = ScriptPackCodec().decode(json)

        assertTrue("解析失败：$parsed", parsed is ScriptPackParseResult.Success)
        val result = (parsed as ScriptPackParseResult.Success).pack
        val shift = result.scenes.first { it.id == "shift" }

        val punishIds = shift.failConditions.map { it.failSceneId }
        assertEquals(listOf("punish_bear", "punish_chicken", "punish_bunny"), punishIds)

        // 惩罚场景必须存在，且不接后继——演完即收官
        punishIds.forEach { id ->
            val scene = result.scenes.firstOrNull { it.id == id }
            assertTrue("缺少惩罚场景 $id", scene != null)
            assertTrue("惩罚场景不应接后继", scene!!.nextSceneIds.isEmpty())
            assertTrue("惩罚场景要有收尾条件", scene.completionCondition != null)
        }
    }

}
