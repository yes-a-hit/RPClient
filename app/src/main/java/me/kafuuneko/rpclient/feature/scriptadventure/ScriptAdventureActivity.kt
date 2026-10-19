package me.kafuuneko.rpclient.feature.scriptadventure

import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.script.ScriptLibraryActivity
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryTab
import me.kafuuneko.rpclient.feature.scriptadventure.presentation.ScriptAdventureUiIntent
import me.kafuuneko.rpclient.feature.scriptadventure.presentation.ScriptAdventureUiState
import me.kafuuneko.rpclient.feature.scriptadventure.presentation.ScriptAdventureViewEvent
import me.kafuuneko.rpclient.feature.scriptadventure.ui.ScriptAdventureLayout
import me.kafuuneko.rpclient.feature.scriptplay.ScriptPlayActivity
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 剧情冒险宿主：开新局或继续已有存档。 */
class ScriptAdventureActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ScriptAdventureViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(Unit) {
            mViewModel.emit(ScriptAdventureUiIntent.Init)
        }
        LaunchedEffect(uiState) {
            if (uiState is ScriptAdventureUiState.Finished) finish()
        }

        ScriptAdventureLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) },
            onBack = { mViewModel.emit(ScriptAdventureUiIntent.Back) }
        )
    }

    /** 进入剧本模式或跳转剧本库。 */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            is ScriptAdventureViewEvent.LaunchPlay -> {
                startActivity(
                    ScriptPlayActivity.intentOf(this, viewEvent.packId, viewEvent.playId)
                )
                return
            }

            ScriptAdventureViewEvent.OpenLibrary -> {
                startActivity(ScriptLibraryActivity.intentOf(this, ScriptLibraryTab.Pack))
                return
            }

            else -> Unit
        }
        super.onReceivedViewEvent(viewEvent)
    }

    companion object {
        /** 构造剧情冒险页的启动 Intent。 */
        fun intentOf(context: Context): Intent =
            Intent(context, ScriptAdventureActivity::class.java)
    }
}
