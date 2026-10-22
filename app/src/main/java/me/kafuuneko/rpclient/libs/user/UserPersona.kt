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
fun describeUser(description: String, gender: String): String {
    val body = description.trim()
    val genderText = gender.trim()
    return when {
        body.isBlank() && genderText.isBlank() -> ""
        genderText.isBlank() -> body
        body.isBlank() -> "性别：" + genderText
        else -> body + "\n性别：" + genderText
    }
}
