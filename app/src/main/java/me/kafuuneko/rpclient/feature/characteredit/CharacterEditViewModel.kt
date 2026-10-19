package me.kafuuneko.rpclient.feature.characteredit

import com.google.gson.Gson
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asImageBitmap
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterEditForm
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterLorebookItem
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterProviderItem
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterWizardStep
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterWizardUiState
import me.kafuuneko.rpclient.feature.characteredit.model.hasUnsavedChangesFrom
import me.kafuuneko.rpclient.feature.characteredit.model.mergeWizardDraft
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.character.CharacterWizardCodec
import me.kafuuneko.rpclient.libs.character.CharacterWizardDraft
import me.kafuuneko.rpclient.libs.character.CharacterWizardLorebook
import me.kafuuneko.rpclient.libs.character.CharacterWizardPromptBuilder
import me.kafuuneko.rpclient.libs.character.CharacterWizardSeed
import me.kafuuneko.rpclient.libs.character.withProfileFlag
import me.kafuuneko.rpclient.libs.character.withProfileText
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditDialogState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditLoadState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditMode
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterPromptField
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiIntent
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditViewEvent
import me.kafuuneko.rpclient.feature.worldbooklist.WorldBookListActivity
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.utils.orSingleBlank
import me.kafuuneko.rpclient.utils.removeAtOrSelf
import me.kafuuneko.rpclient.utils.toJsonString
import me.kafuuneko.rpclient.utils.updateAt
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 角色创建与编辑页的状态持有者。
 *
 * 核心职责：
 * - 维护角色全量表单字段（基本信息、性格、场景、首条消息、示例对话、系统提示词、历史后指令、深度提示词、替代开场白等）；
 * - 管理角色头像文件生命周期（选择、暂存、替换、保存与未提交临时文件清理）；
 * - 协调角色关联世界书与独立 LLM 提供商配置的校验与动态同步；
 * - 提供多行长文本 Prompt 全屏编辑器弹窗及剪贴板复制能力；
 * - 支持未保存修改防误退脏检查弹窗及级联删除关联世界书确认。
 */
