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
    val endReason: String = ""
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
                runCatching {
                    Gson().fromJson(file.readText(), ScriptPlaythrough::class.java)
                }.getOrNull()?.copy(messages = emptyList())
            }
            .sortedByDescending { it.roundCount }
    }

    /** 删除一局存档。 */
    suspend fun delete(playId: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(mRoot, "$playId.json")
        !file.exists() || runCatching { file.delete() }.getOrDefault(false)
    }

    /** 写入存档。 */
    suspend fun save(play: ScriptPlaythrough): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            File(mRoot, "${play.id}.json").writeText(Gson().toJson(play))
            true
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
        remainingRounds: Int
    ): String {
        val builder = StringBuilder()
        builder.append("你正在主持一场角色扮演剧本《").append(pack.name).append("》。\n")
        if (pack.description.isNotBlank()) {
            builder.append(pack.description).append('\n')
        }
        builder.append("\n【当前场景】").append(scene.name).append('\n')

        buildLorebookSection("全局设定", pack.globalLorebook)?.let(builder::append)
        buildLorebookSection("场景设定", scene.lorebook)?.let(builder::append)

        if (scene.cast.isNotEmpty()) {
            builder.append("\n【可出场角色】")
            builder.append(scene.cast.joinToString("、") { it.name })
            builder.append('\n')
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
