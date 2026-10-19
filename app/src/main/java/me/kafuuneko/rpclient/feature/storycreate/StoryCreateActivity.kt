package me.kafuuneko.rpclient.feature.storycreate

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateUiIntent
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateUiState
import me.kafuuneko.rpclient.feature.storycreate.ui.StoryCreateLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** 新建 Story 页面宿主。 */
class StoryCreateActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<StoryCreateViewModel>()

    /** 小说文件选择：既支持选整本，也支持多选散装的章节文件。 */
    private val mNovelPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            mViewModel.emit(StoryCreateUiIntent.NovelFilesPicked(uris))
        }
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is StoryCreateUiState.Finished) finish()
        }

        StoryCreateLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) },
            onPickNovelFiles = {
                mNovelPicker.launch(
                    arrayOf("text/plain", "text/markdown", "application/octet-stream")
                )
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(StoryCreateUiIntent.Init)
    }
}
