package me.kafuuneko.rpclient.feature.gallery

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.gallery.presentation.GalleryUiIntent
import me.kafuuneko.rpclient.feature.gallery.presentation.GalleryUiState
import me.kafuuneko.rpclient.feature.gallery.presentation.GalleryViewEvent
import me.kafuuneko.rpclient.feature.gallery.ui.GalleryLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 图库宿主：导入图片、贴标签、绑定角色、设为公开。 */
class GalleryActivity : CoreActivityWithEvent() {
    private val mViewModel: GalleryViewModel by viewModels()

    /** 图片选择：可多选。 */
    private val mPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            mViewModel.emit(GalleryUiIntent.ImagesPicked(uris))
        }
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(Unit) {
            mViewModel.emit(GalleryUiIntent.Init)
        }

        GalleryLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) },
            onBack = { mViewModel.emit(GalleryUiIntent.Back) }
        )
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            GalleryViewEvent.PickImages -> {
                mPicker.launch(arrayOf("image/*"))
                return
            }

            GalleryViewEvent.Finish -> {
                finish()
                return
            }

            is GalleryViewEvent.Toast -> {
                Toast.makeText(this, viewEvent.message, Toast.LENGTH_SHORT).show()
                return
            }
        }
        super.onReceivedViewEvent(viewEvent)
    }
}
