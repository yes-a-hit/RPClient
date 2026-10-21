package me.kafuuneko.rpclient.libs.script

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 变量与随机。
 *
 * 这两样决定剧本能不能从线性变开放：
 * 变量让玩家行为有累积后果，随机让同一场每次不同。
 * 但随机必须可复现，否则出了问题没法重现。
 */
class ScriptVariableTest {

    private val engine = ScriptPlayEngine

    private fun pack(
        variables: List<ScriptVariable> = emptyList(),
        scenes: List<ScriptScene> = emptyList()
    ) = ScriptPack(
        id = "p1",
        name = "变量剧本",
        variables = variables,
        scenes = scenes
    )

    @Test
    fun `initial variables come from declared values`() {
        val target = pack(
            variables = listOf(
                ScriptVariable(name = "好感", initial = 3),
                ScriptVariable(name = "警戒", initial = 0)
            )
        )

        val vars = engine.initialVariables(target)

        assertEquals(3, vars["好感"])
        assertEquals(0, vars["警戒"])
    }

    @Test
    fun `initial value is clamped into range`() {
        val target = pack(
            variables = listOf(ScriptVariable(name = "电量", initial = 500, min = 0, max = 100))
        )

        assertEquals(100, engine.initialVariables(target)["电量"])
    }

    @Test
    fun `effect adds delta`() {
        val next = engine.applyEffects(
            variables = mapOf("好感" to 3),
            effects = listOf(ScriptEffect(variable = "好感", delta = 2))
        )

        assertEquals(5, next["好感"])
    }

    @Test
    fun `effect setTo wins over delta`() {
        val next = engine.applyEffects(
            variables = mapOf("好感" to 3),
            effects = listOf(ScriptEffect(variable = "好感", delta = 99, setTo = 10))
        )

        assertEquals(10, next["好感"])
    }

    @Test
    fun `effect is clamped by declared bounds`() {
        val declared = listOf(ScriptVariable(name = "电量", min = 0, max = 100))

        val high = engine.applyEffects(mapOf("电量" to 90), listOf(ScriptEffect("电量", delta = 50)), declared)
        val low = engine.applyEffects(mapOf("电量" to 10), listOf(ScriptEffect("电量", delta = -50)), declared)

        assertEquals(100, high["电量"])
        assertEquals(0, low["电量"])
    }

    @Test
    fun `variable condition compares against threshold`() {
        val condition = ScriptCondition(
            type = ScriptConditionType.Variable,
            variable = "好感",
            threshold = 5
        )

        assertFalse(engine.matches(condition, 0, "", mapOf("好感" to 4)))
        assertTrue(engine.matches(condition, 0, "", mapOf("好感" to 5)))
        assertTrue(engine.matches(condition, 0, "", mapOf("好感" to 9)))
    }

    @Test
    fun `missing variable counts as zero`() {
        val condition = ScriptCondition(
            type = ScriptConditionType.Variable,
            variable = "未知",
            threshold = 1
        )

        assertFalse(engine.matches(condition, 0, "", emptyMap()))
    }

    @Test
    fun `failure can be driven by variable`() {
        val scene = ScriptScene(
            id = "s1",
            name = "试探",
            failConditions = listOf(
                ScriptCondition(
                    type = ScriptConditionType.Variable,
                    variable = "警戒",
                    threshold = 3,
                    description = "她起了疑心"
                )
            )
        )

        val calm = engine.evaluate(scene, 1, "", variables = mapOf("警戒" to 1))
        val alert = engine.evaluate(scene, 1, "", variables = mapOf("警戒" to 3))

        assertEquals(ScriptPlayOutcome.Continue, calm)
        assertTrue(alert is ScriptPlayOutcome.Failed)
    }

