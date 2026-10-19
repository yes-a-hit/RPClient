package me.kafuuneko.rpclient.feature.scriptplay

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import me.kafuuneko.rpclient.feature.scriptplay.presentation.ScriptPlayUiIntent
import me.kafuuneko.rpclient.feature.scriptplay.presentation.ScriptPlayUiState
import me.kafuuneko.rpclient.feature.scriptplay.presentation.ScriptPlayViewEvent
import me.kafuuneko.rpclient.feature.scriptplay.ui.ScriptPlayLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.libs.script.ScriptStore
import org.koin.android.ext.android.inject

/** 剧本模式宿主：一局对话的容器。 */
class ScriptPlayActivity : CoreActivityWithEvent() {
    private val mScriptStore by inject<ScriptStore>()

    /** 剧本 ID 由入口传入。 */
    private val mPackId: String by lazy { intent.getStringExtra(EXTRA_PACK_ID).orEmpty() }

    /** 一局一存档，ID 由剧本 ID 加时间戳生成，避免覆盖旧局。 */
    private val mPlayId: String by lazy {
        intent.getStringExtra(EXTRA_PLAY_ID).orEmpty().ifBlank { "$mPackId-${System.currentTimeMillis()}" }
    }

    private val mViewModel: ScriptPlayViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ScriptPlayViewModel(mPackId, mPlayId) as T
            }
        }
    }

    /** 场景 ID 到名称的映射，供场景条展示。 */
    private val mSceneNames = mutableStateMapOf<String, String>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(Unit) {
            // 只读一次剧本，用于把场景 ID 翻成名称
            mScriptStore.load(mPackId)?.scenes?.forEach { scene ->
                mSceneNames[scene.id] = scene.name
            }
            mViewModel.emit(ScriptPlayUiIntent.Init)
        }
        LaunchedEffect(uiState) {
            if (uiState is ScriptPlayUiState.Finished) finish()
        }

        ScriptPlayLayout(
            uiState = uiState,
            sceneNames = mSceneNames,
            emit = { mViewModel.emit(this) },
            onBack = { mViewModel.emit(ScriptPlayUiIntent.Back) }
        )
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        if (viewEvent is ScriptPlayViewEvent.Toast) {
            Toast.makeText(this, viewEvent.message, Toast.LENGTH_SHORT).show()
            return
        }
        super.onReceivedViewEvent(viewEvent)
    }

    companion object {
        private const val EXTRA_PACK_ID = "extra_play_pack_id"
        private const val EXTRA_PLAY_ID = "extra_play_id"

        /** 开始或继续一局剧本。 */
        fun intentOf(context: Context, packId: String, playId: String = ""): Intent =
            Intent(context, ScriptPlayActivity::class.java).apply {
                putExtra(EXTRA_PACK_ID, packId)
                if (playId.isNotBlank()) putExtra(EXTRA_PLAY_ID, playId)
            }
    }
}
