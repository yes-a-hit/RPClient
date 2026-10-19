package me.kafuuneko.rpclient.feature.storycreate.presentation

import me.kafuuneko.rpclient.feature.storycreate.model.ExtractionUiState
import me.kafuuneko.rpclient.feature.storycreate.model.NovelImportPreview
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateCharacterItem
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateForm
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateLorebookGroupItem
import me.kafuuneko.rpclient.feature.storycreate.model.StoryImportState

/** 新建 Story 页面状态树。 */
sealed class StoryCreateUiState {
    data object None : StoryCreateUiState()

    data class Normal(
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: StoryCreateLoadState = StoryCreateLoadState.Loading,
        /** 当前页面正在编辑的表单数据。 */
        val form: StoryCreateForm = StoryCreateForm(),
        /** 当前页面或流程可使用的角色列表。 */
        val characters: List<StoryCreateCharacterItem> = emptyList(),
        /** 角色列表当前使用的搜索关键词。 */
        val characterQuery: String = "",
        /** 按搜索条件过滤后实际展示的角色列表。 */
        val visibleCharacters: List<StoryCreateCharacterItem> = characters,
        /** 世界书列表当前使用的搜索关键词。 */
        val lorebookQuery: String = "",
        /** 按世界书分组后的条目列表。 */
        val lorebookGroups: List<StoryCreateLorebookGroupItem> = emptyList(),
        /** 按搜索条件过滤后实际展示的世界书分组。 */
        val visibleLorebookGroups: List<StoryCreateLorebookGroupItem> = lorebookGroups,
        /** 本地小说导入的阶段性状态。 */
        val importState: StoryImportState = StoryImportState.Idle,
        /** 已解析的小说预览；为空时按普通方式创建故事。 */
        val importPreview: NovelImportPreview? = null,
        /** 角色与世界书提取的展示状态。 */
        val extraction: ExtractionUiState = ExtractionUiState()
    ) : StoryCreateUiState() {
        val selectedCharacterCount: Int
            get() = form.selectedCharacterIds.size
    }

    data class Finished(val previous: StoryCreateUiState) : StoryCreateUiState()

    companion object {
        fun finished(previous: StoryCreateUiState): StoryCreateUiState {
            return previous as? Finished ?: Finished(previous)
        }
    }
}

/** 新建 Story 页面的加载和提交状态。 */
sealed class StoryCreateLoadState {
    data object Loading : StoryCreateLoadState()
    data object Ready : StoryCreateLoadState()
    data object Creating : StoryCreateLoadState()
}
