package me.kafuuneko.rpclient.feature.storycreate.presentation

import android.net.Uri
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateCharacterActivationMode

/** 新建 Story 页面的表单操作和提交意图。 */
sealed class StoryCreateUiIntent {
    data object Init : StoryCreateUiIntent()
    data object Back : StoryCreateUiIntent()
    data class ChangeTitle(val value: String) : StoryCreateUiIntent()
    data class SetIncludeUserPersona(val enabled: Boolean) : StoryCreateUiIntent()
    data class ChangeCharacterQuery(val value: String) : StoryCreateUiIntent()
    data class ToggleCharacter(val characterId: Long) : StoryCreateUiIntent()
    data class SetCharacterActivationMode(
        val characterId: Long,
        val activationMode: StoryCreateCharacterActivationMode
    ) : StoryCreateUiIntent()
    data class ChangeLorebookQuery(val value: String) : StoryCreateUiIntent()
    data class ToggleLorebook(val lorebookId: Long) : StoryCreateUiIntent()
    data class ToggleLorebookEntry(val entryId: Long) : StoryCreateUiIntent()

    /** 用户选中了本地小说文件，开始读取并切分。 */
    data class NovelFilesPicked(val uris: List<Uri>) : StoryCreateUiIntent()

    /** 放弃已解析的小说，回到普通创建流程。 */
    data object ClearImportedNovel : StoryCreateUiIntent()

    /** 调整导入章节的顺序：delta 为 -1 上移、+1 下移。 */
    data class MoveImportedChapter(val index: Int, val delta: Int) : StoryCreateUiIntent()

    /** 调用模型从正文提取角色卡与世界书。 */
    data object ExtractSettingsClick : StoryCreateUiIntent()

    data object CreateStory : StoryCreateUiIntent()
}
