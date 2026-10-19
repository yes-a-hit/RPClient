package me.kafuuneko.rpclient.libs.script

/** 一次对话回合的判定结果。 */
sealed class ScriptPlayOutcome {
    /** 继续当前场景。 */
    data object Continue : ScriptPlayOutcome()

    /** 判定失败，[reason] 为可展示的原因。 */
    data class Failed(val reason: String) : ScriptPlayOutcome()

    /** 判定通关，[reason] 为可展示的原因。 */
    data class Cleared(val reason: String) : ScriptPlayOutcome()
}

/**
 * 剧本运行时判定。
 *
 * 只做确定性判定，不交给模型解释：关键词与正则走文本匹配，
 * 轮次走计数。这样同一段对话在任何模型下都能复现相同的结局，
 * 检查点回滚才有意义。
 *
 * 判定优先级固定为「先失败后通关」：同时命中时判失败，
 * 避免玩家在死亡台词里顺手通关。
 */
object ScriptPlayEngine {
    /**
     * 判定本轮之后的状态。
     *
     * @param scene 当前场景
     * @param roundCount 本场景已完成的轮数（含本轮）
     * @param lastReply 本轮模型回复的正文
     * @param freeMode 是否已进入通关后的自由模式，自由模式下不再判定失败
     */
    fun evaluate(
        scene: ScriptScene,
        roundCount: Int,
        lastReply: String,
        freeMode: Boolean = false
    ): ScriptPlayOutcome {
        if (!freeMode) {
            scene.failConditions.firstOrNull { condition ->
                matches(condition, roundCount, lastReply)
            }?.let { condition ->
                return ScriptPlayOutcome.Failed(
                    condition.description.ifBlank { "触发了失败条件" }
                )
            }
        }
        scene.completionCondition?.let { condition ->
            if (matches(condition, roundCount, lastReply)) {
                return ScriptPlayOutcome.Cleared(
                    condition.description.ifBlank { "达成了通关条件" }
                )
            }
        }
        return ScriptPlayOutcome.Continue
    }

    /**
     * 判定单个条件是否命中。
     *
     * [ScriptConditionType.ScoreAtLeast] 需要外部评分源，当前没有评分链路，
     * 因此按不命中处理，避免误判通关。
     */
    fun matches(condition: ScriptCondition, roundCount: Int, lastReply: String): Boolean =
        when (condition.type) {
            ScriptConditionType.Keyword ->
                condition.pattern.isNotBlank() && lastReply.contains(condition.pattern)

            ScriptConditionType.Regex ->
                condition.pattern.isNotBlank() && runCatching {
                    Regex(condition.pattern).containsMatchIn(lastReply)
                }.getOrDefault(false)

            ScriptConditionType.RoundLimit ->
                condition.threshold > 0 && roundCount >= condition.threshold

            ScriptConditionType.ScoreAtLeast -> false
        }

    /**
     * 通关后是否进入自由模式。
     *
     * 剧本显式开启时进入；末场景没有后继时也允许自由续写，
     * 否则玩家通关后会卡在一个没有任何出口的场景里。
     */
    fun shouldEnterFreeMode(pack: ScriptPack, scene: ScriptScene): Boolean =
        pack.freeModeAfterClear && (scene.nextSceneIds.isEmpty() || pack.mode == ScriptMode.Free)

    /**
     * 玩家可切换的场景。
     *
     * 主线场景的后继优先；没有后继时退回全部场景，保证自由模式下仍能走动。
     */
    fun reachableScenes(pack: ScriptPack, scene: ScriptScene): List<ScriptScene> {
        if (scene.nextSceneIds.isNotEmpty()) {
            return scene.nextSceneIds.mapNotNull { id -> pack.scenes.firstOrNull { it.id == id } }
        }
        return pack.scenes.filterNot { it.id == scene.id }
    }
}
