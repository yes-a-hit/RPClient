package me.kafuuneko.rpclient.libs.script

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一局对话中的一条消息。 */
data class ScriptPlayMessage(
    /** 发言者：`player` 或场景内角色名。 */
    val speaker: String,
    val content: String
)

/**
 * 剧本模式的一局存档。
 *
 * 与群聊的区别在于：这里有一个明确的「当前场景」和轮次计数，
 * 场景决定注入哪套局部世界书、能出现哪些角色、还能走多少轮。
 */
data class ScriptPlaythrough(
    /** 存档 ID，与剧本 ID 拼成文件名。 */
    val id: String,
    /** 所属剧本。 */
    val packId: String,
    val packName: String,
    /** 当前场景。 */
    val sceneId: String,
    /** 已经进行的轮数。 */
    val roundCount: Int = 0,
    /** 本局对话历史。 */
    val messages: List<ScriptPlayMessage> = emptyList(),
    /** 本局是否已结束，[endReason] 说明结束原因。 */
    val finished: Boolean = false,
    val endReason: String = "",
    /**
     * 最近一次检查点快照。
     *
     * 剧本用 `checkpoint` 标记关键场景，进入时记下当时的对话与轮次，
     * 之后走偏了能退回去重来，不用整局重开。
     */
    val snapshot: ScriptPlaySnapshot? = null,
    /**
     * 已触发过的场景事件标识。
     *
     * 一次性事件靠它去重；换场景不清空，同一事件不会反复上演。
     */
    val firedEvents: List<String>? = null,
    /**
     * 事件对在场名单的临时改动。
     *
     * 为空时以场景自带的 cast 为准；事件增删角色后写这里，
     * 清空到零人就是「只与旁白互动」。
     */
    val castOverride: List<String>? = null,
    /**
     * 剧本变量当前值。
     *
     * 玩家行为累积出的状态，条件与事件都读它；
     * 存档带着走，退出再进来不会丢。
     */
    val variables: Map<String, Int>? = null,
    /** 文本变量的当前值，例如各玩偶所在位置。 */
    val textVariables: Map<String, String>? = null
)

/** 检查点快照：够还原到进入该场景的那一刻。 */
data class ScriptPlaySnapshot(
    val sceneId: String,
    val roundCount: Int,
    val messages: List<ScriptPlayMessage> = emptyList()
)

/** 场景状态快照，供界面展示。 */
data class ScriptPlaySceneState(
    val sceneId: String,
    val sceneName: String,
    /** 轮次上限，0 表示不限。 */
    val maxRounds: Int,
    /** 已进行轮数。 */
    val roundCount: Int,
    /** 可切换的后继场景。 */
    val nextSceneIds: List<String>,
    /** 是否检查点。 */
    val checkpoint: Boolean
)

/**
 * 剧本模式存档存储。
 *
 * 一局一个文件，与剧本定义分开存放：
 * 剧本改动不会破坏已有存档，存档也不会污染剧本库列表。
 */
class ScriptPlayStore(private val mContext: Context) {
    private val mRoot: File by lazy {
        File(mContext.filesDir, "script-play").apply { mkdirs() }
    }

    /** 读取某局的存档；不存在时返回 null。 */
    suspend fun load(playId: String): ScriptPlaythrough? = withContext(Dispatchers.IO) {
        val file = File(mRoot, "$playId.json")
        if (!file.isFile) return@withContext null
        runCatching {
            Gson().fromJson(file.readText(), ScriptPlaythrough::class.java)
        }.getOrNull()
    }