    @Test
    fun `cleared can be driven by variable`() {
        val scene = ScriptScene(
            id = "s1",
            name = "交心",
            completionCondition = ScriptCondition(
                type = ScriptConditionType.Variable,
                variable = "好感",
                threshold = 8,
                description = "她终于信你"
            )
        )

        val outcome = engine.evaluate(scene, 1, "", variables = mapOf("好感" to 8))

        assertTrue(outcome is ScriptPlayOutcome.Cleared)
    }

    @Test
    fun `unlock condition gates free roaming`() {
        val locked = ScriptScene(
            id = "roof",
            name = "天台",
            unlockCondition = ScriptCondition(
                type = ScriptConditionType.Variable,
                variable = "好感",
                threshold = 5
            )
        )

        assertFalse(engine.isUnlocked(locked, mapOf("好感" to 4)))
        assertTrue(engine.isUnlocked(locked, mapOf("好感" to 5)))
    }

    @Test
    fun `scene without unlock condition is always open`() {
        assertTrue(engine.isUnlocked(ScriptScene(id = "s1", name = "大堂"), emptyMap()))
    }

    @Test
    fun `reachable scenes respect unlock in free roaming`() {
        val hall = ScriptScene(id = "hall", name = "大堂")
        val roof = ScriptScene(
            id = "roof",
            name = "天台",
            unlockCondition = ScriptCondition(
                type = ScriptConditionType.Variable,
                variable = "好感",
                threshold = 5
            )
        )
        val target = pack(scenes = listOf(hall, roof))

        val lockedIds = engine.reachableScenes(target, hall, mapOf("好感" to 0)).map { it.id }
        val openIds = engine.reachableScenes(target, hall, mapOf("好感" to 5)).map { it.id }

        // 好感不够时天台是关着的，够了才出现在可选列表里
        assertFalse(lockedIds.contains("roof"))
        assertTrue(openIds.contains("roof"))
    }

    @Test
    fun `chance of zero never fires and hundred always fires`() {
        val never = ScriptEvent(name = "必不发", chance = 0)
        val always = ScriptEvent(name = "必发", chance = 100)
        val scene = ScriptScene(id = "s1", name = "场景", events = listOf(never, always))

        // 概率 0 被拦下，轮到概率 100 的那条
        val hit = engine.pickEvent(scene, 0, "", random = Random(1))
        assertEquals("必发", hit?.name)

        assertFalse(engine.rollChance(0, Random(7)))
        assertTrue(engine.rollChance(100, Random(7)))
    }

    @Test
    fun `same seed reproduces the same rolls`() {
        val rolls1 = List(20) { engine.rollChance(50, Random(42)) }
        val rolls2 = List(20) { engine.rollChance(50, Random(42)) }

        assertEquals(rolls1, rolls2)
    }

