package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 主页面需要 Activity 执行的系统文件和图片选择事件。 */
sealed class MainViewEvent : IViewEvent {
    data object OpenUserAvatarPicker : MainViewEvent()

    /** 打开系统图片选择器挑选聊天背景图；背景图不需要方形裁剪。 */
    data object OpenChatBackgroundPicker : MainViewEvent()

    data object OpenChatImporter : MainViewEvent()
    data object OpenChatImageDirectory : MainViewEvent()
}