    /**
     * 列出全部存档，按最近游玩排序。
     *
     * 只解析存档头部字段，不加载全部消息——存档可能很大，
     * 列表页只需要知道是哪部剧本、玩到哪个场景。
     */
    suspend fun list(): List<ScriptPlaythrough> = withContext(Dispatchers.IO) {
        mRoot.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { parsePlay(file.readText()) }.getOrNull()
            }
            .sortedByDescending { it.roundCount }
    }


    /**
     * 手工解析一条存档。
     *
     * 不走 Gson 直接反序列化：它用 Unsafe 建实例、不执行构造体，
     * 旧存档缺的字段会变成 null，后面任何一次 copy 都会 NPE。
     * 这里逐字段读取并给默认值，缺什么补什么。
     */
    private fun parsePlay(text: String): ScriptPlaythrough? {
        val obj = runCatching {
            com.google.gson.JsonParser.parseString(text).asJsonObject
        }.getOrNull() ?: return null
        fun str(key: String): String =
            obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        fun int(key: String): Int =
            obj.get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        fun bool(key: String): Boolean =
            obj.get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        val name = str("packName")
        val sceneId = str("sceneId")
        if (name.isBlank() && sceneId.isBlank()) return null
        return ScriptPlaythrough(
            id = str("id"),
            packId = str("packId"),
            packName = name,
            sceneId = sceneId,
            roundCount = int("roundCount"),
            messages = emptyList(),
            finished = bool("finished"),
            endReason = str("endReason"),
            snapshot = obj.get("snapshot")?.let { element ->
                runCatching {
                    Gson().fromJson(element, ScriptPlaySnapshot::class.java)
                }.getOrNull()
            },
            firedEvents = obj.getAsJsonArray("firedEvents")
                ?.mapNotNull { it.takeIf { item -> item.isJsonPrimitive }?.asString }
                .orEmpty(),
            castOverride = obj.getAsJsonArray("castOverride")
                ?.mapNotNull { it.takeIf { item -> item.isJsonPrimitive }?.asString },
            variables = runCatching {
                obj.getAsJsonObject("variables")?.entrySet()?.associate { entry ->
                    entry.key to entry.value.asInt
                }
            }.getOrNull(),
            textVariables = runCatching {
                obj.getAsJsonObject("textVariables")?.entrySet()?.associate { entry ->
                    entry.key to entry.value.asString
                }
            }.getOrNull()
        )
    }

    /** 删除一局存档。 */
    suspend fun delete(playId: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(mRoot, "$playId.json")
        !file.exists() || runCatching { file.delete() }.getOrDefault(false)
    }

    /** 写入存档。 */
    suspend fun save(play: ScriptPlaythrough): Boolean = withContext(Dispatchers.IO) {
        val target = File(mRoot, play.id + ".json")
        runCatching {
            target.writeText(Gson().toJson(play))
            true
        }.onFailure { error ->
            // 静默失败等于进度悄悄丢光，至少要留个痕迹
            android.util.Log.e("ScriptPlay", "save failed: " + target.absolutePath, error)
        }.getOrDefault(false)
    }
}

/**
 * 剧本模式的 Prompt 组装。
 *
 * 注入顺序固定：剧本设定 → 全局世界书 → 当前场景局部世界书 →
 * 出场角色限制 → 场景开场 → 轮次提示。顺序影响模型注意力，所以写死在这里，
 * 不让调用方各自拼。
 */
