package me.kafuuneko.rpclient.libs.character

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 角色卡生成向导产出的草稿。
 *
 * 草稿只承载模型返回的文本内容，不直接写库：用户需要在向导预览中确认后，
 * 才会合并进角色编辑表单。这样模型输出的不可信内容不会绕过用户确认直接落地。
 */
data class CharacterWizardDraft(
    /** 角色名称。 */
    val name: String = "",
    /** 角色描述。 */
    val description: String = "",
    /** 性格设定。 */
    val personality: String = "",
    /** 场景设定。 */
    val scenario: String = "",
    /** 主开场白。 */
    val firstMessage: String = "",
    /** 示例对话。 */
    val exampleDialogue: String = "",
    /** 角色标签。 */
    val tags: List<String> = emptyList(),
    /** 扩展档案：身份、感情经历与关系边界。 */
    val profile: CharacterProfileExtra = CharacterProfileExtra(),
    /** 关联世界书草稿；未请求世界书生成时为空。 */
    val lorebook: CharacterWizardLorebook? = null
) {
    /** 草稿是否至少包含一项可用于填充表单的内容。 */
    val isEmpty: Boolean
        get() = name.isBlank() &&
                description.isBlank() &&
                personality.isBlank() &&
                scenario.isBlank() &&
                firstMessage.isBlank() &&
                exampleDialogue.isBlank() &&
                tags.isEmpty() &&
                !profile.hasContent &&
                lorebook == null
}

/** 角色卡生成向导产出的世界书草稿。 */
data class CharacterWizardLorebook(
    /** 世界书名称。 */
    val name: String,
    /** 世界书条目列表。 */
    val entries: List<CharacterWizardLorebookEntry>
)

/** 世界书条目草稿，可直接映射为本地 LorebookEntry。 */
data class CharacterWizardLorebookEntry(
    /** 条目名称，同时作为关键词命中后的显示标题。 */
    val name: String,
    /** 触发关键词。 */
    val keywords: List<String>,
    /** 条目正文。 */
    val content: String
)

/**
 * 角色卡生成向导的响应解析器。
 *
 * 模型经常在 JSON 前后附带解释文字或 Markdown 代码块，因此解析前先做两层容错：
 * 剥离 ```json 围栏，再截取首个 `{` 到末个 `}` 之间的内容。解析失败返回 null，
 * 由上层提示用户重试，而不是把半截 JSON 当作文本填入表单。
 *
 * @param mGson 项目统一使用的 Gson 实例
 */
class CharacterWizardCodec(
    private val mGson: Gson
) {
    /**
     * 解析模型返回的角色卡草稿。
     *
     * @param raw 模型原始输出文本
     * @return 解析成功的草稿；无法解析或内容为空时返回 null
     */
    fun parse(raw: String): CharacterWizardDraft? {
        val json = extractJsonObject(raw) ?: return null
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        val profileNode = root.optObject("profile")
        val draft = CharacterWizardDraft(
            name = root.optText("name"),
            description = root.optText("description"),
            personality = root.optText("personality"),
            scenario = root.optText("scenario"),
            firstMessage = root.optText("first_mes"),
            exampleDialogue = root.optText("mes_example"),
            tags = root.optArray("tags").toStringList(),
            profile = profileNode?.toProfile() ?: CharacterProfileExtra(),
            lorebook = root.optObject("lorebook")?.toLorebook()
        )
        return draft.takeUnless { it.isEmpty }
    }

    /** 从模型输出中截取 JSON 主体，兼容代码块围栏与前后解释文字。 */
    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    /** 将 profile 节点映射为扩展档案，未知或缺失字段保持默认值。 */
    private fun JsonObject.toProfile(): CharacterProfileExtra {
        return CharacterProfileExtra(
            age = optText("age"),
            gender = optText("gender"),
            occupation = optText("occupation"),
            orientation = optText("orientation"),
            relationshipStatus = RelationshipStatus.fromPersistedValue(
                optText("relationship_status").takeIf { it.isNotBlank() }
            ),
            hasRomanticHistory = optNullableBoolean("has_romantic_history"),
            romanticHistory = optText("romantic_history"),
            hasSexualHistory = optNullableBoolean("has_sexual_history"),
            sexualHistory = optText("sexual_history"),
            boundaries = optText("boundaries"),
            likes = optText("likes"),
            dislikes = optText("dislikes"),
            extraNotes = optText("extra_notes")
        )
    }

    /** 将 lorebook 节点映射为世界书草稿，条目正文为空时丢弃该条目。 */
    private fun JsonObject.toLorebook(): CharacterWizardLorebook? {
        val entries = optArray("entries").mapNotNull { element ->
            val entry = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
            val content = entry.optText("content")
            if (content.isBlank()) return@mapNotNull null
            CharacterWizardLorebookEntry(
                name = entry.optText("name").ifBlank { entry.optText("comment") },
                keywords = entry.optArray("keys").toStringList()
                    .ifEmpty { entry.optArray("keywords").toStringList() },
                content = content
            )
        }
        if (entries.isEmpty()) return null
        return CharacterWizardLorebook(
            name = optText("name"),
            entries = entries
        )
    }

    private fun JsonObject.optObject(name: String): JsonObject? {
        val element = get(name)?.takeIf { !it.isJsonNull } ?: return null
        return runCatching { element.asJsonObject }.getOrNull()
    }

    private fun JsonObject.optArray(name: String): JsonArray {
        val element = get(name)?.takeIf { !it.isJsonNull } ?: return JsonArray()
        return runCatching { element.asJsonArray }.getOrDefault(JsonArray())
    }

    private fun JsonObject.optText(name: String): String {
        return runCatching {
            get(name)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        }.getOrDefault("")
    }

    private fun JsonObject.optNullableBoolean(name: String): Boolean? {
        return runCatching {
            get(name)?.takeIf { !it.isJsonNull }?.asBoolean
        }.getOrNull()
    }

    private fun JsonArray.toStringList(): List<String> {
        return mapNotNull { element ->
            runCatching { element.takeIf { !it.isJsonNull }?.asString }.getOrNull()
        }.map { it.trim() }.filter { it.isNotEmpty() }
    }
}
