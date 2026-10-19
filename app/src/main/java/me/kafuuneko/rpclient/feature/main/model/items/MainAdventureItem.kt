package me.kafuuneko.rpclient.feature.main.model.items

/**
 * 首页「最近内容」里的一条剧情冒险。
 *
 * 只带定位一局所需的信息：剧本与存档 ID，
 * 点击后直接进入那一局，不需要先经过剧本库。
 */
data class MainAdventureItem(
    /** 存档 ID。 */
    val playId: String,
    /** 所属剧本 ID。 */
    val packId: String,
    /** 剧本名称，作为卡片标题。 */
    val title: String,
    /** 进度或结束原因的预览文本。 */
    val preview: String,
    /** 最后一轮是否已结束。 */
    override val latestTime: Long
) : MainHomeContentItem