object ScriptPlayPromptBuilder {
    /**
     * 构建系统提示词。
     *
     * @param pack 当前剧本
     * @param scene 当前场景
     * @param roundCount 已进行轮数
     * @param remainingRounds 剩余轮数，0 表示不限
     */
    fun buildSystemPrompt(
        pack: ScriptPack,
        scene: ScriptScene,
        roundCount: Int,
        remainingRounds: Int,
        activeCharacters: List<String> = emptyList(),
        mutedCharacters: Set<String> = emptySet(),
        forcedSpeaker: String? = null,
        /**
         * 自动挑出的本轮发言人。
         *
         * 与 [forcedSpeaker] 的区别：这个是系统按轮转规则算出来的，
         * 玩家没指定时用它，保证多角色场景不会一直是同一个人在说。
         */
        autoSpeaker: String? = null,
        /**
         * 事件改写后的在场名单。
         *
         * 传 null 表示没被事件动过，按场景自带 cast 处理；
         * 传空列表表示这一场真的没人了，只留旁白。
         */
        castOverride: List<String>? = null,
        /**
         * 当前变量快照。
         *
         * 不告诉模型的话它会自己编数值，嘴里说的电量和实际判定对不上；
         * 只注入声明里 visible 的那些，隐藏变量不该被念出来。
         */
        variables: Map<String, Int> = emptyMap(),
        declaredVariables: List<ScriptVariable> = emptyList(),
        /** 文本变量的当前值，位置这类状态由它承载。 */
        textVariables: Map<String, String> = emptyMap(),
        /**
         * 在场角色的档案，名字到设定的映射。
         *
         * 只给名字模型只会写旁白，不知道这个人是什么性格、怎么说话；
         * 把角色卡里的设定带进来，角色才真的参与对话。
         */
        castProfiles: Map<String, String> = emptyMap()
    ): String {
        val builder = StringBuilder()
        builder.append("你正在主持一场角色扮演剧本《").append(pack.name).append("》。\n")
        if (pack.description.isNotBlank()) {
            builder.append(pack.description).append('\n')
        }
        builder.append("\n【当前场景】").append(scene.name).append('\n')

        // 可见变量写进提示词：模型据此叙述，不能自己另编一套数值
        val shown = declaredVariables.filter {
            it.visible && it.name.isNotBlank() && it.kind != ScriptVariableKind.Note
        }
        if (shown.isNotEmpty()) {
            builder.append("\n【当前状态】")
            builder.append(
                shown.joinToString("、") { variable ->
                    val label = variable.label.ifBlank { variable.name }
                    val value = when (variable.kind) {
                        ScriptVariableKind.Text ->
                            textVariables[variable.name] ?: variable.text

                        ScriptVariableKind.Switch ->
                            if ((variables[variable.name] ?: variable.initial) != 0) {
                                variable.onLabel.ifBlank { "是" }
                            } else {
                                variable.offLabel.ifBlank { "否" }
                            }

                        else -> (variables[variable.name] ?: variable.initial).toString()
                    }
                    label + " " + value
                }
            )
            builder.append("\n以上状态由系统维护，叙述时以此为准，不要另编数字。\n")
        }

        // 线索单独列一段：它是玩家已经查到的结论，
        // 写进旁白会重复，混在状态里又会被淹没
        val clues = declaredVariables.filter {
            it.kind == ScriptVariableKind.Note && it.name.isNotBlank()
        }
        if (clues.isNotEmpty()) {
            val recorded = clues.mapNotNull { variable ->
                val content = textVariables[variable.name] ?: variable.text
                if (content.isBlank()) null else content
            }
            if (recorded.isNotEmpty()) {
                builder.append("\n【已知线索】\n")
                recorded.forEach { builder.append(it).append('\n') }
                builder.append(
                    "这些是玩家已经掌握的信息，叙述时可直接引用，" +
                        "不要再当成新发现重复描写；玩家说出新的关键信息时，系统会自行记录。\n"
                )
            }
        }

        // 位置类的文本变量额外点名：它们决定谁站在哪，
        // 模型据此安排谁出场、从哪条路来，而不是每轮都重新猜
        val positions = declaredVariables.filter {
            it.kind == ScriptVariableKind.Text && it.name.isNotBlank()
        }
        if (positions.isNotEmpty()) {
            builder.append("\n【在场去向】\n")
            positions.forEach { variable ->
                val label = variable.label.ifBlank { variable.name }
                builder.append(label).append("：")
                    .append(textVariables[variable.name] ?: variable.text)
                    .append('\n')
            }
            builder.append(
                "这些位置由系统给出。玩偶的行动、什么时候移动、往哪走，" +
                    "由你根据当前位置与局势自行决定；" +
                    "若某个玩偶有自己的硬规则（例如只在没人盯梢时才动），以场景设定为准。\n"
            )
        }


        buildLorebookSection("全局设定", pack.globalLorebook)?.let(builder::append)
        buildLorebookSection("场景设定", scene.lorebook)?.let(builder::append)

        val effectiveCast = castOverride ?: scene.cast.map { it.name }
        if (effectiveCast.isNotEmpty()) {
            builder.append("\n【可出场角色】")
            builder.append(effectiveCast.joinToString("、"))
            builder.append('\n')
            // 有档案就把人设摊开：模型据此演出，而不是只念旁白
            val profiles = effectiveCast.mapNotNull { name ->
                val profile = castProfiles[name]?.trim().orEmpty()
                if (profile.isBlank()) null else "$name：$profile"
            }
            if (profiles.isNotEmpty()) {
                builder.append("【角色设定】\n")
                profiles.forEach { builder.append(it).append('\n') }
                builder.append(
                    "按以上设定演出。每一轮都必须有角色真正开口说话或用动作回应，" +
                        "至少一句台词；旁白只用来交代环境与过渡，" +
                        "不能整段都在描写却没有角色参与。\n"
                )
            }
        } else if (castOverride != null) {
            // 事件把人都清走了：这一场只有环境与旁白
            builder.append("\n【本场没有角色在场】只能以旁白描写环境与动静，不要替任何角色说话。\n")
        }
        // GM 本轮干预：禁言名单让某些角色闭嘴，指定发言让场面往想要的方向推
        if (mutedCharacters.isNotEmpty()) {
            builder.append("\\n【本轮禁言】")
            builder.append(mutedCharacters.joinToString("、"))
            builder.append("，本轮不得让他们说话。\\n")
        }
        // 自动安排的发言人；玩家手动指定时以手动为准
        if (autoSpeaker != null && forcedSpeaker.isNullOrBlank() &&
            autoSpeaker !in mutedCharacters
        ) {
            builder.append("\n【本轮建议由】")
            builder.append(autoSpeaker)
            builder.append(" 发言，其他人简短回应即可。\n")
        }
        if (!forcedSpeaker.isNullOrBlank()) {
            builder.append("\\n【本轮必须发言】")
            builder.append(forcedSpeaker)
            builder.append("，本轮至少给他一句台词。\\n")
        }

        // 玩家自己挑的在场角色，与剧本名单取并集，剧本没写时也能用
        if (activeCharacters.isNotEmpty()) {
            builder.append("\n【玩家指定在场】")
            builder.append(activeCharacters.joinToString("、"))
            builder.append("\n")
        }
        if (scene.bannedCast.isNotEmpty()) {
            builder.append("【禁止出场角色】")
            builder.append(scene.bannedCast.joinToString("、"))
            builder.append("：这些角色不得在任何回复中出现。\n")
        }
        if (scene.entryPrompt.isNotBlank()) {
            builder.append("\n【本场开场】").append(scene.entryPrompt).append('\n')
        }
        if (pack.enableGm) {
            builder.append("\n【主持要求】以旁白推进场景，描写环境与角色反应，不要替玩家做决定。\n")
        }
        // 要求分段输出：角色台词带名字，界面才能配头像气泡
        builder.append("\n【输出格式】每段以【旁白】或【角色名】开头，一段一件事：\n")
        builder.append("【旁白】描写环境与动作。\n【角色名】「这个角色说的话」。\n")
        builder.append("不要在段首之外再写名字，也不要替玩家发言。\n")

        // 让模型在本轮末尾给候选行动，玩家点一下就能继续，不用自己想输入什么
        builder.append("\n【候选行动】在本轮正文之后另起一段，输出 2 到 3 个玩家可以立刻执行的行动，")
        builder.append("用下列格式包住，正文里不要重复这些选项：\n")
        builder.append("<<<OPTIONS\n1. 行动一\n2. 行动二\n>>>\n")
        if (remainingRounds > 0) {
            builder.append("【进度】已进行 $roundCount 轮，本场景还剩 $remainingRounds 轮。\n")
        } else if (scene.maxRounds == 0) {
            builder.append("【进度】本场景不限轮次。\n")
        }
        return builder.toString()
    }

    /** 渲染一组世界书条目；没有条目时返回 null。 */
    private fun buildLorebookSection(
        title: String,
        lorebook: ScriptLorebook?
    ): String? {
        if (lorebook == null || lorebook.entries.isEmpty()) return null
        val builder = StringBuilder("\n【").append(title).append("】\n")
        lorebook.entries.forEach { entry ->
            builder.append("- ").append(entry.name).append("：").append(entry.content).append('\n')
        }
        return builder.toString()
    }

    /** 转换历史消息为对话上下文，保留最近若干条。 */
    fun buildHistory(messages: List<ScriptPlayMessage>, limit: Int): List<ScriptPlayMessage> =
        messages.takeLast(limit)
}
