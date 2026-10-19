package me.kafuuneko.rpclient.feature.storyeditor.model

/**
 * 章节重排算法。
 *
 * 只提供确定性规则，保证同一批数据每次重排结果一致；
 * 采用哪种由用户在排序对话框里选择。
 */
enum class ChapterSortAlgorithm {
    /** 识别标题里的序号（中文数字、阿拉伯数字、小数），按数值升序。 */
    ByIndex,

    /** 按标题文本升序，适合没有序号的命名。 */
    ByTitle,

    /** 直接反转当前顺序，用于修掉导入时反了的列表。 */
    Reverse
}
