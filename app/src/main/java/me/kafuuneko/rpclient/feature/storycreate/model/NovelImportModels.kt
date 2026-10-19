package me.kafuuneko.rpclient.feature.storycreate.model

import me.kafuuneko.rpclient.libs.story.NovelChapter

/**
 * 待导入小说的预览状态。
 *
 * 只保留展示所需的统计与章节标题，正文仍留在内存里的 [chapters] 中，
 * 避免把整本正文塞进 UiState 造成状态快照过大。
 */
data class NovelImportPreview(
    /** 首个被选中的文件名，用于告知用户来源。 */
    val sourceName: String,
    /** 选中文件的总数。 */
    val fileCount: Int,
    /** 合并后的章节数量。 */
    val chapterCount: Int,
    /** 正文字符总数。 */
    val characterCount: Int,
    /** 章节标题预览，最多取前若干条。 */
    val chapterTitles: List<String>,
    /** 解析后的完整章节，仅在创建故事时使用。 */
    val chapters: List<NovelChapter>
) {
    /**
     * 用新的章节顺序重建预览。
     *
     * 标题列表与统计都由 [chapters] 派生，避免两处数据不同步。
     */
    fun reordered(newChapters: List<NovelChapter>): NovelImportPreview = copy(
        chapterCount = newChapters.size,
        characterCount = newChapters.sumOf { it.content.length },
        chapterTitles = newChapters.mapIndexed { index, chapter ->
            chapter.title.ifBlank { "#${index + 1}" }
        },
        chapters = newChapters
    )
}

/** 本地小说导入的阶段性状态。 */
enum class StoryImportState {
    /** 未发起导入。 */
    Idle,

    /** 正在读取并切分文件。 */
    Reading,

    /** 解析完成，可创建故事。 */
    Ready
}

/**
 * 设定提取的展示状态。
 *
 * 提取是一次会消耗额度的模型调用，因此运行态与结果态都要明确展示，
 * 失败时只标记失败，不保留半成品计数。
 */
data class ExtractionUiState(
    val running: Boolean = false,
    val failed: Boolean = false,
    val characterCount: Int = 0,
    val entryCount: Int = 0,
    val worldbookName: String = ""
) {
    /** 是否已产生可用结果。 */
    val hasResult: Boolean
        get() = characterCount > 0 || entryCount > 0
}
