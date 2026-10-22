package me.kafuuneko.rpclient.libs.user

import me.kafuuneko.rpclient.libs.defaults.DefaultNames

/**
 * user 人设的取值规则：**本处为空才继承全局**。
 *
 * 之前是「创建会话时把全局快照进来」，之后就脱钩了——
 * 改了全局人设，老会话不会跟着变；反过来想恢复继承也没办法。
 *
 * 这里只做选择，不读全局：全局值由调用方传进来。
 * 直接读 AppModel 会拖进 Kotpref，单元测试里跑不起来——
 * 这条规则本身是有边界要覆盖的，不能因为依赖全局就没法测。
 */

/** 选出生效的显示名：本处优先，其次全局，都空就退回默认称呼。 */
fun pickUserName(local: String, global: String): String =
    local.ifBlank { global }.ifBlank { DefaultNames.USER }

/** 选出生效的人设描述：本处优先，其次全局。 */
fun pickUserDescription(local: String, global: String): String =
    local.ifBlank { global }.trim()

/** 本处是否在覆盖全局人设。 */
fun hasLocalPersona(name: String, description: String): Boolean =
    name.isNotBlank() || description.isNotBlank()

/**
 * 把性别拼进人设描述。
 *
 * 单独一段更好读，也避免用户自己写描述时还要记得提性别。
 * 都空就返回空串，prompt 那边据此整段跳过。
 */
fun describeUser(
    description: String,
    gender: String,
    /**
     * 用户发来的图片代表什么。
     *
     * 空着时模型会按默认理解当成「用户转发来一张图」；
     * 填了之后按这里的说法理解，比如「这张图就是他眼下看到的东西」。
     */
    imageMeaning: String = ""
): String {
    val parts = mutableListOf<String>()
    val body = description.trim()
    if (body.isNotBlank()) parts += body
    val genderText = gender.trim()
    if (genderText.isNotBlank()) parts += "性别：" + genderText
    val imageText = imageMeaning.trim()
    if (imageText.isNotBlank()) parts += "用户发来的图片表示：" + imageText
    return parts.joinToString("\n")
}
