package me.kafuuneko.rpclient.feature.characteredit.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 角色编辑页需要 Activity 调用系统组件处理的一次性事件。 */
sealed class CharacterEditViewEvent : IViewEvent {
    data object OpenAvatarPicker : CharacterEditViewEvent()

    /** 打开系统图片选择器挑选角色卡生成向导的参考图。 */
    data object OpenWizardReferencePicker : CharacterEditViewEvent()

    data class CopyText(val text: String) : CharacterEditViewEvent()
}
