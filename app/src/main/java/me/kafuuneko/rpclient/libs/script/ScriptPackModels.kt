package me.kafuuneko.rpclient.libs.script

/**
 * 剧本包：一套可独立导入、分发的角色扮演剧本。
 *
 * 结构上把「设定」与「流程」分开：
 * - 设定由全局世界书与每个场景的局部世界书组成，导入后落成普通世界书，可直接复用现有触发规则；
 * - 流程由场景顺序、循环开关、失败条件与通关条件描述，运行时只按 [ScriptPack.mode] 解释，
 *   不在数据层写死状态机。
 */
data class ScriptPack(
    /** 结构版本，用于将来兼容旧剧本。 */
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    /** 剧本唯一标识，导入时用于去重。 */
    val id: String,
    /** 剧本名称。 */
    val name: String,
    /** 简介，展示在剧本列表。 */
    val description: String = "",
    /** 运行模式。 */
    val mode: ScriptMode = ScriptMode.Mainline,
    /** 全局世界书，整局都参与 Prompt 构建。 */
    val globalLorebook: ScriptLorebook? = null,
    /** 场景列表，顺序即推荐推进顺序。 */
    val scenes: List<ScriptScene> = emptyList(),
    /** 起始场景 ID；单选写法，等价于只有一个元素的 [startSceneIds]。 */
    val startSceneId: String = "",
    /** 多入口场景 ID 列表，用于多主角各自开线的剧本。 */
    val startSceneIds: List<String> = emptyList(),
    /** 通关后是否进入自由模式。 */
    val freeModeAfterClear: Boolean = true,
    /** 是否启用 GM（游戏主持人）叙述层。 */
    val enableGm: Boolean = true,
    /** 是否启用骰子判定。 */
    val enableDice: Boolean = true,
    /**
     * 开场白。
     *
     * 每条由发言者与内容组成，按顺序作为开局消息播种，
     * 空发言者表示旁白。可以没有，也可以有多条。
     */
    val openings: List<ScriptOpening> = emptyList(),
    /** 成功条件是否对玩家可见。 */
    val showCompletion: Boolean = true,
    /** 设定是否允许其他玩家查看。 */
    val shareSettings: Boolean = false
) {
    companion object {
        /** 当前支持的剧本结构版本。 */
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/** 剧本运行模式。 */
enum class ScriptMode {
    /** 主线：按场景顺序推进，可设置失败条件与检查点。 */
    Mainline,

    /** 循环：场景可在失败后重复，直到满足通关条件。 */
    Loop,

    /** 自由：不限制场景顺序，仅提供设定与场景切换。 */
    Free
}

/**
 * 一个可进入的场景。
 *
 * - 局部世界书只在当前场景生效，与全局世界书叠加；
 * - [nextSceneIds] 为空表示当前场景是终点（主线通关点）；
 * - [checkpoint] 为真时允许失败后回到本场景重来。
 */
data class ScriptScene(
    val id: String,
    val name: String,
    /** 进入场景时插入的开场描述，作为一次性系统提示。 */
    val entryPrompt: String = "",
    /** 场景专属世界书。 */
    val lorebook: ScriptLorebook? = null,
    /** 后继场景 ID，按顺序尝试；空表示终点。 */
    val nextSceneIds: List<String> = emptyList(),
    /** 是否允许重复进入（循环模式常用）。 */
    val loopEnabled: Boolean = false,
    /** 是否作为失败回滚的检查点。 */
    val checkpoint: Boolean = false,
    /** 失败条件，命中即判定本场景失败。 */
    val failConditions: List<ScriptCondition> = emptyList(),
    /** 通关条件，命中即判定本场景完成。 */
    val completionCondition: ScriptCondition? = null,
    /** 允许出场的角色；为空表示不限制。 */
    val cast: List<ScriptCastMember> = emptyList(),
    /** 禁止出场的角色名称，优先级高于 [cast]。 */
    val bannedCast: List<String> = emptyList(),
    /** 本场景最多进行的对话轮数，0 表示不限。 */
    val maxRounds: Int = 0,
    /** 前置场景：这些场景都经历过才解锁；为空表示一开始就可达。 */
    val unlockedBy: List<String> = emptyList(),
    /** 图形化编辑器的节点坐标；手写剧本时可以省略。 */
    val layout: SceneLayout? = null,
    /**
     * 场景背景图的检索词，对应图库里的标签或名称。
     *
     * 只做本地匹配、不触发生成：同一场景反复进入都取同一张图，
     * 既不额外花钱，也不会每次换一张让人出戏。
     */
    val backgroundKey: String = ""
)

/**
 * 场景在图形化编辑器中的位置。
 *
 * 只存坐标，不存连线：连线由 [ScriptScene.nextSceneIds] 与 [ScriptScene.unlockedBy] 推导，
 * 保证画布上的箭头与运行时可达性永远来自同一份数据。
 */
data class SceneLayout(
    val x: Float = 0f,
    val y: Float = 0f
)

/**
 * 剧本内的出场角色描述。
 *
 * 剧本是外部文件，无法预知本地角色卡 ID，因此按名称匹配：
 * 导入时先找同名角色卡，找不到再按 [description] 自动创建，
 * 这样同一个剧本在不同设备上都能落地。
 */
data class ScriptCastMember(
    /** 角色卡名称，用于匹配本地球色卡。 */
    val name: String,
    /** 是否必须出场；非必须角色可以缺席。 */
    val required: Boolean = true,
    /** 剧本自带的角色设定，用于自动创建角色卡。 */
    val description: String = ""
)

/** 一组世界书设定。 */
data class ScriptLorebook(
    val name: String,
    val entries: List<ScriptLorebookEntry> = emptyList()
)

/** 单条设定。 */
data class ScriptLorebookEntry(
    val name: String,
    val content: String,
    /** 触发关键词；为空且 [constant] 为真时按常驻注入。 */
    val keys: List<String> = emptyList(),
    /** 是否常驻注入。 */
    val constant: Boolean = false
)

/**
 * 条件表达式。
 *
 * 只保留可确定性判定的几种类型，避免把解释权交给模型：
 * 关键词命中交给文本匹配，轮次上限与分数阈值由运行时计数。
 */
data class ScriptCondition(
    val type: ScriptConditionType,
    /** 关键词或正则片段，[ScriptConditionType.Keyword] 与 [ScriptConditionType.Regex] 使用。 */
    val pattern: String = "",
    /** 轮次或数值阈值，[ScriptConditionType.RoundLimit] 与 [ScriptConditionType.ScoreAtLeast] 使用。 */
    val threshold: Int = 0,
    /** 展示给用户的失败或通关说明。 */
    val description: String = ""
)

/** 条件类型。 */
enum class ScriptConditionType {
    /** 最近消息命中关键词。 */
    Keyword,

    /** 最近消息命中正则。 */
    Regex,

    /** 本场景对话轮数达到阈值。 */
    RoundLimit,

    /** 场景评分达到阈值。 */
    ScoreAtLeast
}

/**
 * 剧本集：一部作品的宏观容器。
 *
 * 剧本集负责组织多个剧本包（章节、支线、番外），并给出整集的默认开关；
 * 单个剧本包上的同名字段用于覆盖集合默认值。
 */
data class ScriptCollection(
    /** 结构版本，与剧本包共用同一套版本号。 */
    val formatVersion: Int = ScriptPack.CURRENT_FORMAT_VERSION,
    /** 剧本集唯一标识。 */
    val id: String,
    /** 剧本集名称。 */
    val name: String,
    /** 简介。 */
    val description: String = "",
    /** 集内剧本包，按推荐游玩顺序排列。 */
    val packs: List<ScriptPack> = emptyList(),
    /** 整集默认是否启用 GM。 */
    val enableGm: Boolean = true,
    /** 整集默认是否启用骰子。 */
    val enableDice: Boolean = true
) {
    /** 按 ID 查找剧本包。 */
    fun packById(packId: String): ScriptPack? = packs.firstOrNull { it.id == packId }
}

/** 一条开场白：谁说的、说了什么。 */
data class ScriptOpening(
    /** 发言者名称；为空表示旁白。 */
    val speaker: String = "",
    /** 开场内容。 */
    val content: String = ""
)