class CharacterEditViewModel : CoreViewModelWithEvent<CharacterEditUiIntent, CharacterEditUiState>(
    CharacterEditUiState.None
), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mContext by inject<Context>()

    /** 角色卡生成向导的 Prompt 构建器与输出解析器；两者均无状态，可安全复用。 */
    private val mWizardPromptBuilder = CharacterWizardPromptBuilder()
    private val mWizardCodec = CharacterWizardCodec(Gson())

    /** 初始化编辑页，拉取候选世界书与提供商列表，并加载目标角色或新建空表单。 */
    @UiIntentObserver(CharacterEditUiIntent.Init::class)
    private suspend fun onInit(intent: CharacterEditUiIntent.Init) {
        if (!isStateOf<CharacterEditUiState.None>()) return
        // 在 IO 线程并发查询候选世界书列表与提供商列表
        val (lorebooks, providers) = withContext(Dispatchers.IO) {
            mLorebookRepository.getAllLorebooks() to mLLMRepository.getAllProviders()
        }
        // 若传入角色 ID 则查询角色实体详情
        val character = intent.characterId?.let {
            withContext(Dispatchers.IO) { mCharacterRepository.getCharacterById(it) }
        }
        // 查询该角色独立绑定的 LLM 提供商 ID
        val llmProviderId = character?.let {
            withContext(Dispatchers.IO) { mCharacterRepository.getLLMProviderId(it.id) }
        } ?: 0L
        // 转换实体为 UI 表单数据
        val form = character?.let {
            CharacterEditForm.from(it, llmProviderId)
        } ?: CharacterEditForm()
        // 构建初始 UI 状态
        CharacterEditUiState.Normal(
            mode = if (character == null) CharacterEditMode.Create else CharacterEditMode.Edit,
            form = form.ensureListInputs(),
            avatarImage = form.resolveAvatarImage(),
            availableLorebooks = lorebooks.map { CharacterLorebookItem(it.id, it.name) },
            availableProviders = providers
                .filter { it.isEnabled || it.id == form.llmProviderId }
                .map { provider ->
                    CharacterProviderItem(
                        id = provider.id,
                        name = provider.name,
                        model = provider.model,
                        isEnabled = provider.isEnabled
                    )
                },
            loadState = CharacterEditLoadState.None
        ).setup()
    }

    /** 更新角色绑定的世界书 ID。 */
    @UiIntentObserver(CharacterEditUiIntent.UpdateCharacterLorebook::class)
    private fun onUpdateCharacterLorebook(intent: CharacterEditUiIntent.UpdateCharacterLorebook) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(
            form = uiState.form.copy(characterLorebookId = intent.lorebookId)
        ).setup()
    }

    /** 选择角色绑定的独立 LLM 提供商。 */
    @UiIntentObserver(CharacterEditUiIntent.SelectLLMProvider::class)
    private fun onSelectLLMProvider(intent: CharacterEditUiIntent.SelectLLMProvider) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(
            form = uiState.form.copy(llmProviderId = intent.providerId),
            availableProviders = uiState.availableProviders.filter {
                it.isEnabled || it.id == intent.providerId
            }
        ).setup()
    }

    /** 页面恢复可见时重新校验世界书与模型提供商有效性，清理失效外键关联。 */
    @UiIntentObserver(CharacterEditUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        // 重新拉取最新的世界书与提供商列表
        val (lorebooks, providers) = withContext(Dispatchers.IO) {
            mLorebookRepository.getAllLorebooks() to mLLMRepository.getAllProviders()
        }
        val availableLorebookIds = lorebooks.mapTo(mutableSetOf()) { it.id }
        val availableProviderIds = providers.mapTo(mutableSetOf()) { it.id }
        // 校验并重置可能已被外部删除的世界书或提供商外键
        uiState.copy(
            form = uiState.form
                .withValidLorebookAssociation(availableLorebookIds)
                .withValidProviderAssociation(availableProviderIds),
            initialForm = uiState.initialForm
                .withValidLorebookAssociation(availableLorebookIds)
                .withValidProviderAssociation(availableProviderIds),
            availableLorebooks = lorebooks.map { CharacterLorebookItem(it.id, it.name) },
            availableProviders = providers
                .filter { it.isEnabled || it.id == uiState.form.llmProviderId }
                .map { provider ->
                    CharacterProviderItem(
                        id = provider.id,
                        name = provider.name,
                        model = provider.model,
                        isEnabled = provider.isEnabled
                    )
                }
        ).setup()
    }

    /** 打开世界书管理列表页面。 */
    @UiIntentObserver(CharacterEditUiIntent.OpenWorldBookManager::class)
    private fun onOpenWorldBookManager() {
        if (!isStateOf<CharacterEditUiState.Normal>()) return
        AppViewEvent.StartActivity(WorldBookListActivity::class.java).tryEmit()
    }

    /** 处理返回操作，若有未保存修改弹出防误退弹窗，否则清理临时文件并退出。 */
    @UiIntentObserver(CharacterEditUiIntent.Back::class)
    private suspend fun onBack() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (
            uiState.loadState == CharacterEditLoadState.Saving ||
            uiState.loadState == CharacterEditLoadState.Deleting
        ) return
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) {
            uiState.copy(dialogState = CharacterEditDialogState.UnsavedChangesConfirm).setup()
            return
        }
        finishEditing()
    }

    /** 触发系统图片选择器以选取角色头像。 */
    @UiIntentObserver(CharacterEditUiIntent.PickAvatarClick::class)
    private fun onPickAvatarClick() {
        if (!isStateOf<CharacterEditUiState.Normal>()) return
        CharacterEditViewEvent.OpenAvatarPicker.tryEmit()
    }

    /**
     * 接收裁剪页生成的文件，并替换表单持有的临时头像。
     *
     * 连续选择时只删除尚未提交的上一份临时文件；数据库仍引用的原头像必须等角色保存
     * 成功或用户确认删除后才能清理。
     */
    @UiIntentObserver(CharacterEditUiIntent.AvatarCropped::class)
    private suspend fun onAvatarCropped(intent: CharacterEditUiIntent.AvatarCropped) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(loadState = CharacterEditLoadState.Saving).setup()
        // 裁剪页已保存新文件；这里只清理当前表单不再引用的上一份临时头像。
        runCatching {
            withContext(Dispatchers.IO) {
                if (
                    uiState.form.avatar.isNotBlank() &&
                    uiState.form.avatar != uiState.form.originalAvatar &&
                    uiState.form.avatar != intent.fileUuid
                ) {
                    mFileRepository.deleteFile(uiState.form.avatar)
                }
            }
        }.getOrElse {
            val latestState = getOrNull<CharacterEditUiState.Normal>() ?: return
            latestState.copy(loadState = CharacterEditLoadState.None).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.character_avatar_save_failed).tryEmit()
            return
        }
        // 更新表单头像 UUID 并重新解码位图渲染
        val latestState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val form = latestState.form.copy(avatar = intent.fileUuid)
        latestState.copy(
            form = form,
            avatarImage = form.resolveAvatarImage(),
            loadState = CharacterEditLoadState.None
        ).setup()
    }

    /** 修改角色名称。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: CharacterEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    /** 批量设置标签列表。 */
    @UiIntentObserver(CharacterEditUiIntent.SetTags::class)
    private fun onSetTags(intent: CharacterEditUiIntent.SetTags) =
        updateForm { copy(tags = intent.tags.orSingleBlank()) }

    /** 修改角色描述（Description）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeDescription::class)
    private fun onChangeDescription(intent: CharacterEditUiIntent.ChangeDescription) =
        updateForm { copy(description = intent.value) }

    /** 修改作者附言（Creator Notes）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeCreatorNotes::class)
    private fun onChangeCreatorNotes(intent: CharacterEditUiIntent.ChangeCreatorNotes) =
        updateForm { copy(creatorNotes = intent.value) }

    /** 修改创作者名称（Creator）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeCreator::class)
    private fun onChangeCreator(intent: CharacterEditUiIntent.ChangeCreator) =
        updateForm { copy(creator = intent.value) }

    /** 修改角色卡版本号（Character Version）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeCharacterVersion::class)
    private fun onChangeCharacterVersion(intent: CharacterEditUiIntent.ChangeCharacterVersion) =
        updateForm { copy(characterVersion = intent.value) }

    /** 修改性格描述（Personality）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangePersonality::class)
    private fun onChangePersonality(intent: CharacterEditUiIntent.ChangePersonality) =
        updateForm { copy(personality = intent.value) }

    /** 修改场景设定（Scenario）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeScenario::class)
    private fun onChangeScenario(intent: CharacterEditUiIntent.ChangeScenario) =
        updateForm { copy(scenario = intent.value) }

    /** 追加一个空的首条消息（First Message）输入项。 */
    @UiIntentObserver(CharacterEditUiIntent.AddFirstMessage::class)
    private fun onAddFirstMessage() =
        updateForm { copy(firstMessages = firstMessages + "") }

    /** 修改指定索引处的首条消息。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeFirstMessage::class)
    private fun onChangeFirstMessage(intent: CharacterEditUiIntent.ChangeFirstMessage) =
        updateForm { copy(firstMessages = firstMessages.updateAt(intent.index, intent.value)) }

    /** 删除指定索引处的首条消息。 */
    @UiIntentObserver(CharacterEditUiIntent.DeleteFirstMessage::class)
    private fun onDeleteFirstMessage(intent: CharacterEditUiIntent.DeleteFirstMessage) =
        updateForm { copy(firstMessages = firstMessages.removeAtOrSelf(intent.index).orSingleBlank()) }

    /** 修改示例对话（Examples of Dialogue）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeExamplesOfDialogue::class)
    private fun onChangeExamplesOfDialogue(intent: CharacterEditUiIntent.ChangeExamplesOfDialogue) =
        updateForm { copy(examplesOfDialogue = intent.value) }

    /** 修改历史后指令（Post-History Instructions）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangePostHistoryInstructions::class)
    private fun onChangePostHistoryInstructions(intent: CharacterEditUiIntent.ChangePostHistoryInstructions) =
        updateForm { copy(postHistoryInstructions = intent.value) }

    /** 修改主系统提示词（System Prompt）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeSystemPrompt::class)
    private fun onChangeSystemPrompt(intent: CharacterEditUiIntent.ChangeSystemPrompt) =
        updateForm { copy(systemPrompt = intent.value) }

    /** 修改角色深度提示词内容（Depth Prompt）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptPrompt::class)
    private fun onChangeDepthPromptPrompt(intent: CharacterEditUiIntent.ChangeDepthPromptPrompt) =
        updateForm { copy(depthPromptPrompt = intent.value) }

    /** 修改角色深度提示词注入深度（Depth Prompt Depth）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptDepth::class)
    private fun onChangeDepthPromptDepth(intent: CharacterEditUiIntent.ChangeDepthPromptDepth) =
        updateForm { copy(depthPromptDepth = intent.value) }

    /** 修改角色深度提示词消息角色（Depth Prompt Role）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptRole::class)
    private fun onChangeDepthPromptRole(intent: CharacterEditUiIntent.ChangeDepthPromptRole) =
        updateForm { copy(depthPromptRole = intent.value) }

    /** 追加一个空的替代开场白（Alternate Greeting）输入项。 */
    @UiIntentObserver(CharacterEditUiIntent.AddAlternateGreeting::class)
    private fun onAddAlternateGreeting() =
        updateForm { copy(alternateGreetings = alternateGreetings + "") }

    /** 修改指定索引处的替代开场白。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeAlternateGreeting::class)
    private fun onChangeAlternateGreeting(intent: CharacterEditUiIntent.ChangeAlternateGreeting) =
        updateForm { copy(alternateGreetings = alternateGreetings.updateAt(intent.index, intent.value)) }

    /** 删除指定索引处的替代开场白。 */
    @UiIntentObserver(CharacterEditUiIntent.DeleteAlternateGreeting::class)
    private fun onDeleteAlternateGreeting(intent: CharacterEditUiIntent.DeleteAlternateGreeting) =
        updateForm { copy(alternateGreetings = alternateGreetings.removeAtOrSelf(intent.index).orSingleBlank()) }

    /** 修改扩展 JSON 字符串。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeExtensionsJson::class)
    private fun onChangeExtensionsJson(intent: CharacterEditUiIntent.ChangeExtensionsJson) =
        updateForm { copy(extensionsJson = intent.value) }

    /** 修改扩展档案中的文本字段，字段定位由枚举保证与数据模型一致。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeProfileText::class)
    private fun onChangeProfileText(intent: CharacterEditUiIntent.ChangeProfileText) =
        updateForm { copy(profile = profile.withProfileText(intent.field, intent.value)) }

    /** 修改扩展档案中的三态布尔字段；null 表示用户选择「未设定」。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeProfileFlag::class)
    private fun onChangeProfileFlag(intent: CharacterEditUiIntent.ChangeProfileFlag) =
        updateForm { copy(profile = profile.withProfileFlag(intent.field, intent.value)) }

    /** 修改角色当前感情状态。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeRelationshipStatus::class)
    private fun onChangeRelationshipStatus(intent: CharacterEditUiIntent.ChangeRelationshipStatus) =
        updateForm { copy(profile = profile.copy(relationshipStatus = intent.status)) }

    /** 更新向导弹窗状态；向导未打开时直接忽略，避免无谓的状态重建。 */
    private fun updateWizard(block: CharacterWizardUiState.() -> CharacterWizardUiState) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(wizardState = uiState.wizardState.block()).setup()
    }

    /** 打开角色卡生成向导，重置为输入步骤并清空上一次的错误提示。 */
    @UiIntentObserver(CharacterEditUiIntent.OpenCharacterWizard::class)
    private fun onOpenCharacterWizard() = updateWizard {
        copy(
            visible = true,
            step = CharacterWizardStep.Input,
            errorResId = null,
            // 沿用上次选择的识图模型，避免每次打开都要重新指定
            wizardProviderId = AppModel.characterWizardProvider
        )
    }

    /** 关闭向导并丢弃未应用的草稿与参考图引用。 */
    @UiIntentObserver(CharacterEditUiIntent.CloseCharacterWizard::class)
    private fun onCloseCharacterWizard() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(wizardState = CharacterWizardUiState()).setup()
    }

    @UiIntentObserver(CharacterEditUiIntent.ChangeWizardName::class)
    private fun onChangeWizardName(intent: CharacterEditUiIntent.ChangeWizardName) =
        updateWizard { copy(seedName = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeWizardConcept::class)
    private fun onChangeWizardConcept(intent: CharacterEditUiIntent.ChangeWizardConcept) =
        updateWizard { copy(seedConcept = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeWizardRequirements::class)
    private fun onChangeWizardRequirements(intent: CharacterEditUiIntent.ChangeWizardRequirements) =
        updateWizard { copy(seedRequirements = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ToggleWizardLorebook::class)
    private fun onToggleWizardLorebook(intent: CharacterEditUiIntent.ToggleWizardLorebook) =
        updateWizard { copy(includeLorebook = intent.enabled) }

    /**
     * 选择向导使用的模型配置并立即持久化。
     *
     * 识图精度与对话文风往往是两种不同的模型优势，因此该选择独立于角色的默认模型绑定，
     * 只在角色卡生成流程内生效。非法 ID 直接忽略，防止外部传入已删除的配置。
     */
    @UiIntentObserver(CharacterEditUiIntent.SelectWizardProvider::class)
    private fun onSelectWizardProvider(intent: CharacterEditUiIntent.SelectWizardProvider) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val isKnown = intent.providerId == 0L ||
            uiState.availableProviders.any { it.id == intent.providerId }
        if (!isKnown) return
        AppModel.characterWizardProvider = intent.providerId
        updateWizard { copy(wizardProviderId = intent.providerId) }
    }

    /** 请求 Activity 打开系统图片选择器；图片保存由后续 Intent 完成。 */
    @UiIntentObserver(CharacterEditUiIntent.PickWizardReferenceImage::class)
    private fun onPickWizardReferenceImage() {
        if (!isStateOf<CharacterEditUiState.Normal>()) return
        CharacterEditViewEvent.OpenWizardReferencePicker.tryEmit()
    }

    /**
     * 保存向导参考图并生成预览。
     *
     * 参考图按原始比例保存，只做采样解码用于预览与尺寸上报；更换图片时删除不再被引用的旧文件。
     */
    @UiIntentObserver(CharacterEditUiIntent.WizardReferencePicked::class)
    private suspend fun onWizardReferencePicked(intent: CharacterEditUiIntent.WizardReferencePicked) {
        val current = getOrNull<CharacterEditUiState.Normal>() ?: return
        val previousUuid = current.wizardState.referenceImageUuid
        // 复制到应用私有存储，避免依赖系统返回的一次性 URI 授权
        val fileUuid = runCatching {
            withContext(Dispatchers.IO) { mFileRepository.saveFile(intent.uri) }
        }.getOrElse {
            updateWizard { copy(errorResId = R.string.character_wizard_reference_failed) }
            return
        }
        val preview = withContext(Dispatchers.IO) {
            mFileRepository.loadSampledBitmap(
                uuid = fileUuid,
                requestedWidthPx = WIZARD_REFERENCE_MAX_EDGE,
                requestedHeightPx = WIZARD_REFERENCE_MAX_EDGE
            )
        }
        // 新引用就绪后再清理旧参考图，避免中途失败导致预览丢失
        if (previousUuid.isNotBlank() && previousUuid != fileUuid) {
            runCatching {
                withContext(Dispatchers.IO) { mFileRepository.deleteFile(previousUuid) }
            }
        }
        updateWizard {
            copy(
                referenceImageUuid = fileUuid,
                referenceImage = preview?.asImageBitmap(),
                errorResId = null
            )
        }
    }

    /** 清除向导参考图并删除对应文件。 */
    @UiIntentObserver(CharacterEditUiIntent.ClearWizardReference::class)
    private suspend fun onClearWizardReference() {
        val current = getOrNull<CharacterEditUiState.Normal>() ?: return
        val previousUuid = current.wizardState.referenceImageUuid
        if (previousUuid.isNotBlank()) {
            runCatching {
                withContext(Dispatchers.IO) { mFileRepository.deleteFile(previousUuid) }
            }
        }
        updateWizard { copy(referenceImageUuid = "", referenceImage = null) }
    }

    /**
     * 请求模型生成角色卡草稿。
     *
     * - 模型选择优先使用向导专用配置，未单独指定时回落到当前全局配置。
     * - 附带参考图时以多模态消息发送，由视觉模型自行提取外貌特征。
     * - 解析失败与请求失败给出不同提示，便于用户判断是重试还是改模型。
     */
    @UiIntentObserver(CharacterEditUiIntent.GenerateCharacterDraft::class)
    private suspend fun onGenerateCharacterDraft() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val wizard = uiState.wizardState
        if (wizard.generating) return
        updateWizard { copy(generating = true, errorResId = null) }
        try {
            val draft = requestCharacterDraft(wizard)
            updateWizard {
                copy(
                    generating = false,
                    draft = draft,
                    step = CharacterWizardStep.Preview
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: CharacterWizardParseFailure) {
            updateWizard {
                copy(generating = false, errorResId = R.string.character_wizard_parse_failed)
            }
        } catch (error: Exception) {
            updateWizard {
                copy(generating = false, errorResId = R.string.character_wizard_generate_failed)
            }
        }
    }

    /** 把草稿合并进编辑表单并关闭向导；世界书草稿会在此刻落库并绑定角色。 */
    @UiIntentObserver(CharacterEditUiIntent.ApplyCharacterDraft::class)
    private suspend fun onApplyCharacterDraft() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val draft = uiState.wizardState.draft ?: return
        // 用户在向导中显式填写过名称时才覆盖表单名称
        val preferDraftName = uiState.wizardState.seedName.isBlank()
        var mergedForm = uiState.form.mergeWizardDraft(draft, preferDraftName)
        // 世界书草稿在应用时创建，失败不阻断角色卡应用
        draft.lorebook?.let { lorebookDraft ->
            val lorebookId = createWizardLorebook(lorebookDraft, mergedForm.name)
            if (lorebookId != 0L) {
                mergedForm = mergedForm.copy(characterLorebookId = lorebookId)
            }
        }
        uiState.copy(
            form = mergedForm,
            wizardState = CharacterWizardUiState()
        ).setup()
    }

    /**
     * 将向导世界书草稿写入数据库。
     *
     * - 世界书名称优先使用草稿提供的名称，缺失时回退到角色名，再回退到通用标题。
     * - 条目按草稿顺序写入 insertion order，保证关键词命中优先级与生成顺序一致。
     * - 任一步骤失败返回 0，由调用方保留角色卡其余内容，避免整体应用失败。
     *
     * @param draft 世界书草稿
     * @param characterName 当前角色名，用于生成兜底世界书名
     * @return 新建世界书 ID；失败时返回 0
     */
    private suspend fun createWizardLorebook(
        draft: CharacterWizardLorebook,
        characterName: String
    ): Long {
        return runCatching {
            val fallbackName = characterName.ifBlank {
                mContext.getString(R.string.character_wizard_field_lorebook)
            }
            val lorebookId = mLorebookRepository.createLorebook(
                draft.name.ifBlank { fallbackName }
            )
            draft.entries.forEachIndexed { index, entry ->
                mLorebookRepository.saveEntry(
                    LorebookEntry(
                        lorebookId = lorebookId,
                        name = entry.name.ifBlank { entry.keywords.firstOrNull().orEmpty() },
                        keywords = Gson().toJsonString(entry.keywords),
                        secondaryKeywords = "[]",
                        order = index,
                        depth = 4,
                        category = "[]",
                        content = entry.content
                    )
                )
            }
            lorebookId
        }.getOrDefault(0L)
    }

    /**
     * 组装并发送角色卡生成请求。
     *
     * @param wizard 当前向导状态
     * @return 解析成功的角色卡草稿
     * @throws CharacterWizardParseFailure 模型输出无法解析为结构化草稿
     */
    private suspend fun requestCharacterDraft(wizard: CharacterWizardUiState): CharacterWizardDraft {
        // 向导内选择优先，其次是全局保存的识图配置，最后才回落到当前对话模型
        val providerId = wizard.wizardProviderId
            .takeIf { it != 0L }
            ?: AppModel.characterWizardProvider.takeIf { it != 0L }
            ?: AppModel.currentLLMProvider
        val provider = mLLMRepository.getProviderById(providerId)
            ?: throw IllegalStateException("No provider available for character wizard")
        val seed = CharacterWizardSeed(
            name = wizard.seedName,
            concept = wizard.seedConcept,
            requirements = wizard.seedRequirements,
            referenceImageAttached = wizard.referenceImageUuid.isNotBlank(),
            includeLorebook = wizard.includeLorebook
        )
        // 参考图与文本放在同一条用户消息内，保持图文顺序
        val userBlocks = buildList {
            add(LLMContentBlock.Text(mWizardPromptBuilder.buildUserPrompt(seed)))
            if (wizard.referenceImageUuid.isNotBlank()) {
                buildImageReference(wizard.referenceImageUuid)?.let {
                    add(LLMContentBlock.Image(it))
                }
            }
        }
        val request = LLMGenerationRequest(
            messages = listOf(
                LLMMessage(
                    role = LLMMessageRole.System,
                    content = mWizardPromptBuilder.buildSystemPrompt(seed.includeLorebook)
                ),
                LLMMessage(
                    role = LLMMessageRole.User,
                    content = userBlocks.filterIsInstance<LLMContentBlock.Text>()
                        .joinToString("\n\n") { it.text },
                    blocks = userBlocks
                )
            ),
            options = LLMGenerationOptions(temperature = WIZARD_TEMPERATURE)
        )
        val response = mLLMRepository.generate(provider.id, request)
        return mWizardCodec.parse(response.content) ?: throw CharacterWizardParseFailure()
    }

    /**
     * 构造参考图引用，供多模态请求使用。
     *
     * 宽高与字节数用于适配器的图片预算估算；文件缺失时返回 null，
     * 此时请求降级为纯文本，不影响生成流程。
     */
    private suspend fun buildImageReference(uuid: String): LLMImageReference? {
        val entity = mFileRepository.getFileEntity(uuid) ?: return null
        val file = mFileRepository.getFile(uuid)
        val bitmap = mFileRepository.loadSampledBitmap(
            uuid = uuid,
            requestedWidthPx = WIZARD_REFERENCE_MAX_EDGE,
            requestedHeightPx = WIZARD_REFERENCE_MAX_EDGE
        )
        val reference = LLMImageReference(
            uuid = uuid,
            mimeType = entity.mimeType ?: "image/jpeg",
            width = bitmap?.width ?: 0,
            height = bitmap?.height ?: 0,
            byteCount = file?.length() ?: 0L
        )
        bitmap?.recycle()
        return reference
    }

    /** 模型输出无法解析为结构化草稿时使用的内部异常。 */
    private class CharacterWizardParseFailure : Exception("Character wizard output is not valid JSON")

    /** 打开长文本字段的全屏 Prompt 编辑器弹窗。 */
    @UiIntentObserver(CharacterEditUiIntent.ShowPromptEditor::class)
    private fun onShowPromptEditor(intent: CharacterEditUiIntent.ShowPromptEditor) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val draftText = uiState.form.promptText(intent.field) ?: return
        uiState.copy(
            dialogState = CharacterEditDialogState.PromptEditor(intent.field, draftText)
        ).setup()
    }

    /** 更新全屏 Prompt 编辑器中的临时草稿文本。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangePromptEditorDraft::class)
    private fun onChangePromptEditorDraft(intent: CharacterEditUiIntent.ChangePromptEditorDraft) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterEditDialogState.PromptEditor ?: return
        uiState.copy(dialogState = dialogState.copy(draftText = intent.value)).setup()
    }

    /** 复制全屏 Prompt 编辑器中的草稿文本到剪贴板。 */
    @UiIntentObserver(CharacterEditUiIntent.CopyPromptEditorText::class)
    private fun onCopyPromptEditorText() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterEditDialogState.PromptEditor ?: return
        CharacterEditViewEvent.CopyText(dialogState.draftText).tryEmit()
    }

    /** 确认并回写全屏 Prompt 编辑器中的内容至主表单。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmPromptEditor::class)
    private fun onConfirmPromptEditor() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterEditDialogState.PromptEditor ?: return
        uiState.copy(
            form = uiState.form.withPromptText(dialogState.field, dialogState.draftText),
            dialogState = CharacterEditDialogState.None
        ).setup()
    }

    /** 先提交角色对新头像的引用，再清理不再使用的原头像文件。 */
    @UiIntentObserver(CharacterEditUiIntent.SaveCharacter::class)
    private suspend fun onSaveCharacter() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val character = uiState.form.toCharacterOrNullWithToast() ?: return
        uiState.copy(loadState = CharacterEditLoadState.Saving).setup()
        // 在 IO 线程保存角色及关联的 LLM 提供商配置
        withContext(Dispatchers.IO) {
            mCharacterRepository.saveCharacterWithLLMProvider(
                character = character,
                llmProviderId = uiState.form.llmProviderId
            )
            // 提交成功后安全删除被替换的原头像物理文件
            if (uiState.form.originalAvatar.isNotBlank() && uiState.form.originalAvatar != character.avatar) {
                mFileRepository.deleteFile(uiState.form.originalAvatar)
            }
        }
        AppViewEvent.PopupToastMessageByResId(
            if (uiState.mode == CharacterEditMode.Create) R.string.character_created else R.string.character_saved
        ).tryEmit()
        CharacterEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 点击删除角色，检查是否有关联世界书并弹出相应确认对话框。 */
    @UiIntentObserver(CharacterEditUiIntent.DeleteCharacterClick::class)
    private suspend fun onDeleteCharacterClick() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.isNew) {
            cleanupPendingAvatar()
            CharacterEditUiState.finished(uiStateFlow.value).setup()
            return
        }
        val savedCharacter = withContext(Dispatchers.IO) {
            mCharacterRepository.getCharacterById(uiState.form.id)
        } ?: run {
            CharacterEditUiState.finished(uiStateFlow.value).setup()
            return
        }
        val associatedLorebook = savedCharacter.characterLorebookId
            .takeIf { it != 0L }
            ?.let { lorebookId ->
                withContext(Dispatchers.IO) {
                    mLorebookRepository.getLorebookById(lorebookId)
                }
            }
        uiState.copy(
            dialogState = if (associatedLorebook == null) {
                CharacterEditDialogState.DeleteConfirm(
                    characterName = savedCharacter.name
                )
            } else {
                CharacterEditDialogState.DeleteWithLorebookConfirm(
                    characterName = savedCharacter.name,
                    lorebookId = associatedLorebook.id,
                    lorebookName = associatedLorebook.name
                )
            }
        ).setup()
    }

    /** 确认删除无关联世界书的角色。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmDeleteCharacter::class)
    private suspend fun onConfirmDeleteCharacter() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState !is CharacterEditDialogState.DeleteConfirm) return
        deleteCharacter()
    }

    /** 用户确认仅删除角色本身，保留关联世界书。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmDeleteCharacterOnly::class)
    private suspend fun onConfirmDeleteCharacterOnly() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState !is CharacterEditDialogState.DeleteWithLorebookConfirm) return
        deleteCharacter()
    }

    /** 用户确认一并删除角色及其绑定的专属世界书。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmDeleteCharacterWithLorebook::class)
    private suspend fun onConfirmDeleteCharacterWithLorebook() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterEditDialogState.DeleteWithLorebookConfirm
            ?: return
        deleteCharacter(lorebookId = dialogState.lorebookId)
    }

    /**
     * 删除角色及其头像；只有用户在关联确认中明确选择时才一并删除世界书。
     *
     * 表单尚未提交的新头像不受数据库实体追踪，需要在同一流程中额外清理。
     *
     * @param lorebookId 需一并删除的关联世界书 ID（若有）
     */
    private suspend fun deleteCharacter(lorebookId: Long? = null) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.isNew) return
        uiState.copy(
            loadState = CharacterEditLoadState.Deleting,
            dialogState = CharacterEditDialogState.None
        ).setup()
        val pendingAvatar = uiState.form.avatar
            .takeIf { it.isNotBlank() && it != uiState.form.originalAvatar }
        withContext(Dispatchers.IO) {
            val character = mCharacterRepository.getCharacterById(uiState.form.id)
            // 可选删除关联的世界书
            lorebookId?.let {
                mLorebookRepository.deleteLorebook(it)
            }
            // 删除角色实体
            mCharacterRepository.deleteCharacter(uiState.form.id)
            // 删除已持久化的旧头像文件
            character?.avatar?.takeIf { it.isNotBlank() }?.let {
                mFileRepository.deleteFile(it)
            }
            // 删除未持久化的新头像临时文件
            pendingAvatar?.let {
                mFileRepository.deleteFile(it)
            }
        }
        AppViewEvent.PopupToastMessageByResId(R.string.character_deleted).tryEmit()
        CharacterEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 确认放弃未保存的修改，直接退出编辑流程。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmDiscardChanges::class)
    private suspend fun onConfirmDiscardChanges() {
        finishEditing()
    }

    /** 关闭当前显示的弹窗。 */
    @UiIntentObserver(CharacterEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(dialogState = CharacterEditDialogState.None).setup()
    }

    /** 辅助方法：以不可变方式更新当前角色表单数据。 */
    private fun updateForm(block: CharacterEditForm.() -> CharacterEditForm) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

    /** 将表单转换为角色实体，名称为空时弹出 Toast 提示并返回 null。 */
    private fun CharacterEditForm.toCharacterOrNullWithToast(): Character? {
        if (name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.character_name_empty).tryEmit()
            return null
        }
        return toCharacter()
    }

    /** 异步加载当前表单持有的头像图片并解码为 ImageBitmap。 */
    private suspend fun CharacterEditForm.resolveAvatarImage() =
        avatar.takeIf { it.isNotBlank() }?.let {
            withContext(Dispatchers.IO) { mFileRepository.loadAvatarBitmap(it)?.asImageBitmap() }
        }

    /** 清理尚未提交保存的新头像临时物理文件。 */
    private suspend fun cleanupPendingAvatar() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.avatar.isBlank() || uiState.form.avatar == uiState.form.originalAvatar) return
        withContext(Dispatchers.IO) {
            mFileRepository.deleteFile(uiState.form.avatar)
        }
    }

    /** 结束编辑流程：清理未提交的临时头像文件并迁移至 Finished 状态。 */
    private suspend fun finishEditing() {
        cleanupPendingAvatar()
        CharacterEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 确保所有列表输入项至少包含一个输入框。 */
    private fun CharacterEditForm.ensureListInputs(): CharacterEditForm {
        return copy(
            tags = tags.orSingleBlank(),
            firstMessages = firstMessages.orSingleBlank(),
            alternateGreetings = alternateGreetings.orSingleBlank()
        )
    }

    /** 校验绑定的世界书 ID 是否仍然存在，若已被删除则重置为 0。 */
    private fun CharacterEditForm.withValidLorebookAssociation(
        availableLorebookIds: Set<Long>
    ): CharacterEditForm {
        if (characterLorebookId == 0L || characterLorebookId in availableLorebookIds) return this
        return copy(characterLorebookId = 0L)
    }

    /** 校验绑定的 LLM 提供商 ID 是否仍然存在，若已被删除则重置为 0。 */
    private fun CharacterEditForm.withValidProviderAssociation(
        availableProviderIds: Set<Long>
    ): CharacterEditForm {
        if (llmProviderId == 0L || llmProviderId in availableProviderIds) return this
        return copy(llmProviderId = 0L)
    }

    /** 根据目标枚举字段读取对应的 Prompt 文本内容。 */
    private fun CharacterEditForm.promptText(field: CharacterPromptField): String? {
        return when (field) {
            CharacterPromptField.Description -> description
            CharacterPromptField.Personality -> personality
            CharacterPromptField.Scenario -> scenario
            is CharacterPromptField.FirstMessage -> firstMessages.getOrNull(field.index)
            CharacterPromptField.DialogueExamples -> examplesOfDialogue
            CharacterPromptField.SystemPrompt -> systemPrompt
            CharacterPromptField.PostHistoryInstructions -> postHistoryInstructions
            CharacterPromptField.DepthPrompt -> depthPromptPrompt
            is CharacterPromptField.AlternateGreeting -> alternateGreetings.getOrNull(field.index)
        }
    }

    /** 根据目标枚举字段将 Prompt 编辑器中的文本回写至表单对应字段。 */
    private fun CharacterEditForm.withPromptText(
        field: CharacterPromptField,
        text: String
    ): CharacterEditForm {
        return when (field) {
            CharacterPromptField.Description -> copy(description = text)
            CharacterPromptField.Personality -> copy(personality = text)
            CharacterPromptField.Scenario -> copy(scenario = text)
            is CharacterPromptField.FirstMessage -> copy(
                firstMessages = firstMessages.updateAt(field.index, text)
            )
            CharacterPromptField.DialogueExamples -> copy(examplesOfDialogue = text)
            CharacterPromptField.SystemPrompt -> copy(systemPrompt = text)
            CharacterPromptField.PostHistoryInstructions -> copy(postHistoryInstructions = text)
            CharacterPromptField.DepthPrompt -> copy(depthPromptPrompt = text)
            is CharacterPromptField.AlternateGreeting -> copy(
                alternateGreetings = alternateGreetings.updateAt(field.index, text)
            )
        }
    }

}

/** 向导参考图解码的最大边长；兼顾视觉模型识别精度与内存占用。 */
private const val WIZARD_REFERENCE_MAX_EDGE = 1280

/** 角色卡生成的采样温度：需要一定发散度，但过高会导致 JSON 结构不稳定。 */
private const val WIZARD_TEMPERATURE = 0.9f
