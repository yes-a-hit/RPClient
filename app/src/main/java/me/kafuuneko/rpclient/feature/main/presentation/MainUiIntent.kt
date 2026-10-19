package me.kafuuneko.rpclient.feature.main.presentation

import android.net.Uri
import me.kafuuneko.rpclient.feature.main.model.ImageSendField
import me.kafuuneko.rpclient.feature.main.model.MainGenerationParameter
import me.kafuuneko.rpclient.feature.main.model.MainHomeContentTab
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemSelection
import me.kafuuneko.rpclient.feature.main.model.Route
import me.kafuuneko.rpclient.libs.imagegen.ImageGenAspect
import me.kafuuneko.rpclient.libs.media.ImageSendMode
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole
import me.kafuuneko.rpclient.libs.theme.AppThemeMode

/** 首页及全局设置页可接收的全部用户意图。 */
sealed class MainUiIntent {
    data class Init(val route: Route = Route.Main) : MainUiIntent()

    /** 打开助手会话页。 */
    data object OpenAssistant : MainUiIntent()

    /** 打开剧本画布。 */
    data object OpenScriptEditor : MainUiIntent()

    /** 打开剧情冒险：开新局或继续存档。 */
    data object OpenAdventure : MainUiIntent()

    /** 打开剧本库。 */
    data object OpenScriptLibrary : MainUiIntent()

    /** 打开场景总览。 */
    data object OpenScriptScene : MainUiIntent()

    data object Resume : MainUiIntent()

    data object Back : MainUiIntent()

    data class SelectImageSendMode(val mode: ImageSendMode) : MainUiIntent()

    data class ChangeImageSendLimit(val field: ImageSendField, val value: String) : MainUiIntent()

    data object SaveImageSendLimits : MainUiIntent()

    /** 修改外接生图服务地址。 */
    data class ChangeImageGenBaseUrl(val value: String) : MainUiIntent()

    /** 修改外接生图服务 API Key。 */
    data class ChangeImageGenApiKey(val value: String) : MainUiIntent()

    /** 修改外接生图服务模型名。 */
    data class ChangeImageGenModel(val value: String) : MainUiIntent()

    /** 修改外接生图默认画幅。 */
    data class SelectImageGenAspect(val aspect: ImageGenAspect) : MainUiIntent()

    /** 启用或停用某个 Skill。 */
    data class ToggleSkill(val id: String, val enabled: Boolean) : MainUiIntent()

    /** 删除某个 Skill。 */
    data class DeleteSkill(val id: String) : MainUiIntent()

    /** 打开 Skill 导入对话框。 */
    data object OpenSkillImport : MainUiIntent()

    /** 修改导入对话框中的 JSON 草稿。 */
    data class ChangeSkillImportDraft(val value: String) : MainUiIntent()

    /** 提交导入草稿。 */
    data object ConfirmSkillImport : MainUiIntent()

    data class SelectPage(val page: MainPage) : MainUiIntent()

    data class SelectThemeMode(val themeMode: AppThemeMode) : MainUiIntent()

    data class SelectHomeContentTab(val tab: MainHomeContentTab) : MainUiIntent()

    data class OpenChat(val sessionId: String) : MainUiIntent()

    data object OpenCreateChat : MainUiIntent()

    data class OpenGroupChat(val sessionId: String) : MainUiIntent()

    data object OpenCreateGroupChat : MainUiIntent()

    data class OpenStory(val storyId: Long) : MainUiIntent()

    data object OpenCreateStory : MainUiIntent()

    data class ShowRenameItemDialog(val item: MainHomeItemSelection) : MainUiIntent()

    data class ChangeItemTitleDraft(val value: String) : MainUiIntent()

    data object ConfirmItemRename : MainUiIntent()

    data object OpenCharacterManager : MainUiIntent()

    data object OpenWorldBookManager : MainUiIntent()

    data object OpenProviderManager : MainUiIntent()

    data object OpenSelectedProviderEdit : MainUiIntent()

    data class ShowGenerationParameterDialog(
        val parameter: MainGenerationParameter
    ) : MainUiIntent()

    data class ChangeGenerationParameterDraft(val value: String) : MainUiIntent()

    data object ConfirmGenerationParameter : MainUiIntent()

    data object PickUserAvatarClick : MainUiIntent()

    data class UserAvatarCropped(val fileUuid: String) : MainUiIntent()

    data object ClearUserAvatar : MainUiIntent()

    /** 请求打开系统图片选择器挑选聊天背景图。 */
    data object PickChatBackgroundClick : MainUiIntent()

