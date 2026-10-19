package me.kafuuneko.rpclient.libs.story

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.repository.StoryCharacterSelection
import me.kafuuneko.rpclient.libs.room.repository.StoryLorebookEntrySelection
import me.kafuuneko.rpclient.libs.room.repository.StoryRepository

/**
 * 本地小说导入仓库。
 *
 * 承接 SAF 选中的文件与故事数据层之间的转换：
 * - 读取文本并按 [NovelChapterParser] 切章，整本与散装多文件共用同一条路径。
 * - 多文件按文件名自然排序，缺少标题的单文件回退成文件名当章节名。
 * - 建故事时复用 StoryRepository 的事务接口，先建骨架再逐章写入正文。
 */
class NovelImportRepository(
    private val mContext: Context,
    private val mStoryRepository: StoryRepository
) {
    /**
     * 读取并切分选中的文件。
     *
     * @param uris SAF 返回的文件列表，允许只选一个
     * @return 合并后的章节列表；单文件内部已有标题时不再叠加文件名
     */
    suspend fun readChapters(uris: List<Uri>): List<NovelChapter> = withContext(Dispatchers.IO) {
        val files = uris.mapNotNull { uri ->
            val name = queryDisplayName(uri)
            val text = readText(uri)
            if (text.isBlank()) null else name to text
        }.sortedBy { (name, _) -> naturalKey(name) }

        val result = mutableListOf<NovelChapter>()
        for ((name, text) in files) {
            val parsed = NovelChapterParser.parse(text)
            val fallbackTitle = name.substringBeforeLast('.').trim()
            if (parsed.isEmpty()) continue
            // 单文件只解析出一段且没有标题时，用文件名兜底，避免整章无名
            if (parsed.size == 1 && parsed.first().title.isBlank() && fallbackTitle.isNotBlank()) {
                result += parsed.first().copy(title = fallbackTitle)
            } else {
                result += parsed
            }
        }
        result
    }

    /**
     * 用导入的章节新建故事。
     *
     * @param title 故事标题
     * @param chapters 已切分的章节，至少一项
     * @param lorebookSelections 创建时一并关联的世界书条目
     * @param characterSelections 创建时一并关联的参演角色
     * @return 新故事 ID；章节为空时返回 0
     */
    suspend fun createStoryFromNovel(
        title: String,
        chapters: List<NovelChapter>,
        lorebookSelections: List<StoryLorebookEntrySelection> = emptyList(),
        characterSelections: List<StoryCharacterSelection> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        if (chapters.isEmpty()) return@withContext 0L
        val first = chapters.first()
        val firstTitle = first.title.ifBlank { DEFAULT_CHAPTER_TITLE }
        // 先用首章标题建骨架，事务内会同时生成第一个章节
        val storyId = mStoryRepository.createStoryWithConfiguration(
            title = title,
            lorebookSelections = lorebookSelections,
            characterSelections = characterSelections,
            initialChapterTitle = firstTitle
        )
            val editor = mStoryRepository.getStoryEditorData(storyId) ?: return@withContext storyId

        // 首章写入既有章节，剩余章节逐条新建后写入正文
        val head = editor.currentChapter
        mStoryRepository.updateChapterDraft(
            storyId = storyId,
            chapterId = head.id,
            expectedChapterRevision = head.contentRevision,
            content = first.content,
            continuationGuidance = ""
        )
        chapters.drop(1).forEach { chapter ->
            val chapterId = mStoryRepository.createChapter(
                storyId = storyId,
                volumeId = null,
                title = chapter.title.ifBlank { DEFAULT_CHAPTER_TITLE }
            )
            mStoryRepository.updateChapterDraft(
                storyId = storyId,
                chapterId = chapterId,
                expectedChapterRevision = 0L,
                content = chapter.content,
                continuationGuidance = ""
            )
        }
            storyId
        }

    /** 读取文本内容；UTF-8 出现大量替换字符时回退到 GB18030。 */
    private fun readText(uri: Uri): String {
        val bytes = runCatching {
            mContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return ""
        val utf8 = bytes.toString(Charsets.UTF_8)
        val broken = utf8.count { it == '\uFFFD' }
        // 替换字符超过千分之一视为编码不符，按中文常见编码重试
        return if (broken > 0 && broken * 1000 > utf8.length) {
            runCatching { bytes.toString(Charset.forName("GB18030")) }.getOrDefault(utf8)
        } else {
            utf8
        }
    }

    /** 查询 SAF 文件的显示名，用于章节兜底标题与排序。 */
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

    /** 文件名自然排序键：数字段补零，保证 chapter2 排在 chapter10 之前。 */
    private fun naturalKey(name: String): String = name.lowercase().replace(Regex("\\d+")) { match ->
        match.value.padStart(8, '0')
    }

    private companion object {
        /** 导入章节缺少标题时使用的占位章节名。 */
        const val DEFAULT_CHAPTER_TITLE = "正文"
    }
}
