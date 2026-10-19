package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * 聊天页背景展示配置，单聊与群聊共用同一份结构。
 *
 * - [image] 为空表示未配置背景图，页面继续使用主题纯色背景。
 * - [dimPercent] 为遮罩不透明度，越高背景越淡，用于在深色插画上保持正文可读性。
 */
data class ChatBackgroundUiState(
    /** 已解码的背景位图；为空时聊天页不渲染背景图。 */
    val image: ImageBitmap? = null,
    /** 遮罩不透明度（0-100）。 */
    val dimPercent: Int = 0
) {
    /** 是否配置了可渲染的背景图。 */
    val isVisible: Boolean
        get() = image != null
}

/**
 * 在目标区域底部绘制聊天背景。
 *
 * 使用 `drawBehind` 而非额外嵌套一层 Box，原因是聊天页主体是带权重和滚动状态的 Column，
 * 改变层级会破坏既有的键盘避让与列表跟随逻辑；绘制阶段叠加背景对布局零影响。
 *
 * - 未配置背景图时退化为纯色背景，与未启用该功能前的视觉完全一致。
 * - 背景图按短边裁切铺满，保证任意比例图片都不被拉伸变形。
 * - 遮罩使用主题背景色，浅色与深色主题下都能维持正文对比度。
 *
 * @param state 背景配置
 * @param scrimColor 遮罩与纯色回退使用的主题背景色
 */
fun Modifier.chatBackgroundLayer(
    state: ChatBackgroundUiState,
    scrimColor: Color
): Modifier = drawBehind {
    val image = state.image
    if (image == null) {
        // 未配置背景图时保持原有纯色背景
        drawRect(color = scrimColor)
        return@drawBehind
    }
    // 按短边裁切铺满目标区域，居中绘制避免主体被切在边缘
    val scale = maxOf(size.width / image.width, size.height / image.height)
    val dstWidth = (image.width * scale).toInt().coerceAtLeast(1)
    val dstHeight = (image.height * scale).toInt().coerceAtLeast(1)
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(
            x = ((size.width - dstWidth) / 2f).toInt(),
            y = ((size.height - dstHeight) / 2f).toInt()
        ),
        dstSize = IntSize(dstWidth, dstHeight),
        // 背景图通常被放大绘制，使用中等质量过滤避免明显锯齿
        filterQuality = FilterQuality.Medium
    )
    // 叠加主题色遮罩，压低插画对比度以突出正文与气泡
    val scrimAlpha = state.dimPercent.coerceIn(0, 100) / 100f
    if (scrimAlpha > 0f) {
        drawRect(color = scrimColor, alpha = scrimAlpha)
    }
}
