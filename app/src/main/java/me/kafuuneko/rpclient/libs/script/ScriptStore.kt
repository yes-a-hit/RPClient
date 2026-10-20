package me.kafuuneko.rpclient.libs.script

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 已保存剧本的摘要，用于列表展示。 */
data class ScriptPackSummary(
    val id: String,
    val name: String,
    /** 场景数量。 */
    val sceneCount: Int,
    /** 运行模式的展示名。 */
    val mode: ScriptMode
)

/**
 * 剧本文件存储。
 *
 * 剧本是低频读写的结构化文本，放文件系统而不是 Room：
 * 不需要为新功能做数据库迁移，出问题也能直接看原文。
 *
 * 目录结构：`filesDir/scripts/<packId>.json`，一个剧本一个文件。
 * 文件名取剧本 id 并做安全化处理，避免路径穿越。
 */
class ScriptStore(private val mContext: Context) {
    private val mCodec = ScriptPackCodec()

    /** 剧本目录。 */
    private val mRoot: File by lazy {
        File(mContext.filesDir, "scripts").apply { mkdirs() }
    }

    /** 列出全部已保存剧本，按名称排序。 */
    suspend fun list(): List<ScriptPackSummary> = withContext(Dispatchers.IO) {
        // 目录与文件数打到日志，路径对不上时一眼能看出来
        val candidates = mRoot.listFiles { file -> file.isFile && file.extension == "json" }
        android.util.Log.d(
            "ScriptStore",
            "dir=" + mRoot.absolutePath + " files=" + (candidates?.size ?: -1) +
                " names=" + candidates?.joinToString(",") { it.name }
        )
        candidates
            ?.mapNotNull { file ->
                // 单个文件损坏不影响其余剧本的展示
                when (val parsed = mCodec.decode(file.readText())) {
                    is ScriptPackParseResult.Success -> ScriptPackSummary(
                        id = parsed.pack.id,
                        name = parsed.pack.name,
                        sceneCount = parsed.pack.scenes.size,
                        mode = parsed.pack.mode
                    )

                    is ScriptPackParseResult.Failure -> {
                        // 解析失败不再静默丢弃：记下文件名与原因，方便排查
                        android.util.Log.w(
                            "ScriptStore",
                            "skip " + file.name + ": " + parsed.error + " " + parsed.detail
                        )
                        null
                    }
                }
            }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    /** 按 ID 读取剧本；文件缺失或损坏时返回 null。 */
    suspend fun load(packId: String): ScriptPack? = withContext(Dispatchers.IO) {
        if (packId.isBlank()) return@withContext null
        // 先按 id 拼文件名找，这是自己保存时的命名规则
        val direct = File(mRoot, fileNameOf(packId))
        if (direct.isFile) {
            return@withContext when (val parsed = mCodec.decode(direct.readText())) {
                is ScriptPackParseResult.Success -> parsed.pack
                is ScriptPackParseResult.Failure -> null
            }
        }
        // 找不到再扫一遍：外部导入的剧本文件名未必等于 id，
        // 只按文件名找会让这类剧本「点进去毫无反应」
        mRoot.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .firstNotNullOfOrNull { file ->
                val parsed = mCodec.decode(file.readText())
                (parsed as? ScriptPackParseResult.Success)
                    ?.pack
                    ?.takeIf { it.id == packId }
            }
    }

    /** 读取任务队列中的第一个剧本，供编辑页默认打开。 */
    suspend fun loadFirst(): ScriptPack? = withContext(Dispatchers.IO) {
        list().firstOrNull()?.let { load(it.id) }
    }

    /** 保存剧本，覆盖同名文件。 */
    suspend fun save(pack: ScriptPack): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            File(mRoot, fileNameOf(pack.id)).writeText(mCodec.encode(pack))
            true
        }.getOrDefault(false)
    }

    /** 删除剧本文件。 */
    suspend fun delete(packId: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(mRoot, fileNameOf(packId))
        !file.exists() || file.delete()
    }

    /**
     * 从文本或剧本集文件导入。
     *
     * 先按单剧本解析，失败再尝试剧本集；剧本集只导入其中的第一个剧本，
     * 其余由调用方按需继续处理，避免一次导入写入过多文件。
     */
    suspend fun importFromText(text: String): ScriptImportOutcome = withContext(Dispatchers.IO) {
        when (val single = mCodec.decode(text)) {
            is ScriptPackParseResult.Success -> {
                val pack = single.pack
                if (save(pack)) {
                    ScriptImportOutcome.Success(pack.id, pack.name, pack.scenes.size, 1)
                } else {
                    ScriptImportOutcome.Failure("无法写入剧本文件")
                }
            }

            is ScriptPackParseResult.Failure -> {
                when (val collection = mCodec.decodeCollection(text)) {
                    is ScriptCollectionParseResult.Success -> {
                        var saved = 0
                        collection.collection.packs.forEach { pack ->
                            if (save(pack)) saved++
                        }
                        if (saved > 0) {
                            val first = collection.collection.packs.first()
                            ScriptImportOutcome.Success(first.id, first.name, first.scenes.size, saved)
                        } else {
                            ScriptImportOutcome.Failure("剧本集写入失败")
                        }
                    }

                    is ScriptCollectionParseResult.Failure ->
                        ScriptImportOutcome.Failure(single.detail)
                }
            }
        }
    }

    /** 从系统文件选择器读取并导入。 */
    suspend fun importFromUri(uri: Uri): ScriptImportOutcome = withContext(Dispatchers.IO) {
        val text = runCatching {
            mContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) return@withContext ScriptImportOutcome.Failure("文件为空或无法读取")
        importFromText(text)
    }

    /** 文件名安全化：只保留字母、数字、下划线与连字符。 */
    private fun fileNameOf(packId: String): String {
        val safe = packId.map { char ->
            if (char.isLetterOrDigit() || char == '_' || char == '-') char else '_'
        }.joinToString("")
        return "${safe.ifBlank { "unnamed" }}.json"
    }
}

/** 导入结果。 */
sealed class ScriptImportOutcome {
    /** 成功导入，[savedPackCount] 为实际写入的剧本数量。 */
    data class Success(
        val packId: String,
        val packName: String,
        val sceneCount: Int,
        val savedPackCount: Int
    ) : ScriptImportOutcome()

    /** 失败，[detail] 给出可展示的原因。 */
    data class Failure(val detail: String) : ScriptImportOutcome()
}
