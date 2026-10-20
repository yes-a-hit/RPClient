package me.kafuuneko.rpclient.feature.gallery

import android.net.Uri
import me.kafuuneko.rpclient.feature.gallery.presentation.GalleryUiIntent
import me.kafuuneko.rpclient.feature.gallery.presentation.GalleryUiState
import me.kafuuneko.rpclient.feature.gallery.presentation.GalleryViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.gallery.GalleryImage
import me.kafuuneko.rpclient.libs.gallery.GalleryStore
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext

/**
 * 图库状态持有者。
 *
 * 图库本身只是本地元数据与图片文件，不发任何模型请求；
 * 模型通过助手工具读写它，这里负责人工整理标签与角色绑定。
 */
class GalleryViewModel : CoreViewModelWithEvent<GalleryUiIntent, GalleryUiState>(
    GalleryUiState.Loading
), KoinComponent {
    private val mStore by inject<GalleryStore>()
    private val mFileRepository by inject<FileRepository>()
    private val mContext by inject<android.content.Context>()

    /** 读取全部图片。 */
    @UiIntentObserver(GalleryUiIntent.Init::class)
    private suspend fun onInit() {
        if (isStateOf<GalleryUiState.Ready>()) return
        publish()
    }

    /** 打开系统选择器。 */
    @UiIntentObserver(GalleryUiIntent.ImportClick::class)
    private fun onImportClick() {
        GalleryViewEvent.PickImages.tryEmit()
    }

    /** 选中的图片导入图库，默认公开、无标签。 */
    @UiIntentObserver(GalleryUiIntent.ImagesPicked::class)
    private suspend fun onImagesPicked(intent: GalleryUiIntent.ImagesPicked) {
        var imported = 0
        intent.uris.forEach { uri ->
            val name = queryName(uri)
            val saved = mStore.importFromUri(
                uri = uri,
                saveFile = { target -> runCatching { mFileRepository.saveFile(target) }.getOrNull() },
                name = name,
                labels = emptyList(),
                isPublic = true
            )
            if (saved != null) imported++
        }
        GalleryViewEvent.Toast("已导入 $imported 张").tryEmit()
        publish()
    }

    /** 打开标签编辑器。 */
    @UiIntentObserver(GalleryUiIntent.EditLabels::class)
    private suspend fun onEditLabels(intent: GalleryUiIntent.EditLabels) {
        val state = getOrNull<GalleryUiState.Ready>() ?: return
        val image = mStore.find(intent.id) ?: return
        state.copy(
            editingId = image.id,
            labelDraft = image.labels.joinToString("、"),
            characterDraft = image.characterName,
            publicDraft = image.isPublic,
            descriptionDraft = image.description
        ).setup()
    }

    /** 关闭编辑器。 */
    @UiIntentObserver(GalleryUiIntent.CloseEditor::class)
    private fun onCloseEditor() {
        val state = getOrNull<GalleryUiState.Ready>() ?: return
        state.copy(editingId = null).setup()
    }

    @UiIntentObserver(GalleryUiIntent.LabelDraftChanged::class)
    private fun onLabelDraftChanged(intent: GalleryUiIntent.LabelDraftChanged) {
        val state = getOrNull<GalleryUiState.Ready>() ?: return
        state.copy(labelDraft = intent.value).setup()
    }

    @UiIntentObserver(GalleryUiIntent.CharacterDraftChanged::class)
    private fun onCharacterDraftChanged(intent: GalleryUiIntent.CharacterDraftChanged) {
        val state = getOrNull<GalleryUiState.Ready>() ?: return
        state.copy(characterDraft = intent.value).setup()
    }

    @UiIntentObserver(GalleryUiIntent.DescriptionDraftChanged::class)
    private fun onDescriptionDraftChanged(intent: GalleryUiIntent.DescriptionDraftChanged) {
        val state = getOrNull<GalleryUiState.Ready>() ?: return
        state.copy(descriptionDraft = intent.value).setup()
    }

    @UiIntentObserver(GalleryUiIntent.PublicDraftChanged::class)
    private fun onPublicDraftChanged(intent: GalleryUiIntent.PublicDraftChanged) {
        val state = getOrNull<GalleryUiState.Ready>() ?: return
        state.copy(publicDraft = intent.value).setup()
    }

    /** 保存标签、角色绑定与公开标记。 */
    @UiIntentObserver(GalleryUiIntent.SaveEdit::class)
    private suspend fun onSaveEdit() {
        val state = getOrNull<GalleryUiState.Ready>() ?: return
        val id = state.editingId ?: return
        val image = mStore.find(id) ?: return
        val labels = state.labelDraft
            .split('、', ',', '，', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        mStore.save(
            image.copy(
                labels = labels,
                characterName = state.characterDraft.trim(),
                isPublic = state.publicDraft,
                description = state.descriptionDraft.trim()
            )
        )
        GalleryViewEvent.Toast("已保存").tryEmit()
        publish()
    }

    /** 删除一张图。 */
    @UiIntentObserver(GalleryUiIntent.Delete::class)
    private suspend fun onDelete(intent: GalleryUiIntent.Delete) {
        mStore.delete(intent.id)
        publish()
    }

    @UiIntentObserver(GalleryUiIntent.Back::class)
    private fun onBack() {
        GalleryViewEvent.Finish.tryEmit()
    }

    /** 重新读取并发布。 */
    private suspend fun publish() {
        val images = mStore.list()
        GalleryUiState.Ready(
            images = images,
            publicCount = images.count { it.isPublic }
        ).setup()
    }

    /** 取文件显示名；失败时用固定名，避免空名落库。 */
    private fun queryName(uri: Uri): String = runCatching {
        val cursor = mContext.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        cursor?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "image"
}
