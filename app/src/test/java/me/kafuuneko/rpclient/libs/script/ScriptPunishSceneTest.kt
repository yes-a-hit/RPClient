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


    @Test
    fun `demo three nights wires four dolls with distinct rules`() {
        // 复杂示范：三晚推进，四个玩偶各有各的克制手段，跳杀场景走超时转场
        val json = javaClass.classLoader!!.getResourceAsStream("demo-three-nights.json")!!
            .bufferedReader()
            .readText()

        val parsed = ScriptPackCodec().decode(json)

        assertTrue("解析失败：$parsed", parsed is ScriptPackParseResult.Success)
        val pack = (parsed as ScriptPackParseResult.Success).pack

        // 三晚加三个跳杀，加三个下班过场
        assertEquals(9, pack.scenes.size)

        // 每晚都有轮次上限与超时转场，否则跑完不会结束
        listOf("night1", "night2", "night3").forEach { id ->
            val night = pack.scenes.first { it.id == id }
            assertTrue("$id 缺轮次上限", night.maxRounds > 0)
            assertTrue("$id 缺超时转场", night.timeoutSceneId.isNotBlank())
            assertTrue("$id 的跳杀场景不存在",
                pack.scenes.any { it.id == night.timeoutSceneId })
        }

        // 位置用文本：0/1 表达不了「舞台 / 左走廊 / 通风管」这种多值状态
        val vars = pack.variables.associateBy { it.name }
        listOf("邦尼位置", "奇卡位置", "弗莱迪位置").forEach {
            assertTrue("缺少变量 $it", it in vars)
            assertEquals(ScriptVariableKind.Text, vars[it]?.kind)
        }
        // 霍斯不需要位置：他只有「在冲」与「不在冲」两态
        assertEquals(ScriptVariableKind.Switch, vars["霍斯冲刺"]?.kind)
        assertTrue("霍斯不该有位置变量", "霍斯位置" !in vars)
        // 右门被撞坏是二态，用开关；耐久另用数字
        assertEquals(ScriptVariableKind.Switch, vars["右门损坏"]?.kind)
        assertEquals(ScriptVariableKind.Number, vars["右门耐久"]?.kind)
    }


    @Test
    fun `text effects overwrite positions but append clues`() {
        // 位置是覆盖，线索是追加：写混了会把查到的线索冲掉
        val declared = listOf(
            ScriptVariable(name = "邦尼位置", kind = ScriptVariableKind.Text, text = "舞台"),
            ScriptVariable(name = "线索", kind = ScriptVariableKind.Note)
        )
        val start = mapOf("邦尼位置" to "舞台", "线索" to "")

        val moved = ScriptPlayEngine.applyTextEffects(
            variables = start,
            effects = listOf(ScriptEffect("邦尼位置", setText = "左走廊")),
            declared = declared
        )
        assertEquals("左走廊", moved["邦尼位置"])

        val first = ScriptPlayEngine.applyTextEffects(
            variables = moved,
            effects = listOf(ScriptEffect("线索", appendText = "厨房的刀不见了")),
            declared = declared
        )
        val second = ScriptPlayEngine.applyTextEffects(
            variables = first,
            effects = listOf(ScriptEffect("线索", appendText = "冰柜门缝有血")),
            declared = declared
        )

        assertTrue(second["线索"]!!.contains("厨房的刀不见了"))
        assertTrue(second["线索"]!!.contains("冰柜门缝有血"))
        assertEquals("左走廊", second["邦尼位置"])
    }

    @Test
    fun `same clue is not recorded twice`() {
        val declared = listOf(ScriptVariable(name = "线索", kind = ScriptVariableKind.Note))
        val once = ScriptPlayEngine.applyTextEffects(
            variables = mapOf("线索" to ""),
            effects = listOf(ScriptEffect("线索", appendText = "左门的锁是坏的")),
            declared = declared
        )
        val twice = ScriptPlayEngine.applyTextEffects(
            variables = once,
            effects = listOf(ScriptEffect("线索", appendText = "左门的锁是坏的")),
            declared = declared
        )

        assertEquals(once["线索"], twice["线索"])
    }


    @Test
    fun `foxy chain reacts to doors and screaming`() {
        // 霍斯的机制是一串联动：冲刺 → 撞门 → 门坏 → 别人也能进
        // 单独一个「位置」变量表达不了这套关系
        val json = javaClass.classLoader!!.getResourceAsStream("demo-three-nights.json")!!
            .bufferedReader()
            .readText()
        val pack = (ScriptPackCodec().decode(json) as ScriptPackParseResult.Success).pack
        val night3 = pack.scenes.first { it.id == "night3" }

        val names = night3.events.map { it.name }
        listOf("霍斯起冲", "霍斯撞门", "右门报废", "鬼叫招人", "手电反杀").forEach {
            assertTrue("缺少事件 $it", it in names)
        }

        // 门坏之后邦尼能长驱直入，这条依赖右门损坏这个开关
        assertTrue("缺少事件 门坏之后邦尼长驱直入", "门坏之后邦尼长驱直入" in names)
        val straightIn = night3.events.first { it.name == "门坏之后邦尼长驱直入" }
        assertEquals("右门损坏", straightIn.condition?.variable)

        // 手电是唯一能把突破次数压回去的手段
        val flashlight = night3.events.first { it.name == "手电反杀" }
        assertTrue(flashlight.effects.any {
            it.variable == "霍斯突破" && it.delta < 0
        })

        // 两条失败路径都要在：霍斯撞穿，或邦尼借门进来
        val failVars = night3.failConditions.mapNotNull { it.variable }.toSet()
        assertTrue("缺少霍斯突破的失败条件", "霍斯突破" in failVars)
        assertTrue("缺少邦尼压力的失败条件", "邦尼压力" in failVars)

        // 反杀场景必须能通关，否则被撞进来就是必死
        val foxyScene = pack.scenes.first { it.id == "jumpscare_foxy" }
        assertTrue("反杀场景要有通关条件", foxyScene.completionCondition != null)
    }

}
