package me.kafuuneko.rpclient.feature.gallery.presentation

import me.kafuuneko.rpclient.libs.gallery.GalleryImage

/** 图库页状态。 */
sealed class GalleryUiState {
    /** 加载中。 */
    data object Loading : GalleryUiState()

    /** 正常态。 */
    data class Ready(
        /** 全部图片，按加入时间倒序。 */
        val images: List<GalleryImage> = emptyList(),
        /** 沙箱外的公开图数量，用于顶部统计。 */
        val publicCount: Int = 0,
        /** 正在编辑哪张图的标签；null 表示未展开编辑器。 */
        val editingId: String? = null,
        /** 标签编辑草稿。 */
        val labelDraft: String = "",
        /** 角色绑定草稿。 */
        val characterDraft: String = "",
        /** 是否设为公开。 */
        val publicDraft: Boolean = true,
        /** 一句话说明草稿。 */
        val descriptionDraft: String = ""
    ) : GalleryUiState() {
        /** 空图库。 */
        val isEmpty: Boolean get() = images.isEmpty()
    }
}

/** 图库页意图。 */
sealed class GalleryUiIntent {
    /** 初始化。 */
    data object Init : GalleryUiIntent()

    /** 选择图片导入。 */
    data object ImportClick : GalleryUiIntent()

    /** 已选中的图片 URI。 */
    data class ImagesPicked(val uris: List<android.net.Uri>) : GalleryUiIntent()

    /** 打开某张图的标签编辑器。 */
    data class EditLabels(val id: String) : GalleryUiIntent()

    /** 关闭编辑器。 */
    data object CloseEditor : GalleryUiIntent()

    /** 标签草稿变化，逗号或顿号分隔。 */
    data class LabelDraftChanged(val value: String) : GalleryUiIntent()

    /** 角色绑定变化。 */
    data class CharacterDraftChanged(val value: String) : GalleryUiIntent()

    /** 说明变化。 */
    data class DescriptionDraftChanged(val value: String) : GalleryUiIntent()

    /** 公开开关。 */
    data class PublicDraftChanged(val value: Boolean) : GalleryUiIntent()

    /** 保存当前编辑。 */
    data object SaveEdit : GalleryUiIntent()

    /** 删除一张图。 */
    data class Delete(val id: String) : GalleryUiIntent()

    /** 返回。 */
    data object Back : GalleryUiIntent()
}

/** 图库页事件。 */
sealed class GalleryViewEvent : me.kafuuneko.rpclient.libs.core.IViewEvent {
    /** 轻提示。 */
    data class Toast(val message: String) : GalleryViewEvent()

    /** 打开系统文件选择器。 */
    data object PickImages : GalleryViewEvent()

    /** 退出页面。 */
    data object Finish : GalleryViewEvent()
}
