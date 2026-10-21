package me.kafuuneko.rpclient.libs.script

import kotlin.random.Random

/** 一次对话回合的判定结果。 */
sealed class ScriptPlayOutcome {
    /** 继续当前场景。 */
    data object Continue : ScriptPlayOutcome()

    /** 判定失败，[reason] 为可展示的原因。 */
    data class Failed(
        val reason: String,
        /**
         * 战败后要转到的场景。
         *
         * 留空就照常结束整局；填了先演惩罚场景。
         */
        val gotoSceneId: String = ""
    ) : ScriptPlayOutcome()

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
        freeMode: Boolean = false,
        variables: Map<String, Int> = emptyMap()
    ): ScriptPlayOutcome {
        if (!freeMode) {
            scene.failConditions.firstOrNull { condition ->
                matches(condition, roundCount, lastReply, variables)
            }?.let { condition ->
                return ScriptPlayOutcome.Failed(
                    reason = condition.description.ifBlank { "触发了失败条件" },
                    gotoSceneId = condition.failSceneId
                )
            }
        }
        scene.completionCondition?.let { condition ->
            if (matches(condition, roundCount, lastReply, variables)) {
                return ScriptPlayOutcome.Cleared(
                    condition.description.ifBlank { "达成了通关条件" }
                )
            }
        }
        // 轮次用尽也算结局。之前只认作者手写的失败条件，
        // 场景设了 maxRounds 却什么都不会发生，玩家能无限聊下去。
        if (!freeMode && scene.maxRounds > 0 && roundCount >= scene.maxRounds) {
            return ScriptPlayOutcome.Failed(
                reason = scene.failText.ifBlank { "时间到了，这一场没有结果" },
                gotoSceneId = scene.timeoutSceneId
            )
        }
        return ScriptPlayOutcome.Continue
    }

    /**
     * 判定单个条件是否命中。
     *
     * [ScriptConditionType.ScoreAtLeast] 需要外部评分源，当前没有评分链路，
     * 因此按不命中处理，避免误判通关。
     *
     * @param variables 当前变量快照，[ScriptConditionType.Variable] 用
     */
    fun matches(
        condition: ScriptCondition,
        roundCount: Int,
        lastReply: String,
        variables: Map<String, Int> = emptyMap()
    ): Boolean =
        when (condition.type) {
            ScriptConditionType.Keyword ->
                condition.pattern.isNotBlank() && lastReply.contains(condition.pattern)

            ScriptConditionType.Regex ->
                condition.pattern.isNotBlank() && runCatching {
                    Regex(condition.pattern).containsMatchIn(lastReply)
                }.getOrDefault(false)

            ScriptConditionType.RoundLimit ->
                condition.threshold > 0 && roundCount >= condition.threshold

            ScriptConditionType.Variable -> {
                val name = condition.variable
                if (name.isBlank()) {
                    false
                } else {
                    val value = variables[name] ?: 0
                    when (condition.comparison) {
                        ScriptComparison.AtLeast -> value >= condition.threshold
                        ScriptComparison.AtMost -> value <= condition.threshold
                        ScriptComparison.Equal -> value == condition.threshold
                    }
                }
            }

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
    fun reachableScenes(
        pack: ScriptPack,
        scene: ScriptScene,
        variables: Map<String, Int> = emptyMap()
    ): List<ScriptScene> {
        if (scene.nextSceneIds.isNotEmpty()) {
            return scene.nextSceneIds.mapNotNull { id -> pack.scenes.firstOrNull { it.id == id } }
        }
        // 自由漫游时按解锁条件筛：状态没到，这扇门就是关着的
        return pack.scenes.filterNot { it.id == scene.id }.filter { target ->
            isUnlocked(target, variables)
        }
    }

    /**
     * 场景是否已解锁。
     *
     * 没写解锁条件的视为常开；写了的要求变量达到阈值。
     * 只影响自由漫游，主线推进不看这条，免得条件写错把主线卡死。
     */
    fun isUnlocked(scene: ScriptScene, variables: Map<String, Int>): Boolean {
        val condition = scene.unlockCondition ?: return true
        return matches(condition, roundCount = 0, lastReply = "", variables = variables)
    }

    /**
     * 挑出本轮该触发的事件。
     *
     * 先排除已触发过的一次性事件，再看轮次与条件。
     * 同一轮只放行一个——两个事件同时改在场名单会让场面失控。
     *
     * @param firedEventKeys 已触发事件的标识集合
     * @param variables 当前变量快照
     * @param random 随机源；传固定种子就能复现同一场
     */
    fun pickEvent(
        scene: ScriptScene,
        roundCount: Int,
        lastReply: String,
        firedEventKeys: Set<String> = emptySet(),
        variables: Map<String, Int> = emptyMap(),
        random: Random = Random.Default
    ): ScriptEvent? = scene.events.firstOrNull { event ->
        val blocked = event.once && eventKey(scene, event) in firedEventKeys
        !blocked &&
            matchesEvent(event, roundCount, lastReply, variables) &&
            rollChance(event.chance, random)
    }

    /**
     * 事件是否满足触发条件：轮次到了，或条件命中。
     *
     * 轮次和条件都没配时视为「进入即触发」。
     */
    fun matchesEvent(
        event: ScriptEvent,
        roundCount: Int,
        lastReply: String,
        variables: Map<String, Int> = emptyMap()
    ): Boolean {
        val roundHit = event.atRound > 0 && roundCount >= event.atRound
        val conditionHit = event.condition
            ?.let { matches(it, roundCount, lastReply, variables) }
            ?: false
        if (event.atRound <= 0 && event.condition == null) return true
        return roundHit || conditionHit
    }

    /**
     * 按百分比掷一次概率。
     *
     * 100 直接放行、0 直接拦下，避免为了必发或必不发还去摇一次随机。
     */
    fun rollChance(chance: Int, random: Random = Random.Default): Boolean = when {
        chance >= 100 -> true
        chance <= 0 -> false
        else -> random.nextInt(100) < chance
    }

    /**
     * 事件标识。
     *
     * 作者没写 id 时用「场景 + 序号」兜底，
     * 保证同一份剧本里事件能被稳定记住。
     */
    fun eventKey(scene: ScriptScene, event: ScriptEvent): String {
        if (event.id.isNotBlank()) return event.id
        return scene.id + "#" + scene.events.indexOf(event)
    }

    /**
     * 应用事件对在场名单的影响。
     *
     * 移除优先于追加：作者同时写了同名角色时，
     * 意图通常是「这一场让他退下」。
     */
    fun applyEventCast(current: List<String>, event: ScriptEvent): List<String> {
        val removed = current.filterNot { it in event.removeCast }
        // 同名同时出现在增删里时按移除算：作者意图是让这个人退下
        val added = event.addCast.filterNot { it in event.removeCast }
        return (removed + added).distinct()
    }

    /**
     * 应用事件对变量的改动。
     *
     * 按剧本里的 min/max 钳制——好感度冲到 999 没有意义，
     * 而且会让后续阈值判定全部失效。
     */
    fun applyEffects(
        variables: Map<String, Int>,
        effects: List<ScriptEffect>,
        declared: List<ScriptVariable> = emptyList()
    ): Map<String, Int> {
        if (effects.isEmpty()) return variables
        val result = variables.toMutableMap()
        effects.forEach { effect ->
            if (effect.variable.isBlank()) return@forEach
            val current = result[effect.variable] ?: 0
            val next = effect.setTo ?: (current + effect.delta)
            val bounds = declared.firstOrNull { it.name == effect.variable }
            result[effect.variable] = next.coerceIn(
                bounds?.min ?: Int.MIN_VALUE,
                bounds?.max ?: Int.MAX_VALUE
            )
        }
        return result
    }


    /**
     * 算出本场实际在场名单。
     *
     * 常驻 cast 加条件到场的角色，再去掉禁止名单。
     * 结果为空是合法的：零角色场景只剩旁白。
     */
    fun effectiveCast(
        scene: ScriptScene,
        variables: Map<String, Int> = emptyMap(),
        roundCount: Int = 0,
        lastReply: String = ""
    ): List<String> {
        val base = scene.cast.map { it.name }
        val arrivals = scene.conditionalCast
            .filter { rule ->
                val condition = rule.condition ?: return@filter true
                matches(condition, roundCount, lastReply, variables)
            }
            .map { it.name }
        return (base + arrivals)
            .filterNot { it in scene.bannedCast }
            .distinct()
    }

    /** 按剧本声明的初值建一份变量表。 */
    fun initialVariables(pack: ScriptPack): Map<String, Int> =
        pack.variables
            .filter { it.name.isNotBlank() }
            .associate { it.name to it.initial.coerceIn(it.min, it.max) }
}
