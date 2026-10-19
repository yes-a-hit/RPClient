package me.kafuuneko.rpclient.feature.characteredit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiIntent
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditViewEvent
import me.kafuuneko.rpclient.feature.characteredit.ui.CharacterEditLayout
import me.kafuuneko.rpclient.feature.imagecrop.ImageCropActivity
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 角色创建与编辑页面宿主，处理头像文件选择等系统事件。 */
class CharacterEditActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<CharacterEditViewModel>()

    /** 系统选择结果只用于打开裁剪页，不直接进入角色表单。 */
    private val mAvatarPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { mImageCropLauncher.launch(ImageCropActivity.createIntent(this, it)) }
    }

    /** 裁剪页返回已持久化的方形头像 UUID，再交由角色表单管理其临时生命周期。 */
    private val mImageCropLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        ImageCropActivity.getResultFileUuid(result.data)?.let {
            mViewModel.emit(CharacterEditUiIntent.AvatarCropped(it))
        }
    }

    /**
     * 角色卡生成向导的参考图选择结果。
     *
     * 参考图只用于视觉模型识别外貌，不做方形裁剪，因此直接把 URI 交给 ViewModel 保存。
     */
    private val mWizardReferenceLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { mViewModel.emit(CharacterEditUiIntent.WizardReferencePicked(it)) }
        }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is CharacterEditUiState.Finished) finish()
        }

        CharacterEditLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val characterId = intent.getLongExtra(EXTRA_CHARACTER_ID, 0L).takeIf { it > 0L }
        mViewModel.emit(CharacterEditUiIntent.Init(characterId))
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(CharacterEditUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        super.onReceivedViewEvent(viewEvent)
        when (viewEvent) {
            CharacterEditViewEvent.OpenAvatarPicker -> mAvatarPickerLauncher.launch("image/*")
            CharacterEditViewEvent.OpenWizardReferencePicker ->
                mWizardReferenceLauncher.launch("image/*")
            is CharacterEditViewEvent.CopyText -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.character), viewEvent.text)
                )
                Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_CHARACTER_ID = "extra_character_id"
    }
}
