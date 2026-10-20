package me.kafuuneko.rpclient.feature.gallery.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.gallery.presentation.GalleryUiIntent
import me.kafuuneko.rpclient.feature.gallery.presentation.GalleryUiState
import me.kafuuneko.rpclient.libs.gallery.GalleryImage
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import org.koin.core.context.GlobalContext

/**
 * 图库页。
 *
 * 图片是智能体的可发素材：标签决定何时被选中，角色绑定限制给谁看，
 * 公开则任何对话都能用。这里只做整理，不影响聊天流程。
 */
@Composable
fun GalleryLayout(
    uiState: GalleryUiState,
    emit: GalleryUiIntent.() -> Unit,
    onBack: () -> Unit
) {
    val state = uiState as? GalleryUiState.Ready ?: return
    Scaffold(
        topBar = {
            AppTopBar(
                title = "图库",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { GalleryUiIntent.ImportClick.emit() }) {
                        Icon(Icons.Rounded.Add, contentDescription = "导入图片")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "共 ${state.images.size} 张 · 公开 ${state.publicCount} 张",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.isEmpty) {
                item {
                    Text(
                        text = "还没有图片。导入后可以贴标签、绑定角色，助手会按情境自己挑图发给你。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.images) { image ->
                GalleryRow(
                    image = image,
                    onEdit = { GalleryUiIntent.EditLabels(image.id).emit() },
                    onDelete = { GalleryUiIntent.Delete(image.id).emit() }
                )
            }
        }
    }

    if (state.editingId != null) {
        GalleryEditDialog(state, emit)
    }
}

/** 一行：缩略图 + 名称 + 标签与绑定信息。 */
@Composable
private fun GalleryRow(
    image: GalleryImage,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GalleryThumbnail(uuid = image.fileUuid)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = image.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = image.toBrief(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 缩略图；加载失败显示占位图标。 */
@Composable
private fun GalleryThumbnail(uuid: String) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uuid) {
        val loaded: androidx.compose.ui.graphics.ImageBitmap? = withContext(Dispatchers.IO) {
            runCatching {
                val repository = GlobalContext.get().get<FileRepository>()
                repository.loadSampledBitmap(uuid, 128, 128)?.asImageBitmap()
            }.getOrNull()
        }
        value = loaded
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 标签与绑定编辑。 */
@Composable
private fun GalleryEditDialog(
    state: GalleryUiState.Ready,
    emit: GalleryUiIntent.() -> Unit
) {
    AlertDialog(
        onDismissRequest = { GalleryUiIntent.CloseEditor.emit() },
        title = { Text("图片设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.labelDraft,
                    onValueChange = { GalleryUiIntent.LabelDraftChanged(it).emit() },
                    label = { Text("标签（顿号或逗号分隔）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.characterDraft,
                    onValueChange = { GalleryUiIntent.CharacterDraftChanged(it).emit() },
                    label = { Text("绑定角色（留空不限）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.descriptionDraft,
                    onValueChange = { GalleryUiIntent.DescriptionDraftChanged(it).emit() },
                    label = { Text("适合什么时候发") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "公开（任何对话可用）",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = state.publicDraft,
                        onCheckedChange = { GalleryUiIntent.PublicDraftChanged(it).emit() }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { GalleryUiIntent.SaveEdit.emit() }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = { GalleryUiIntent.CloseEditor.emit() }) {
                Text("取消")
            }
        }
    )
}
