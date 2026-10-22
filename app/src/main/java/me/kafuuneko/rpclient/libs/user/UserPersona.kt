package me.kafuuneko.rpclient.libs.user

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.defaults.DefaultNames

/**
 * user 人设的取值规则：**本处为空才继承全局**。
 *
 * 之前是「创建会话时把全局快照进来」，之后就脱钩了——
 * 改了全局人设，老会话不会跟着变；反过来想恢复继承也没办法。
 * 现在改成读的时候回落：本处有值用本处，空着取全局，全局也空就不注入。
 *
 * 放在这里而不是散在各 ViewModel，是因为取值点有十来个，
 * 规则一改就得全找一遍，很容易漏。
 */

/** 本会话的显示名；本处空着就用全局。 */
fun effectiveUserName(local: String): String =
    local.ifBlank { AppModel.resolvedUserName }.ifBlank { DefaultNames.USER }

/** 本会话的人设描述；本处空着就用全局。 */
fun effectiveUserDescription(local: String): String =
    local.ifBlank { AppModel.userDescription }.trim()

/** 本处是否在覆盖全局人设。 */
fun hasLocalPersona(name: String, description: String): Boolean =
    name.isNotBlank() || description.isNotBlank()
