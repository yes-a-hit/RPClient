package me.kafuuneko.rpclient.libs.assistant

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 助手会话的消息存储。
 *
 * 会话只有一条主线，不需要多会话管理，因此直接落成单个 JSON 文件；
 * 这样既避免了为助手单独做一次 Room 迁移，也让备份和排查只需要看一个文本文件。
 */
class AssistantStore(private val mContext: Context) {
    /** 沙箱根目录：助手唯一被允许读写的文件区域。 */
    val sandboxRoot: File by lazy {
        File(mContext.filesDir, "assistant/sandbox").apply { mkdirs() }
    }

    private val mSessionFile: File by lazy {
        File(mContext.filesDir, "assistant/session.json")
    }

    /** 读取历史消息；文件缺失或损坏时按空会话处理。 */
    suspend fun load(): List<AssistantMessage> = withContext(Dispatchers.IO) {
        if (!mSessionFile.isFile) return@withContext emptyList()
        runCatching {
            val type = object : TypeToken<List<AssistantMessage>>() {}.type
            val parsed: List<AssistantMessage>? = Gson().fromJson(mSessionFile.readText(), type)
            parsed ?: emptyList()
        }.getOrElse { emptyList() }
    }

    /** 覆盖写入当前会话。 */
    suspend fun save(messages: List<AssistantMessage>) = withContext(Dispatchers.IO) {
        mSessionFile.parentFile?.mkdirs()
        mSessionFile.writeText(Gson().toJson(messages))
    }

    /** 清空会话文件；沙箱文件不受影响。 */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mSessionFile.delete()
        Unit
    }

    /** 沙箱内的文件数量与占用，供页面展示权限范围。 */
    fun sandboxSummary(): String {
        val files = sandboxRoot.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) return "沙箱为空"
        val bytes = files.sumOf { it.length() }
        return "沙箱 ${files.size} 个文件 · ${formatSize(bytes)}"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    }
}

/**
 * 一条助手会话消息。
 *
 * [role] 只取 user、assistant、tool 三种：
 * - user 是用户输入；
 * - assistant 是模型可见的自然语言回复（不含工具调用块）；
 * - tool 是工具执行结果，只在上下文里回填给模型，页面折叠展示。
 *
 * [toolName] 记录产生该结果工具名，仅 tool 角色使用。
 */
data class AssistantMessage(
    val role: String,
    val content: String,
    val toolName: String? = null
)
