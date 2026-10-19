package me.kafuuneko.rpclient.libs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 剧本运行时判定测试。
 *
 * 覆盖一条完整剧本会走到的四种结局：
 * 分支继续、失败、通关、通关后自由模式；
 * 以及条件判定的边界（空模式、非法正则、评分类条件）。
 */
class ScriptPlayEngineTest {
    /** 带分支、失败条件与通关条件的测试剧本。 */
    private val pack = ScriptPack(
        id = "engine-demo",
        name = "判定演示",
        mode = ScriptMode.Mainline,
        startSceneId = "start",
        freeModeAfterClear = true,
        scenes = listOf(
            ScriptScene(
                id = "start",
                name = "序章",
                nextSceneIds = listOf("branch-a", "branch-b"),
                failConditions = listOf(
                    ScriptCondition(ScriptConditionType.Keyword, pattern = "被杀", description = "你在码头遇害"),
                    ScriptCondition(ScriptConditionType.RoundLimit, threshold = 5, description = "天亮前没能脱身")
                )
            ),
            ScriptScene(
                id = "branch-a",
                name = "A 线",
                nextSceneIds = listOf("final"),
                completionCondition = ScriptCondition(
                    ScriptConditionType.Keyword,
                    pattern = "交出名单",
                    description = "A 线完成"
                )
            ),
            ScriptScene(
                id = "branch-b",
                name = "B 线",
                failConditions = listOf(
                    ScriptCondition(ScriptConditionType.Regex, pattern = "背叛.*船", description = "背叛了船队")
                ),
                completionCondition = ScriptCondition(
                    ScriptConditionType.Keyword,
                    pattern = "起锚",
                    description = "B 线完成"
                )
            ),
            ScriptScene(
                id = "final",
                name = "终章",
                completionCondition = ScriptCondition(
                    ScriptConditionType.Keyword,
                    pattern = "离开雾港",
                    description = "主线通关"
                )
            )
        )
    )

    @Test
    fun `normal round continues`() {
        val outcome = ScriptPlayEngine.evaluate(
            scene = pack.scenes[0],
            roundCount = 1,
            lastReply = "码头的灯忽明忽暗，远处有人在喊你的名字。"
        )

        assertTrue(outcome is ScriptPlayOutcome.Continue)
    }

    @Test
    fun `keyword triggers failure`() {
        val outcome = ScriptPlayEngine.evaluate(
            scene = pack.scenes[0],
            roundCount = 2,
            lastReply = "刀光一闪，你被杀了。"
        )

        assertTrue(outcome is ScriptPlayOutcome.Failed)
        assertEquals("你在码头遇害", (outcome as ScriptPlayOutcome.Failed).reason)
    }

    @Test
    fun `round limit triggers failure`() {
        val outcome = ScriptPlayEngine.evaluate(
            scene = pack.scenes[0],
            roundCount = 5,
            lastReply = "天亮了。"
        )

        assertTrue(outcome is ScriptPlayOutcome.Failed)
        assertEquals("天亮前没能脱身", (outcome as ScriptPlayOutcome.Failed).reason)
    }

    @Test
    fun `regex condition on branch scene`() {
        val branchB = pack.scenes[2]

        val failed = ScriptPlayEngine.evaluate(branchB, 1, "你背叛了船队。")
        val cleared = ScriptPlayEngine.evaluate(branchB, 2, "天亮时起锚离港。")

        assertTrue(failed is ScriptPlayOutcome.Failed)
        assertTrue(cleared is ScriptPlayOutcome.Cleared)
    }

    @Test
    fun `completion clears scene`() {
        val outcome = ScriptPlayEngine.evaluate(
            scene = pack.scenes[3],
            roundCount = 3,
            lastReply = "你终于离开雾港。"
        )

        assertTrue(outcome is ScriptPlayOutcome.Cleared)
        assertEquals("主线通关", (outcome as ScriptPlayOutcome.Cleared).reason)
    }

    @Test
    fun `failure wins over completion in same round`() {
        // 同一轮既含死亡台词又含通关台词时，判失败，避免带伤通关
        val scene = ScriptScene(
            id = "mixed",
            name = "混合场景",
            failConditions = listOf(ScriptCondition(ScriptConditionType.Keyword, pattern = "被杀")),
            completionCondition = ScriptCondition(ScriptConditionType.Keyword, pattern = "离开")
        )

        val outcome = ScriptPlayEngine.evaluate(scene, 1, "你杀了他，但自己也被杀了，随后离开。")

        assertTrue(outcome is ScriptPlayOutcome.Failed)
    }

    @Test
    fun `free mode disables failure`() {
        val outcome = ScriptPlayEngine.evaluate(
            scene = pack.scenes[0],
            roundCount = 9,
            lastReply = "你被杀了。",
            freeMode = true
        )

        // 自由模式下不再判失败，轮次上限也不再拦截
        assertTrue(outcome is ScriptPlayOutcome.Continue)
    }

    @Test
    fun `free mode after clear only for terminal scene`() {
        val cleared = ScriptPlayEngine.shouldEnterFreeMode(pack, pack.scenes[3])
        val midScene = ScriptPlayEngine.shouldEnterFreeMode(pack, pack.scenes[1])

        assertTrue(cleared)
        assertFalse(midScene)
    }

    @Test
    fun `reachable scenes follow next ids then fallback`() {
        val fromStart = ScriptPlayEngine.reachableScenes(pack, pack.scenes[0])
        val fromFinal = ScriptPlayEngine.reachableScenes(pack, pack.scenes[3])

        assertEquals(listOf("branch-a", "branch-b"), fromStart.map { it.id })
        // 末场景没有后继，退回其余场景，避免自由模式走不动
        assertEquals(3, fromFinal.size)
        assertFalse(fromFinal.any { it.id == "final" })
    }

    @Test
    fun `invalid regex does not crash`() {
        val condition = ScriptCondition(ScriptConditionType.Regex, pattern = "[unclosed")

        assertFalse(ScriptPlayEngine.matches(condition, 1, "任意文本"))
    }

    @Test
    fun `score condition never matches without scorer`() {
        val condition = ScriptCondition(ScriptConditionType.ScoreAtLeast, threshold = 1)

        assertFalse(ScriptPlayEngine.matches(condition, 1, "任意文本"))
    }
}
