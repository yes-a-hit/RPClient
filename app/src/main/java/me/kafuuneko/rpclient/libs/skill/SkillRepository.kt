package me.kafuuneko.rpclient.libs.skill

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.AppModel

/**
 * Skill 能力包的本地存储与激活判定。
 *
 * 每个 Skill 以独立 JSON 文件保存在应用私有目录下，因此导入、导出、备份都只需处理纯文本，
 * 不需要数据库迁移；文件损坏或字段缺失时按空结果处理，不影响聊天主流程。
 *
 * @param mContext 用于定位应用私有目录
 */
class SkillRepository(private val mContext: Context) {
    private val mDirectory: File by lazy {
        File(mContext.filesDir, SKILL_DIRECTORY).apply { mkdirs() }
    }

    /**
     * 读取全部已安装 Skill。
     *
     * @return 按名称排序的 Skill 列表；单个文件损坏时跳过该文件
     */
    suspend fun listSkills(): List<SkillManifest> = withContext(Dispatchers.IO) {
        ensureStarterSkill()
        readAll().sortedBy { it.name.lowercase() }
    }

    /**
     * 保存或更新一个 Skill。
     *
     * @param skill 待保存内容；[SkillManifest.id] 为空时自动生成
     * @return 实际落盘的 Skill（含生成的 ID）
     */
    suspend fun save(skill: SkillManifest): SkillManifest = withContext(Dispatchers.IO) {
        val id = skill.id.ifBlank { generateId(skill.name) }
        val stored = skill.copy(id = id, name = skill.name.trim())
        fileFor(id).writeText(SkillCodec.encode(stored))
        stored
    }

