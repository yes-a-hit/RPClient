package me.kafuuneko.rpclient.feature.assistant

import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantUiIntent
import me.kafuuneko.rpclient.feature.assistant.presentation.AssistantViewEvent
import me.kafuuneko.rpclient.feature.assistant.ui.AssistantLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 助手会话页面宿主。 */
class AssistantActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<AssistantViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 助手页只发 Toast，其余事件交回基类统一处理。 */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        if (viewEvent is AssistantViewEvent.Toast) {
            Toast.makeText(this, viewEvent.message, Toast.LENGTH_SHORT).show()
            return
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
