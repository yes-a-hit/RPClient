package me.kafuuneko.rpclient.feature.script

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryTab
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryUiIntent
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryUiState
import me.kafuuneko.rpclient.feature.script.presentation.ScriptLibraryViewEvent
import me.kafuuneko.rpclient.feature.script.ui.ScriptLibraryLayout
import me.kafuuneko.rpclient.feature.scriptplay.ScriptPlayActivity
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 剧本库宿主：承载剧本与场景两个标签，以及导入与跳转编排。 */
class ScriptLibraryActivity : CoreActivityWithEvent() {
    /** 初始标签由入口决定，默认展示剧本。 */
    private val mInitialTab: ScriptLibraryTab by lazy {
        when (intent.getStringExtra(EXTRA_TAB)) {
            TAB_SCENE -> ScriptLibraryTab.Scene
            else -> ScriptLibraryTab.Pack
        }
    }

    private val mViewModel: ScriptLibraryViewModel by viewModels {
        ScriptLibraryViewModelFactory(mInitialTab)
    }

    /** 文件选择：导入剧本 JSON。 */
    private val mFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) mViewModel.emit(ScriptLibraryUiIntent.ImportFromUri(uri))
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(Unit) {
            mViewModel.emit(ScriptLibraryUiIntent.Init)
        }
        LaunchedEffect(uiState) {
            if (uiState is ScriptLibraryUiState.Finished) finish()
        }

        ScriptLibraryLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) },
            onBack = { mViewModel.emit(ScriptLibraryUiIntent.Back) }
        )
    }

    /** 打开选择器、跳转编辑页与结果提示都在宿主处理。 */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            ScriptLibraryViewEvent.OpenFilePicker -> {
                mFilePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                return
            }

            is ScriptLibraryViewEvent.StartPlay -> {
                startActivity(ScriptPlayActivity.intentOf(this, viewEvent.packId))
                return
            }

            is ScriptLibraryViewEvent.StartEditor -> {
                startActivity(ScriptEditorActivity.intentOf(this, viewEvent.packId))
                return
            }

            is ScriptLibraryViewEvent.ImportResult -> {
                val message = if (viewEvent.success) {
                    getString(R.string.script_import_ok, viewEvent.detail, 1)
                } else {
                    getString(R.string.script_import_failed, viewEvent.detail)
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return
            }

            else -> Unit
        }
        super.onReceivedViewEvent(viewEvent)
    }

    companion object {
        /** 入口标签参数名。 */
        const val EXTRA_TAB = "extra_script_tab"

        /** 场景标签取值。 */
        const val TAB_SCENE = "scene"

        /** 构造剧本库的启动 Intent。 */
        fun intentOf(context: android.content.Context, tab: ScriptLibraryTab): android.content.Intent =
            android.content.Intent(context, ScriptLibraryActivity::class.java).apply {
                if (tab == ScriptLibraryTab.Scene) putExtra(EXTRA_TAB, TAB_SCENE)
            }
    }
}

/** 剧本库 ViewModel 工厂：把入口标签带进 ViewModel。 */
class ScriptLibraryViewModelFactory(
    private val tab: ScriptLibraryTab
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ScriptLibraryViewModel(tab) as T
    }
}