    /** 删除指定 Skill；文件不存在时静默返回。 */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        if (id.isNotBlank()) fileFor(id).delete()
        Unit
    }

    /** 切换启用状态；ID 不存在时不做任何改动。 */
    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = readAll().firstOrNull { it.id == id } ?: return@withContext
        fileFor(id).writeText(SkillCodec.encode(current.copy(enabled = enabled)))
    }

    /**
     * 导入用户提供的 Skill 文本。
     *
     * 同 ID 的 Skill 会被覆盖，因此重复粘贴同一份内容不会产生副本。
     *
     * @param raw 用户粘贴的 JSON，可包含一个或多个 Skill
     * @return 成功写入的 Skill 列表；无法解析时为空
     */
    suspend fun importSkills(raw: String): List<SkillManifest> = withContext(Dispatchers.IO) {
        val parsed = SkillCodec.decode(raw)
        if (parsed.isEmpty()) return@withContext emptyList()
        val existing = readAll().associateBy { it.id }
        parsed.map { skill ->
            // 同 ID 覆盖时保留用户此前设置的启用状态
            val merged = skill.copy(
                id = skill.id.ifBlank { generateId(skill.name) },
                enabled = existing[skill.id]?.enabled ?: skill.enabled
            )
            fileFor(merged.id).writeText(SkillCodec.encode(merged))
            merged
        }
    }

    /** 导出全部 Skill，便于分享给其他设备。 */
    suspend fun exportAll(): String = withContext(Dispatchers.IO) {
        SkillCodec.encodeAll(readAll())
    }

    /**
     * 从 SAF 选中的文件导入。
     *
     * 每个文件独立解析，单个文件损坏不影响其余文件；
     * 返回的成功数量按实际写入的 Skill 条数统计，而非文件数。
     *
     * @param uris 用户选中的文件，可多选
     * @return 导入结果统计
     */
    suspend fun importFromUris(uris: List<Uri>): SkillImportOutcome = withContext(Dispatchers.IO) {
        var imported = 0
        var failed = 0
        for (uri in uris) {
            // 读取失败的单个文件计入失败数后继续处理下一个
            val text = runCatching {
                mContext.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                failed++
                continue
            }
            val parsed = runCatching { importSkills(text) }.getOrElse { emptyList() }
            if (parsed.isEmpty()) failed++ else imported += parsed.size
        }
        SkillImportOutcome(importedCount = imported, failedFileCount = failed)
    }

    /**
     * 从 SAF 授权的目录树递归导入。
     *
     * 只收集 `.json` 文件，跳过子目录以外的其他类型；目录可以是用户从「文档」里授权的任意位置。
     *
     * @param treeUri 目录选择器返回的树 URI
     * @return 导入结果统计
     */
    suspend fun importFromTree(treeUri: Uri): SkillImportOutcome = withContext(Dispatchers.IO) {
        val documents = collectJsonDocuments(treeUri)
        if (documents.isEmpty()) return@withContext SkillImportOutcome(0, 0)
        importFromUris(documents)
    }

    /** 递归收集目录树下的 JSON 文档；读取失败的子目录直接跳过。 */
    private fun collectJsonDocuments(treeUri: Uri): List<Uri> {
        val documentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return emptyList()
        val result = mutableListOf<Uri>()
        val pending = ArrayDeque<String>()
        pending.addLast(documentId)
        // 广度遍历整棵树，深度异常时由队列自然收敛
        while (pending.isNotEmpty()) {
            val parentId = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            runCatching {
                mContext.contentResolver.query(childrenUri, projection, null, null, null)
                    ?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val childId = cursor.getString(0)
                            val name = cursor.getString(1) ?: ""
                            val mime = cursor.getString(2) ?: ""
                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                pending.addLast(childId)
                            } else if (name.endsWith(FILE_SUFFIX, ignoreCase = true)) {
                                result += DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            }
                        }
                    }
            }
        }
        return result
    }

    /**
     * 按最近聊天内容判定本轮激活的 Skill。
     *
     * 判定规则：已启用，且（常驻 或 触发词在最近文本中出现）。
     *
     * @param recentText 最近若干条消息拼接而成的扫描文本
     * @return 需要注入 Prompt 的 Skill 列表
     */
    suspend fun resolveActive(recentText: String): List<SkillManifest> = withContext(Dispatchers.IO) {
        ensureStarterSkill()
        readAll().filter { it.enabled && it.isUsable && it.matches(recentText) }
    }

    /**
     * 首次使用时写入一个示例 Skill。
     *
     * 只在偏好标记未置位时执行，因此用户删除示例后不会被重新创建；
     * 示例内容使用与内置提示词一致的英文，避免与全局 Prompt 语言混排。
     */
    private fun ensureStarterSkill() {
        if (AppModel.skillStarterInitialized) return
        runCatching {
            if (readAll().isEmpty()) {
                fileFor(STARTER_SKILL_ID).writeText(SkillCodec.encode(STARTER_SKILL))
            }
        }
        AppModel.skillStarterInitialized = true
    }

    /** 读取目录下全部可用 Skill；解析失败的文件直接跳过。 */
    private fun readAll(): List<SkillManifest> {
        val files = mDirectory.listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
            ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching { SkillCodec.decode(file.readText()).firstOrNull() }.getOrNull()
        }
    }

    /** 定位 Skill 文件；文件名只由安全字符组成，避免路径穿越。 */
    private fun fileFor(id: String): File = File(mDirectory, "${sanitizeId(id)}$FILE_SUFFIX")

    /** 由名称派生稳定且可读的文件名。 */
    private fun generateId(name: String): String {
        val base = sanitizeId(name).ifBlank { DEFAULT_ID_PREFIX }
        return "$base-${UUID.randomUUID().toString().take(8)}"
    }

    /** 只保留小写字母、数字、连字符与下划线。 */
    private fun sanitizeId(value: String): String =
        value.trim().lowercase().map { char ->
            if (char.isLetterOrDigit() && char.code < 128 || char == '-' || char == '_') char else '-'
        }.joinToString("").trim('-').take(MAX_ID_LENGTH)

    private companion object {
        const val SKILL_DIRECTORY = "skills"
        const val FILE_SUFFIX = ".json"
        const val DEFAULT_ID_PREFIX = "skill"
        const val MAX_ID_LENGTH = 48
        const val STARTER_SKILL_ID = "starter-dice-check"

        /** 内置示例：掷骰判定，演示「触发词 → 流程化输出」的完整链路。 */
        val STARTER_SKILL = SkillManifest(
            id = STARTER_SKILL_ID,
            name = "Dice check",
            description = "Resolve uncertain actions with a d20 roll and narrate the outcome.",
            version = "1.0",
            triggers = listOf("dice", "roll", "骰", "掷骰", "判定"),
            alwaysOn = false,
            enabled = true,
            instructions = """
When the scene needs an uncertain outcome, resolve it with a single d20 check.
- State the difficulty (easy 10, normal 15, hard 20) before rolling.
- Roll one d20, add the most fitting modifier from the character sheet, and compare.
- Narrate the result through the character's senses instead of printing raw numbers only.
- Keep the roll inside the story: no tables, no meta commentary, no asking the user to roll.
""".trimIndent()
        )
    }
}

/** 判断单个 Skill 是否应在本轮激活。 */
private fun SkillManifest.matches(recentText: String): Boolean {
    if (alwaysOn) return true
    if (recentText.isBlank()) return false
    return triggers.any { trigger ->
        trigger.isNotBlank() && recentText.contains(trigger, ignoreCase = true)
    }
}

/** 一次 Skill 导入的结果统计。 */
data class SkillImportOutcome(
    /** 实际写入的 Skill 条数。 */
    val importedCount: Int,
    /** 解析或读取失败的文件数。 */
    val failedFileCount: Int
)