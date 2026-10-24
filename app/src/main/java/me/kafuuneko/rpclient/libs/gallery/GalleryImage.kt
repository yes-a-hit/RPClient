package me.kafuuneko.rpclient.libs.gallery

/**
 * 图库里的一张图片。
 *
 * 标签是给模型看的检索入口，角色绑定用于「只在某个角色在场时才该发」的场合，
 * 公开标记则允许模型在任何对话里使用。
 */
data class GalleryImage(
    /** 稳定 ID，文件名为 `<id>.json`。 */
    val id: String,
    /** 展示名，也用于模型点名。 */
    val name: String,
    /** 图片在应用文件库里的 UUID。 */
    val fileUuid: String,
    /** 检索标签，例如「开心」「meme」「海」「外貌」。 */
    val labels: List<String> = emptyList(),
    /** 绑定的角色名；为空表示不限角色。 */
    val characterName: String = "",
    /** 是否公开：公开图任何对话都可用，非公开只对绑定角色生效。 */
    val isPublic: Boolean = true,
    /** 模型可读的一句话说明，描述这张图适合什么时候发。 */
    val description: String = "",
    /** 加入时间戳，用于排序。 */
    val addedAt: Long = 0L,
    /**
     * 是否是角色本人的外观参考。
     *
     * 和「能不能发」是两件事：普通图是模型可以拿去发的东西，
     * 外观图是**角色自己长什么样**——它要进 Prompt 当视觉锚点，
     * 让模型认得出用户发来的 meme 里那个形象就是自己。
     */
    val isAppearance: Boolean = false,
    /** 外观图在角色身上的含义，例如「立绘」「被摸头时的表情」。 */
    val appearanceNote: String = ""
) {
    /** 供模型检索的一句话摘要。 */
    fun toBrief(): String = buildString {
        append(name)
        if (labels.isNotEmpty()) append("｜标签：").append(labels.joinToString("、"))
        if (characterName.isNotBlank()) append("｜角色：").append(characterName)
        if (!isPublic) append("｜私用")
        if (description.isNotBlank()) append("｜").append(description)
    }
}
