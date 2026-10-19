package me.kafuuneko.rpclient.feature.characteredit.presentation

import android.net.Uri
import me.kafuuneko.rpclient.libs.character.CharacterProfileFlagField
import me.kafuuneko.rpclient.libs.character.CharacterProfileTextField
import me.kafuuneko.rpclient.libs.character.RelationshipStatus

/** 角色创建/编辑页面的字段变更、文件选择、保存与删除意图。 */
sealed class CharacterEditUiIntent {
    data class Init(val characterId: Long?) : CharacterEditUiIntent()

    data object Resume : CharacterEditUiIntent()

    data object Back : CharacterEditUiIntent()

    data object PickAvatarClick : CharacterEditUiIntent()

    data class AvatarCropped(val fileUuid: String) : CharacterEditUiIntent()

    data class UpdateCharacterLorebook(val lorebookId: Long) : CharacterEditUiIntent()

    data class SelectLLMProvider(val providerId: Long) : CharacterEditUiIntent()

    data object OpenWorldBookManager : CharacterEditUiIntent()

    data class ChangeName(val value: String) : CharacterEditUiIntent()

    data class SetTags(val tags: List<String>) : CharacterEditUiIntent()

    data class ChangeDescription(val value: String) : CharacterEditUiIntent()

    data class ChangeCreatorNotes(val value: String) : CharacterEditUiIntent()

    data class ChangeCreator(val value: String) : CharacterEditUiIntent()

    data class ChangeCharacterVersion(val value: String) : CharacterEditUiIntent()

    data class ChangePersonality(val value: String) : CharacterEditUiIntent()

    data class ChangeScenario(val value: String) : CharacterEditUiIntent()

    data object AddFirstMessage : CharacterEditUiIntent()

    data class ChangeFirstMessage(val index: Int, val value: String) : CharacterEditUiIntent()

    data class DeleteFirstMessage(val index: Int) : CharacterEditUiIntent()

    data class ChangeExamplesOfDialogue(val value: String) : CharacterEditUiIntent()

    data class ChangePostHistoryInstructions(val value: String) : CharacterEditUiIntent()

    data class ChangeSystemPrompt(val value: String) : CharacterEditUiIntent()

    data class ChangeDepthPromptPrompt(val value: String) : CharacterEditUiIntent()

    data class ChangeDepthPromptDepth(val value: String) : CharacterEditUiIntent()

    data class ChangeDepthPromptRole(val value: String) : CharacterEditUiIntent()

    data object AddAlternateGreeting : CharacterEditUiIntent()

    data class ChangeAlternateGreeting(val index: Int, val value: String) : CharacterEditUiIntent()

    data class DeleteAlternateGreeting(val index: Int) : CharacterEditUiIntent()

    data class ChangeExtensionsJson(val value: String) : CharacterEditUiIntent()

    /** 修改扩展档案中的文本字段（身份、感情史、边界等）。 */
    data class ChangeProfileText(
        val field: CharacterProfileTextField,
        val value: String
    ) : CharacterEditUiIntent()

    /** 修改扩展档案中的三态布尔字段；null 表示回到未设定。 */
    data class ChangeProfileFlag(
        val field: CharacterProfileFlagField,
        val value: Boolean?
    ) : CharacterEditUiIntent()

    /** 修改角色当前感情状态。 */
    data class ChangeRelationshipStatus(
        val status: RelationshipStatus
    ) : CharacterEditUiIntent()

    /** 打开角色卡生成向导。 */
    data object OpenCharacterWizard : CharacterEditUiIntent()

    /** 关闭向导并丢弃未应用的草稿。 */
    data object CloseCharacterWizard : CharacterEditUiIntent()

    /** 修改向导中的角色名种子。 */
    data class ChangeWizardName(val value: String) : CharacterEditUiIntent()

    /** 修改向导中的角色概念描述。 */
    data class ChangeWizardConcept(val value: String) : CharacterEditUiIntent()

    /** 修改向导中的额外要求。 */
    data class ChangeWizardRequirements(val value: String) : CharacterEditUiIntent()

    /** 切换是否同时生成世界书。 */
    data class ToggleWizardLorebook(val enabled: Boolean) : CharacterEditUiIntent()

    /** 选择向导生成使用的模型配置；0 表示跟随全局模型。 */
    data class SelectWizardProvider(val providerId: Long) : CharacterEditUiIntent()

    /** 请求打开系统图片选择器挑选参考图。 */
    data object PickWizardReferenceImage : CharacterEditUiIntent()

    /** 参考图已选定，携带系统返回的图片 URI。 */
    data class WizardReferencePicked(val uri: Uri) : CharacterEditUiIntent()

    /** 清除已选择的参考图。 */
    data object ClearWizardReference : CharacterEditUiIntent()

    /** 请求模型生成角色卡草稿。 */
    data object GenerateCharacterDraft : CharacterEditUiIntent()

    /** 把当前草稿应用到角色编辑表单。 */
    data object ApplyCharacterDraft : CharacterEditUiIntent()

    data class ShowPromptEditor(val field: CharacterPromptField) : CharacterEditUiIntent()

    data class ChangePromptEditorDraft(val value: String) : CharacterEditUiIntent()

    data object CopyPromptEditorText : CharacterEditUiIntent()

    data object ConfirmPromptEditor : CharacterEditUiIntent()

    data object SaveCharacter : CharacterEditUiIntent()

    data object DeleteCharacterClick : CharacterEditUiIntent()

    data object ConfirmDeleteCharacter : CharacterEditUiIntent()

    data object ConfirmDeleteCharacterOnly : CharacterEditUiIntent()

    data object ConfirmDeleteCharacterWithLorebook : CharacterEditUiIntent()

    data object ConfirmDiscardChanges : CharacterEditUiIntent()

    data object DismissDialog : CharacterEditUiIntent()
}
