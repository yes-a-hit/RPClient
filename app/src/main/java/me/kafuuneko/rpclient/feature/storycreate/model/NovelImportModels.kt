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
)

/** 本地小说导入的阶段性状态。 */
enum class StoryImportState {
    /** 未发起导入。 */
    Idle,

    /** 正在读取并切分文件。 */
    Reading,

    /** 解析完成，可创建故事。 */
    Ready
}
