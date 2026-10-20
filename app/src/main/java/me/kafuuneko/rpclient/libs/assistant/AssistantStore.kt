package me.kafuuneko.rpclient.libs.assistant

import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantAttachment
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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

    /**
     * 把手机里的文件复制进沙箱，供助手读写。
     *
     * 只复制内容不保留原路径；重名时自动追加序号，避免互相覆盖。
     * 助手仍只能看到沙箱内部，所以导入是它接触外部内容的唯一入口。
     *
     * @param uris 系统文件选择器返回的 URI
     * @return 成功与失败的文件数
     */
    suspend fun importFiles(uris: List<Uri>): AssistantImportResult = withContext(Dispatchers.IO) {
        var imported = 0
        var failed = 0
        for (uri in uris) {
            // 单个文件读取失败不影响其余文件
            val copied = runCatching {
                val name = safeFileName(queryDisplayName(uri))
                val target = uniqueFile(name)
                mContext.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (copied) imported++ else failed++
        }
        AssistantImportResult(importedCount = imported, failedCount = failed)
    }

    /** 查询 SAF 文件的显示名，缺失时退回 URI 末段。 */
    private fun queryDisplayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        runCatching {
            mContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index) ?: ""
                }
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    /** 只保留安全字符，避免文件名里带路径分隔符或非法符号。 */
    private fun safeFileName(raw: String): String {
        val cleaned = raw.trim().map { char ->
            if (char.isLetterOrDigit() && (char.code < 128) || char in "-_. ") char else '_'
        }.joinToString("").trim().trim('.')
        return cleaned.ifBlank { "imported.txt" }.take(MAX_FILE_NAME_LENGTH)
    }

    /** 同名文件追加序号，保证不覆盖沙箱里已有内容。 */
    private fun uniqueFile(name: String): File {
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var candidate = File(sandboxRoot, name)
        var index = 1
        while (candidate.exists()) {
            candidate = File(sandboxRoot, "$base-$index$extension")
            index++
        }
        return candidate
    }

    /** 清空会话文件；沙箱文件不受影响。 */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mSessionFile.delete()
        Unit
    }

    /** 沙箱内的文件数量与占用，供页面展示权限范围。 */
    /**
     * 清空沙箱。
     *
     * 只删子项、保留根目录：根目录被删掉后工具会立刻失效，需要重启才能恢复。
     */
    suspend fun clearSandbox(): Int = withContext(Dispatchers.IO) {
        val children = sandboxRoot.listFiles().orEmpty()
        var removed = 0
        children.forEach { child ->
            if (runCatching { child.deleteRecursively() }.getOrDefault(false)) removed++
        }
        removed
    }


    /**
     * 列出沙箱内的文件，供界面浏览。
     *
     * 只返回文件不返回目录层级，路径相对沙箱根，界面直接展示。
     */
    fun listSandboxFiles(): List<AssistantSandboxFile> {
        val rootPath = sandboxRoot.absolutePath
        return sandboxRoot.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                AssistantSandboxFile(
                    path = file.absolutePath.removePrefix("$rootPath/"),
                    size = file.length()
                )
            }
            .sortedBy { it.path }
            .toList()
    }

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
    val toolName: String? = null,
    /**
     * 随这条消息附上的文件。
     *
     * 文本会同步复制进沙箱供工具读写，但对话里仍留一份可见记录，
     * 否则用户发完文件就再也看不见自己发过什么。
     */
    val attachments: List<AssistantAttachment>? = null
)

/** 一次沙箱导入的结果统计。 */
data class AssistantImportResult(
    /** 成功复制进沙箱的文件数。 */
    val importedCount: Int,
    /** 读取或写入失败的文件数。 */
    val failedCount: Int
)

/** 沙箱内文件名允许的最大长度。 */
private const val MAX_FILE_NAME_LENGTH = 96

/** 沙箱内的一个文件，供浏览界面展示。 */
data class AssistantSandboxFile(
    /** 相对沙箱根的路径。 */
    val path: String,
    /** 字节数。 */
    val size: Long
)