    @Test
    fun `event effects change variables through json`() {
        val json = """
            {
              "formatVersion": 1,
              "id": "p1",
              "name": "变量剧本",
              "variables": [
                { "name": "好感", "initial": 2, "min": 0, "max": 10 }
              ],
              "scenes": [
                {
                  "id": "s1",
                  "name": "初遇",
                  "events": [
                    { "name": "她笑了", "effects": [ { "variable": "好感", "delta": 3 } ] }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = ScriptPackCodec().decode(json)

        assertTrue(parsed is ScriptPackParseResult.Success)
        val result = (parsed as ScriptPackParseResult.Success).pack
        assertEquals(2, result.variables.single().initial)
        val effect = result.scenes.single().events.single().effects.single()
        assertEquals("好感", effect.variable)
        assertEquals(3, effect.delta)
    }

    @Test
    fun `demo open house is a valid free roaming pack`() {
        // 开放剧本示范：变量声明齐全，条件引用的变量都真实存在
        val json = javaClass.classLoader!!.getResourceAsStream("demo-open-house.json")!!
            .bufferedReader()
            .readText()

        val parsed = ScriptPackCodec().decode(json)

        assertTrue("解析失败：$parsed", parsed is ScriptPackParseResult.Success)
        val result = (parsed as ScriptPackParseResult.Success).pack
        assertEquals(ScriptMode.Free, result.mode)
        assertTrue(result.variables.isNotEmpty())

        val declared = result.variables.map { it.name }.toSet()
        // 解锁条件里写的变量必须声明过，否则门永远开不了
        result.scenes.mapNotNull { it.unlockCondition }
            .filter { it.type == ScriptConditionType.Variable }
            .forEach { assertTrue(it.variable in declared) }
    }


    @Test
    fun `old save without new fields loads without crashing`() {
        // 旧存档没有 firedEvents 与 variables，Gson 会塞 null 进来；
        // 读取时补默认值，否则第一次 copy 就 NPE
        val json = """
            {
              "id": "old-1",
              "packId": "p1",
              "packName": "旧局",
              "sceneId": "s1",
              "roundCount": 3,
              "finished": false
            }
        """.trimIndent()

        val play = com.google.gson.Gson().fromJson(json, ScriptPlaythrough::class.java)

        // 直接反序列化时缺字段会变 null，连 endReason 这种非空声明也逃不掉，
        // 这正是存档读取改走显式字段解析的原因
        assertEquals(null, play.firedEvents)
        assertEquals(null, play.variables)
        assertEquals(null, play.endReason)
        assertTrue(play.firedEvents.orEmpty().isEmpty())
        assertTrue(play.variables.orEmpty().isEmpty())
    }


    @Test
    fun `AtMost fires when the value drops to the threshold`() {
        // 电量归零是「掉到 0」，不是「至少 0」——写错会一开局就判死
        val condition = ScriptCondition(
            type = ScriptConditionType.Variable,
            variable = "电量",
            threshold = 0,
            comparison = ScriptComparison.AtMost
        )

        assertFalse(ScriptPlayEngine.matches(condition, 0, "", mapOf("电量" to 100)))
        assertFalse(ScriptPlayEngine.matches(condition, 0, "", mapOf("电量" to 1)))
        assertTrue(ScriptPlayEngine.matches(condition, 0, "", mapOf("电量" to 0)))
    }

    @Test
    fun `AtLeast is the default direction`() {
        val condition = ScriptCondition(
            type = ScriptConditionType.Variable,
            variable = "信任",
            threshold = 3
        )

        assertEquals(ScriptComparison.AtLeast, condition.comparison)
        assertFalse(ScriptPlayEngine.matches(condition, 0, "", mapOf("信任" to 2)))
        assertTrue(ScriptPlayEngine.matches(condition, 0, "", mapOf("信任" to 3)))
    }

    @Test
    fun `Equal matches exactly`() {
        val condition = ScriptCondition(
            type = ScriptConditionType.Variable,
            variable = "阶段",
            threshold = 2,
            comparison = ScriptComparison.Equal
        )

        assertFalse(ScriptPlayEngine.matches(condition, 0, "", mapOf("阶段" to 1)))
        assertTrue(ScriptPlayEngine.matches(condition, 0, "", mapOf("阶段" to 2)))
        assertFalse(ScriptPlayEngine.matches(condition, 0, "", mapOf("阶段" to 3)))
    }

    @Test
    fun `json keeps comparison direction`() {
        val json = """
            {
              "formatVersion": 1,
              "id": "p1",
              "name": "夜班",
              "variables": [ { "name": "电量", "initial": 100 } ],
              "scenes": [
                {
                  "id": "s1",
                  "name": "值班室",
                  "failConditions": [
                    {
                      "type": "Variable",
                      "variable": "电量",
                      "threshold": 0,
                      "comparison": "AtMost"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = ScriptPackCodec().decode(json)

        assertTrue("解析失败：$parsed", parsed is ScriptPackParseResult.Success)
        val condition = (parsed as ScriptPackParseResult.Success)
            .pack.scenes.single().failConditions.single()
        assertEquals(ScriptComparison.AtMost, condition.comparison)
    }

}
