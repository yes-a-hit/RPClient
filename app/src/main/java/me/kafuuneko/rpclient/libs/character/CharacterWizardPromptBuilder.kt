package me.kafuuneko.rpclient.libs.character

/**
 * 角色卡生成向导的输入种子。
 *
 * 用户只需要提供少量起点信息，其余内容交给模型补全；[referenceImageAttached]
 * 表示本次请求已附带参考图，用于在 Prompt 中提示模型优先依据画面描述外貌。
 */
data class CharacterWizardSeed(
    /** 用户指定的角色名；留空时由模型决定。 */
    val name: String = "",
    /** 一句话角色概念或世界观描述。 */
    val concept: String = "",
    /** 额外要求，例如语言、风格、禁忌。 */
    val requirements: String = "",
    /** 是否随请求附带了参考图。 */
    val referenceImageAttached: Boolean = false,
    /** 是否同时生成世界书。 */
    val includeLorebook: Boolean = false
)

/**
 * 角色卡生成向导的 Prompt 构建器。
 *
 * 输出协议固定为单个 JSON 对象，字段名与 Character Card V2 及本地扩展档案保持一致，
 * 使模型输出可以直接映射为编辑表单，而不需要额外的自然语言解析。
 */
class CharacterWizardPromptBuilder {
    /**
     * 构建系统提示词。
     *
     * @param includeLorebook 是否要求模型同时输出世界书条目
     * @return 约束输出格式的系统提示词
     */
    fun buildSystemPrompt(includeLorebook: Boolean): String {
        val lorebookSection = if (includeLorebook) {
            LOREBOOK_SCHEMA
        } else {
            "Do not include a \"lorebook\" field."
        }
        return SYSTEM_PROMPT_TEMPLATE
            .replace("{{lorebook_schema}}", lorebookSection)
    }

    /**
     * 构建用户提示词。
     *
     * 未填写的种子字段不会出现在提示中，避免模型把空字符串当作有效设定。
     *
     * @param seed 向导输入
     * @return 用户提示词
     */
    fun buildUserPrompt(seed: CharacterWizardSeed): String {
        val lines = mutableListOf<String>()
        if (seed.name.isNotBlank()) lines += "Character name: ${seed.name}"
        if (seed.concept.isNotBlank()) lines += "Concept: ${seed.concept}"
        if (seed.requirements.isNotBlank()) lines += "Additional requirements: ${seed.requirements}"
        if (seed.referenceImageAttached) {
            lines += "A reference image is attached. Derive appearance, clothing, and vibe from it, " +
                    "and keep the description consistent with what is visible."
        }
        if (lines.isEmpty()) {
            lines += "Create an original character suitable for immersive roleplay."
        }
        return lines.joinToString("\n")
    }

    private companion object {
        /** 系统提示主体；{{lorebook_schema}} 由调用方按需替换。 */
        const val SYSTEM_PROMPT_TEMPLATE = """
You are a character card author for a roleplay chat application.
Produce one character card as a single JSON object. Output JSON only, with no explanation, no markdown fence, and no trailing text.

Required JSON shape:
{
  "name": "string",
  "description": "string",
  "personality": "string",
  "scenario": "string",
  "first_mes": "string",
  "mes_example": "string",
  "tags": ["string"],
  "profile": {
    "age": "string",
    "gender": "string",
    "occupation": "string",
    "orientation": "string",
    "relationship_status": "unspecified | single | dating | married | divorced | widowed | complicated",
    "has_romantic_history": true | false | null,
    "romantic_history": "string",
    "has_sexual_history": true | false | null,
    "sexual_history": "string",
    "boundaries": "string",
    "likes": "string",
    "dislikes": "string",
    "extra_notes": "string"
  },
  "lorebook": {{lorebook_schema}}
}

Writing rules:
- Write all prose in the same language the user used, unless explicitly requested otherwise.
- "description" should read as a compact character sheet: identity, appearance, speech habits, and current situation.
- "personality" should describe traits and behavioral patterns, not plot events.
- "scenario" should set the opening situation in one short paragraph.
- "first_mes" is the character's opening line, written in first person and in character.
- "mes_example" uses the format "<START>" separated dialogue blocks with {{user}} and {{char}} placeholders.
- "tags" holds 3 to 8 short descriptors.
- Use null for "has_romantic_history" and "has_sexual_history" when the character's past is genuinely undefined; use false when the character explicitly has no such history.
- Keep "boundaries" concrete and behavioral, describing what the character will and will not do in intimate or high-pressure situations.
- Never describe minors in sexual terms, and never produce sexual content involving minors.
"""

        /** 世界书输出结构，仅在用户请求时拼入系统提示。 */
        const val LOREBOOK_SCHEMA = """
{
    "name": "string",
    "entries": [
      { "name": "string", "keys": ["string"], "content": "string" }
    ]
  }
Rules for lorebook entries:
- Each entry covers one self-contained fact, place, faction, or event.
- "keys" holds 2 to 6 trigger keywords that would appear in chat when the entry becomes relevant.
- Produce 4 to 8 entries that support the character without repeating the description."""
    }
}