    /** 用户已选定背景图，携带系统返回的图片 URI 交给数据层保存。 */
    data class ChatBackgroundPicked(val uri: Uri) : MainUiIntent()

    /** 清除聊天背景并删除不再被引用的图片文件。 */
    data object ClearChatBackground : MainUiIntent()

    /** 调整背景遮罩不透明度，[percent] 取值 0-100。 */
    data class SetChatBackgroundDim(val percent: Int) : MainUiIntent()

    data object ImportChatClick : MainUiIntent()

    data class ImportChatResult(val uri: Uri) : MainUiIntent()

    data class ChangeImportCharacterQuery(val value: String) : MainUiIntent()

    data class SelectImportCharacter(val characterId: Long) : MainUiIntent()

    data object ChooseChatImageDirectory : MainUiIntent()

    data class ChatImageDirectoryResult(val uri: Uri?) : MainUiIntent()

    data object ConfirmImportChat : MainUiIntent()

    data class ChangeUserName(val value: String) : MainUiIntent()

    data class ChangeUserDescription(val value: String) : MainUiIntent()

    data object ShowUserDescriptionEditor : MainUiIntent()

    data class ChangeUserDescriptionEditorDraft(val value: String) : MainUiIntent()

    data object ConfirmUserDescriptionEditor : MainUiIntent()

    data class SelectProvider(val providerId: Long) : MainUiIntent()

    data class ToggleStreamEnabled(val enabled: Boolean) : MainUiIntent()

    data class SelectPostProcessingMode(val mode: PromptPostProcessingMode) : MainUiIntent()

    data class SelectExampleDialogueBehavior(
        val behavior: ExampleDialogueBehavior
    ) : MainUiIntent()

    data class ToggleIncludeThinkInContext(val enabled: Boolean) : MainUiIntent()

    data class ChangeMaxPromptHistoryMessages(val value: String) : MainUiIntent()

    data class ChangeWorldInfoBudgetPercent(val value: Int) : MainUiIntent()

    data class ChangeWorldInfoBudgetCap(val value: String) : MainUiIntent()

    data class ToggleWorldInfoOverflowAlert(val enabled: Boolean) : MainUiIntent()

    data class ToggleContextTrimmingAlert(val enabled: Boolean) : MainUiIntent()

    data class ToggleDebugModeEnabled(val enabled: Boolean) : MainUiIntent()

    data class ToggleAutoSummaryEnabled(val enabled: Boolean) : MainUiIntent()

    data class SelectSummaryProvider(val providerId: Long) : MainUiIntent()

    data class ChangeSummaryTriggerMessageCount(val value: String) : MainUiIntent()

    data class ChangeSummaryWordsLimit(val value: String) : MainUiIntent()

    data class ChangeSummaryMaxMessagesPerRequest(val value: String) : MainUiIntent()

    data class ChangeSummaryResponseTokens(val value: String) : MainUiIntent()

    data class SelectSummarySettingsTab(
        val tab: MainSummarySettingsTab
    ) : MainUiIntent()

    data class SelectSummaryInjectionPosition(
        val position: SummaryInjectionPosition
    ) : MainUiIntent()

    data class ChangeSummaryInjectionDepth(val value: String) : MainUiIntent()

    data class SelectSummaryInjectionRole(
        val role: SummaryInjectionRole
    ) : MainUiIntent()

    data object OpenPromptPreset : MainUiIntent()

    data object OpenRegexScripts : MainUiIntent()

    data object OpenRequestLogs : MainUiIntent()

    data object OpenTokenUsage : MainUiIntent()

    data object OpenAbout : MainUiIntent()

    data class EnterMultiSelect(val item: MainHomeItemSelection) : MainUiIntent()

    data class ToggleItemSelection(val item: MainHomeItemSelection) : MainUiIntent()

    data class ToggleSessionGroup(val characterId: String) : MainUiIntent()

    data object ExitMultiSelect : MainUiIntent()

    data object ShowDeleteSelectedDialog : MainUiIntent()

    data object ConfirmDeleteSelected : MainUiIntent()

    data object DismissDialog : MainUiIntent()

    /** 打开 Skill 文件选择器。 */
    data object PickSkillFiles : MainUiIntent()

    /** 打开 Skill 目录选择器。 */
    data object PickSkillFolder : MainUiIntent()

    /** 用户选中了若干 Skill 文件。 */
    data class SkillFilesPicked(val uris: List<Uri>) : MainUiIntent()

    /** 用户选中了包含 Skill 文件的目录。 */
    data class SkillFolderPicked(val treeUri: Uri) : MainUiIntent()
}
