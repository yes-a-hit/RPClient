package me.kafuuneko.rpclient.feature.script

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.script.presentation.ScriptEditorUiIntent
import me.kafuuneko.rpclient.feature.script.presentation.ScriptEditorUiState
import me.kafuuneko.rpclient.feature.script.presentation.ScriptEditorViewEvent
import me.kafuuneko.rpclient.feature.script.ui.ScriptEditorLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 剧本画布宿主：承载节点拖拽与 JSON 导出。 */
class ScriptEditorActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ScriptEditorViewModel>()

    /** 剧本文件选择：导入 JSON 后立即装载到表格。 */
    private val mFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) mViewModel.emit(ScriptEditorUiIntent.ImportFromUri(uri))
    }

    /** 从剧本库进入时携带的剧本 ID。 */
    private val mPackId: String by lazy { intent.getStringExtra(EXTRA_PACK_ID).orEmpty() }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(Unit) {
            if (mPackId.isNotBlank()) {
                mViewModel.emit(ScriptEditorUiIntent.OpenPack(mPackId))
            } else {
                mViewModel.emit(ScriptEditorUiIntent.Init)
            }
        }
        LaunchedEffect(uiState) {
            if (uiState is ScriptEditorUiState.Finished) finish()
        }

        ScriptEditorLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) },
            onBack = { mViewModel.emit(ScriptEditorUiIntent.Back) }
        )
    }

    /** 导出写入剪贴板，导入拉起文件选择器。 */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            ScriptEditorViewEvent.OpenFilePicker -> {
                mFilePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                return
            }

            is ScriptEditorViewEvent.ImportResult -> {
                val message = if (viewEvent.success) {
                    getString(R.string.script_import_ok, viewEvent.detail, viewEvent.savedPackCount)
                } else {
                    getString(R.string.script_import_failed, viewEvent.detail)
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return
            }

            else -> Unit
        }
        if (viewEvent is ScriptEditorViewEvent.CopyToClipboard) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("script-pack", viewEvent.text))
            Toast.makeText(this, R.string.script_export_done, Toast.LENGTH_SHORT).show()
            return
        }
        super.onReceivedViewEvent(viewEvent)
    }

    companion object {
        /** 剧本 ID 参数名。 */
        private const val EXTRA_PACK_ID = "extra_pack_id"

        /** 构造进入指定剧本的启动 Intent。 */
        fun intentOf(context: Context, packId: String): Intent =
            Intent(context, ScriptEditorActivity::class.java).putExtra(EXTRA_PACK_ID, packId)
    }
}
