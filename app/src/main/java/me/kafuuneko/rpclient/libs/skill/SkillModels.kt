package me.kafuuneko.rpclient.libs.skill

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 一个可被助手直接接入的 Skill 能力包。
 *
 * Skill 是声明式的能力描述：命中 [triggers] 或标记为 [alwaysOn] 时，
 * [instructions] 会作为独立片段注入系统区，让模型按既定流程完成某类任务，
 * 而不需要用户每次手写长段提示词。
 *
 * 字段全部为纯文本，便于在设备之间以 JSON 复制粘贴分享。
 */
data class SkillManifest(
    /** 稳定标识，同时作为存储文件名；导入时为空会自动生成。 */
    val id: String = "",
    /** 展示名称，注入 Prompt 时作为小节标题。 */
    val name: String = "",
    /** 一句话说明用途，仅在管理界面展示。 */
    val description: String = "",
    /** 作者自定义的版本号，仅用于展示。 */
    val version: String = "",
    /** 触发词列表；最近消息中包含任一词时激活。 */
    val triggers: List<String> = emptyList(),
    /** 是否常驻激活，不受触发词限制。 */
    val alwaysOn: Boolean = false,
    /** 是否启用；关闭后不参与激活判定。 */
    val enabled: Boolean = true,
    /** 能力正文，直接注入 Prompt。 */
    val instructions: String = "",
    /**
     * 输出模板。
     *
     * 与 [instructions] 的分工：instructions 说「做什么」，
     * template 说「按什么样子交出来」——表格、JSON、分点清单都行。
     * 留空表示不限制输出形态。
     */
    val template: String = ""
) {
    /** 是否具备参与激活判定的最低内容。 */
    val isUsable: Boolean
        get() = name.isNotBlank() && instructions.isNotBlank()

    /** 界面展示用的触发词摘要；常驻时给出常驻标记。 */
    val triggerSummary: String
        get() = if (alwaysOn) ALWAYS_ON_LABEL else triggers.joinToString(" / ")

    companion object {
        /** 常驻 Skill 在界面上的占位标签，实际文案由界面层本地化。 */
        const val ALWAYS_ON_LABEL = "*"
    }
}

/**
 * Skill 的 JSON 编解码。
 *
 * 导入时先尝试解析为数组，再尝试单个对象，并允许前后附带说明文字，
 * 以便用户直接粘贴带 Markdown 围栏的分享内容。
 */
object SkillCodec {
    private val mGson = Gson()

    /**
     * 解析一个或多个 Skill。
     *
     * @param raw 用户提供的原始文本
     * @return 解析成功且内容可用的 Skill 列表；无法解析时返回空列表
     */
    fun decode(raw: String): List<SkillManifest> {
        val json = extractJson(raw) ?: return emptyList()
        val element = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return emptyList()
        val manifests = when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { toManifest(it) }
            element.isJsonObject -> listOfNotNull(toManifest(element))
            else -> emptyList()
        }
        return manifests.filter { it.isUsable }
    }

    /** 序列化单个 Skill。 */
    fun encode(skill: SkillManifest): String = mGson.toJson(skill)

    /** 序列化 Skill 列表，用于整体导出。 */
    fun encodeAll(skills: List<SkillManifest>): String = mGson.toJson(skills)

    /** 从可能包含围栏或解释文字的文本中截取 JSON 主体。 */
    private fun extractJson(raw: String): String? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val arrayStart = text.indexOf('[')
        val objectStart = text.indexOf('{')
        val start = when {
            arrayStart < 0 -> objectStart
            objectStart < 0 -> arrayStart
            else -> minOf(arrayStart, objectStart)
        }
        if (start < 0) return null
        val end = if (text[start] == '[') text.lastIndexOf(']') else text.lastIndexOf('}')
        if (end <= start) return null
        return text.substring(start, end + 1)
    }

    /** 把单个 JSON 对象映射为 Skill；缺少必要字段时返回 null。 */
    private fun toManifest(element: JsonElement): SkillManifest? {
        val json = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return SkillManifest(
            id = json.stringOrEmpty("id"),
            name = json.stringOrEmpty("name"),
            description = json.stringOrEmpty("description"),
            version = json.stringOrEmpty("version"),
            triggers = json.stringList("triggers"),
            alwaysOn = json.booleanOrFalse("alwaysOn"),
            enabled = json.booleanOrDefault("enabled", true),
            instructions = json.stringOrEmpty("instructions"),
                template = json.stringOrEmpty("template")
        )
    }

    private fun JsonObject.stringOrEmpty(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()

    private fun JsonObject.booleanOrFalse(key: String): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false

    private fun JsonObject.booleanOrDefault(key: String, fallback: Boolean): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: fallback

    /** 读取字符串数组；兼容单个字符串与缺失字段。 */
    private fun JsonObject.stringList(key: String): List<String> {
        val element = get(key) ?: return emptyList()
        if (element.isJsonArray) {
            return (element as JsonArray)
                .mapNotNull { it.takeIf { item -> item.isJsonPrimitive }?.asString }
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
        val single = element.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        return if (single.isBlank()) emptyList() else listOf(single)
    }
}
