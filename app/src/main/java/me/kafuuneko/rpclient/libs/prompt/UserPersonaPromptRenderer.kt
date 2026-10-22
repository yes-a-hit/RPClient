package me.kafuuneko.rpclient.libs.prompt

/**
 * 使用统一模板包装用户人设，并按当前会话作用域展开名称宏。
 *
 * 人设为空时不注入任何内容；模板为空或未包含 `{{persona}}` 时回退为人设正文，
 * 避免错误配置导致用户人设被静默丢弃。没有唯一角色的上下文可传入 null，保留
 * 人设正文与模板中的 `{{char}}` 供后续诊断。
 */
fun renderUserPersonaTemplate(
    template: String,
    userName: String,
    userDescription: String,
    characterName: String?,
    /** 性别或性别认同，空着不影响原有行为。 */
    userGender: String = "",
    /** 用户发来的图片代表什么；空着按模型默认理解。 */
    userImageMeaning: String = ""
): String {
    // 三者都空才跳过，只填了其中一个也算人设
    val merged = me.kafuuneko.rpclient.libs.user.describeUser(
        userDescription,
        userGender,
        userImageMeaning
    )
    if (merged.isBlank()) return ""
    // 先行替换人设描述中的 {{char}} / {{user}} 宏
    val resolvedDescription = resolveCharacterUserMacros(
        template = merged,
        characterName = characterName,
        userName = userName
    )
    if (template.isBlank()) return resolvedDescription
    // 替换模板中的 {{char}} / {{user}} 宏
    val resolvedTemplate = resolveCharacterUserMacros(
        template = template,
        characterName = characterName,
        userName = userName
    )
    // 查找并替换 {{persona}} 插槽宏
    val personaMacro = Regex("""\{\{\s*persona\s*\}\}""", RegexOption.IGNORE_CASE)
    return if (personaMacro.containsMatchIn(resolvedTemplate)) {
        resolvedTemplate.replace(personaMacro) { resolvedDescription }
    } else {
        resolvedDescription
    }
}
