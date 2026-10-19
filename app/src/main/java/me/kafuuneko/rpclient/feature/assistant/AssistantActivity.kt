package me.kafuuneko.rpclient.feature.assistant

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantUiIntent
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantViewEvent
import me.kafuuneko.rpclient.feature.assistant.ui.AssistantLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 助手会话页面宿主。 */
class AssistantActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<AssistantViewModel>()

    /** 文件选择：把手机里的文件复制进助手沙箱。 */
    private val mFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) mViewModel.emit(AssistantUiIntent.FilesPicked(uris))
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 助手页发 Toast 与导入结果，文件选择与其余事件交回基类或 launcher 处理。 */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            is AssistantViewEvent.Toast -> {
                Toast.makeText(this, viewEvent.message, Toast.LENGTH_SHORT).show()
                return
            }

            AssistantViewEvent.OpenFilePicker -> {
                mFilePicker.launch(arrayOf("image/*", "text/plain", "text/markdown", "application/json", "application/octet-stream"))
                return
            }

            is AssistantViewEvent.ShowImportResult -> {
                Toast.makeText(
                    this,
                    getString(
                        R.string.assistant_import_result,
                        viewEvent.importedCount,
                        viewEvent.failedCount
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }
        super.onReceivedViewEvent(viewEvent)
    }

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(Unit) {
            mViewModel.emit(AssistantUiIntent.Init)
        }

        AssistantLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) },
            onBack = { finish() }
        )
    }
}
