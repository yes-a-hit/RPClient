package me.kafuuneko.rpclient.feature.characteredit.model

import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.libs.character.CharacterProfileExtra
import me.kafuuneko.rpclient.libs.character.CharacterWizardDraft
import me.kafuuneko.rpclient.libs.character.RelationshipStatus

/** 角色卡生成向导当前所处的步骤。 */
enum class CharacterWizardStep {
    /** 填写种子信息并选择参考图。 */
    Input,

    /** 预览生成结果并决定是否应用。 */
    Preview
}

/**
 * 角色卡生成向导的界面状态。
 *
 * 向导以弹窗形式挂在角色编辑页上，因此状态独立于 [CharacterEditForm]：
 * 生成结果在用户点击应用前不会影响正在编辑的表单。
 */
data class CharacterWizardUiState(
    /** 弹窗是否可见。 */
    val visible: Boolean = false,
    /** 当前步骤。 */
    val step: CharacterWizardStep = CharacterWizardStep.Input,
    /** 用户指定的角色名，可留空交给模型决定。 */
    val seedName: String = "",
    /** 一句话概念描述。 */
    val seedConcept: String = "",
    /** 额外要求，例如语言与风格。 */
    val seedRequirements: String = "",
    /** 是否同时生成世界书。 */
    val includeLorebook: Boolean = true,
    /** 参考图预览。 */
    val referenceImage: ImageBitmap? = null,
    /** 参考图在本地文件库中的 UUID；为空表示未选择。 */
    val referenceImageUuid: String = "",
    /**
     * 生成请求使用的模型配置 ID。
     *
     * 0 表示跟随全局模型。识别参考图依赖多模态能力，而不同模型的图像理解精度差异很大，
     * 因此允许为向导单独指定一个识图专用配置，避免与日常对话模型互相牵制。
     */
    val wizardProviderId: Long = 0L,
    /** 是否正在请求模型。 */
    val generating: Boolean = false,
    /** 生成失败时展示的本地化提示。 */
    val errorResId: Int? = null,
    /** 生成成功后的草稿，供预览与应用。 */
    val draft: CharacterWizardDraft? = null
) {
    /** 是否可以发起生成：正在生成时禁用，避免重复请求。 */
    val canGenerate: Boolean
        get() = !generating
}

/**
 * 将向导草稿合并进角色编辑表单。
 *
 * 合并规则：
 * - 角色名：用户在向导中显式填写过则使用向导值，否则保留表单原有名称。
 * - 文本字段：草稿中非空的内容覆盖表单，空内容不覆盖，避免把已填字段清空。
 * - 标签：取并集并去重，保留用户已有标签。
 * - 扩展档案：逐字段覆盖非空项，三态布尔仅在草稿明确给出时更新。
 *
 * @param draft 向导生成的草稿
 * @param preferDraftName 是否采用草稿中的角色名
 * @return 合并后的表单
 */
fun CharacterEditForm.mergeWizardDraft(
    draft: CharacterWizardDraft,
    preferDraftName: Boolean
): CharacterEditForm {
    return copy(
        name = if (preferDraftName && draft.name.isNotBlank()) draft.name else name,
        description = draft.description.ifBlank { description },
        personality = draft.personality.ifBlank { personality },
        scenario = draft.scenario.ifBlank { scenario },
        firstMessages = if (draft.firstMessage.isNotBlank()) {
            listOf(draft.firstMessage)
        } else {
            firstMessages
        },
        examplesOfDialogue = draft.exampleDialogue.ifBlank { examplesOfDialogue },
        tags = (tags + draft.tags).distinct().filter { it.isNotBlank() },
        profile = profile.mergeWizardProfile(draft.profile)
    )
}

/** 逐字段合并扩展档案，草稿中的空值不会清空用户已填内容。 */
private fun CharacterProfileExtra.mergeWizardProfile(
    draft: CharacterProfileExtra
): CharacterProfileExtra {
    return CharacterProfileExtra(
        age = draft.age.ifBlank { age },
        gender = draft.gender.ifBlank { gender },
        occupation = draft.occupation.ifBlank { occupation },
        orientation = draft.orientation.ifBlank { orientation },
        relationshipStatus = draft.relationshipStatus.takeIf {
            it != RelationshipStatus.Unspecified
        } ?: relationshipStatus,
        hasRomanticHistory = draft.hasRomanticHistory ?: hasRomanticHistory,
        romanticHistory = draft.romanticHistory.ifBlank { romanticHistory },
        hasSexualHistory = draft.hasSexualHistory ?: hasSexualHistory,
        sexualHistory = draft.sexualHistory.ifBlank { sexualHistory },
        boundaries = draft.boundaries.ifBlank { boundaries },
        likes = draft.likes.ifBlank { likes },
        dislikes = draft.dislikes.ifBlank { dislikes },
        extraNotes = draft.extraNotes.ifBlank { extraNotes }
    )
}
