package me.kafuuneko.rpclient.libs.character

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 角色扩展档案：感情经历、亲密关系边界与基础身份信息。
 *
 * 设计约束：
 * - 全部字段都存放在角色卡 `extensions` 的自定义命名空间内，不新增数据库列，
 *   因此导入导出 V2 角色卡时这些信息会随卡一起携带，也不会破坏 SillyTavern 的既有字段。
 * - 所有字段均为可选项，未填写时不参与 Prompt 构建，避免向模型注入空描述。
 * - 布尔字段使用三态（是/否/未设定），以便区分「明确没有」与「作者未填写」。
 */
data class CharacterProfileExtra(
    /** 年龄描述，允许「外表二十出头」这类非数值表达。 */
    val age: String = "",
    /** 性别或性别认同。 */
    val gender: String = "",
    /** 职业或身份。 */
    val occupation: String = "",
    /** 性取向描述。 */
    val orientation: String = "",
    /** 当前感情状态。 */
    val relationshipStatus: RelationshipStatus = RelationshipStatus.Unspecified,
    /** 是否有恋爱史；null 表示作者未设定。 */
    val hasRomanticHistory: Boolean? = null,
    /** 恋爱史正文，可记录前任、分手原因、当前纠葛等。 */
    val romanticHistory: String = "",
    /** 是否有性经历；null 表示作者未设定。 */
    val hasSexualHistory: Boolean? = null,
    /** 性经历正文，用于记录角色对此的态度与边界。 */
    val sexualHistory: String = "",
    /** 亲密关系边界与禁忌，供模型在扮演时遵守。 */
    val boundaries: String = "",
    /** 喜好。 */
    val likes: String = "",
    /** 厌恶。 */
    val dislikes: String = "",
    /** 其他补充设定。 */
    val extraNotes: String = ""
) {
    /** 是否至少填写了一项内容；用于决定是否写入 extensions 与 Prompt。 */
    val hasContent: Boolean
        get() = age.isNotBlank() ||
                gender.isNotBlank() ||
                occupation.isNotBlank() ||
                orientation.isNotBlank() ||
                relationshipStatus != RelationshipStatus.Unspecified ||
                hasRomanticHistory != null ||
                romanticHistory.isNotBlank() ||
                hasSexualHistory != null ||
                sexualHistory.isNotBlank() ||
                boundaries.isNotBlank() ||
                likes.isNotBlank() ||
                dislikes.isNotBlank() ||
                extraNotes.isNotBlank()
}

/**
 * 角色扩展档案中可通过单行或多行文本编辑的字段。
 *
 * 与 [CharacterProfileExtra] 的文本属性一一对应，供 MVI Intent 以枚举而非字段名字符串定位，
 * 避免界面与数据层之间出现拼写漂移。
 */
enum class CharacterProfileTextField {
    Age,
    Gender,
    Occupation,
    Orientation,
    RomanticHistory,
    SexualHistory,
    Boundaries,
    Likes,
    Dislikes,
    ExtraNotes
}

/** 角色扩展档案中采用三态（有/无/未设定）编辑的布尔字段。 */
enum class CharacterProfileFlagField {
    HasRomanticHistory,
    HasSexualHistory
}

/**
 * 按枚举定位并替换扩展档案中的文本字段。
 *
 * @param field 目标字段
 * @param value 新的文本值
 * @return 替换后的档案副本，未匹配字段时原样返回
 */
fun CharacterProfileExtra.withProfileText(
    field: CharacterProfileTextField,
    value: String
): CharacterProfileExtra = when (field) {
    CharacterProfileTextField.Age -> copy(age = value)
    CharacterProfileTextField.Gender -> copy(gender = value)
    CharacterProfileTextField.Occupation -> copy(occupation = value)
    CharacterProfileTextField.Orientation -> copy(orientation = value)
    CharacterProfileTextField.RomanticHistory -> copy(romanticHistory = value)
    CharacterProfileTextField.SexualHistory -> copy(sexualHistory = value)
    CharacterProfileTextField.Boundaries -> copy(boundaries = value)
    CharacterProfileTextField.Likes -> copy(likes = value)
    CharacterProfileTextField.Dislikes -> copy(dislikes = value)
    CharacterProfileTextField.ExtraNotes -> copy(extraNotes = value)
}

/**
 * 按枚举定位并替换扩展档案中的三态布尔字段。
 *
 * @param field 目标字段
 * @param value true 表示有，false 表示无，null 表示未设定
 * @return 替换后的档案副本
 */
fun CharacterProfileExtra.withProfileFlag(
    field: CharacterProfileFlagField,
    value: Boolean?
): CharacterProfileExtra = when (field) {
    CharacterProfileFlagField.HasRomanticHistory -> copy(hasRomanticHistory = value)
    CharacterProfileFlagField.HasSexualHistory -> copy(hasSexualHistory = value)
}

/** 角色当前感情状态；[persistedValue] 用于 extensions 与界面状态的稳定序列化。 */
enum class RelationshipStatus(val persistedValue: String) {
    Unspecified("unspecified"),
    Single("single"),
    Dating("dating"),
    Married("married"),
    Divorced("divorced"),
    Widowed("widowed"),
    Complicated("complicated");

    companion object {
        /** 按持久化值反查状态，未知值回退为未设定，保证旧卡或手改 JSON 不会导致崩溃。 */
        fun fromPersistedValue(value: String?): RelationshipStatus {
            return entries.firstOrNull { it.persistedValue == value } ?: Unspecified
        }
    }
}

/**
 * 角色扩展档案与角色卡 `extensions` 之间的编解码器。
 *
 * - 读写固定在 `extensions.rpclient_profile` 命名空间，避免与 SillyTavern 及其他前端扩展冲突。
 * - 解析失败时返回空档案而不是抛出异常，损坏的第三方字段不应阻断角色卡导入。
 * - 写入时保留 extensions 中其余字段原样，确保导入再导出不丢数据。
 *
 * @param mGson 项目统一使用的 Gson 实例
 */
class CharacterProfileCodec(
    private val mGson: Gson
) {
    /**
     * 从角色卡 extensions JSON 中读取扩展档案。
     *
     * @param extensionsJson 角色卡 `data.extensions` 的原始 JSON 文本
     * @return 解析出的档案；字段缺失或 JSON 损坏时返回空档案
     */
    fun extract(extensionsJson: String): CharacterProfileExtra {
        val profile = runCatching {
            JsonParser.parseString(extensionsJson)
                .asJsonObject
                .getAsJsonObject(NAMESPACE)
        }.getOrNull() ?: return CharacterProfileExtra()
        return CharacterProfileExtra(
            age = profile.optText("age"),
            gender = profile.optText("gender"),
            occupation = profile.optText("occupation"),
            orientation = profile.optText("orientation"),
            relationshipStatus = RelationshipStatus.fromPersistedValue(
                profile.optText("relationship_status").takeIf { it.isNotBlank() }
            ),
            hasRomanticHistory = profile.optNullableBoolean("has_romantic_history"),
            romanticHistory = profile.optText("romantic_history"),
            hasSexualHistory = profile.optNullableBoolean("has_sexual_history"),
            sexualHistory = profile.optText("sexual_history"),
            boundaries = profile.optText("boundaries"),
            likes = profile.optText("likes"),
            dislikes = profile.optText("dislikes"),
            extraNotes = profile.optText("extra_notes")
        )
    }

    /**
     * 将扩展档案写回角色卡 extensions JSON。
     *
     * - 档案为空时移除命名空间节点，避免在角色卡里留下空对象。
     * - 其余 extensions 字段原样保留，不覆盖第三方数据。
     *
     * @param extensionsJson 原始 extensions JSON
     * @param profile 待写入的扩展档案
     * @return 更新后的 extensions JSON 文本
     */
    fun inject(extensionsJson: String, profile: CharacterProfileExtra): String {
        val root = runCatching {
            JsonParser.parseString(extensionsJson).asJsonObject
        }.getOrDefault(JsonObject())
        if (!profile.hasContent) {
            root.remove(NAMESPACE)
            return mGson.toJson(root)
        }
        val node = JsonObject()
        node.addProperty("age", profile.age)
        node.addProperty("gender", profile.gender)
        node.addProperty("occupation", profile.occupation)
        node.addProperty("orientation", profile.orientation)
        node.addProperty("relationship_status", profile.relationshipStatus.persistedValue)
        profile.hasRomanticHistory?.let { node.addProperty("has_romantic_history", it) }
        node.addProperty("romantic_history", profile.romanticHistory)
        profile.hasSexualHistory?.let { node.addProperty("has_sexual_history", it) }
        node.addProperty("sexual_history", profile.sexualHistory)
        node.addProperty("boundaries", profile.boundaries)
        node.addProperty("likes", profile.likes)
        node.addProperty("dislikes", profile.dislikes)
        node.addProperty("extra_notes", profile.extraNotes)
        root.add(NAMESPACE, node)
        return mGson.toJson(root)
    }

    /**
     * 将扩展档案转换为可注入角色描述的文本片段。
     *
     * 只输出已填写的字段，并显式标注「未设定」的布尔项不参与生成，
     * 避免模型把空白字段误读为「没有相关经历」。
     *
     * @param profile 扩展档案
     * @return 多行文本片段；档案为空时返回空字符串
     */
    fun toPromptFragment(profile: CharacterProfileExtra): String {
        if (!profile.hasContent) return ""
        val lines = mutableListOf<String>()
        // 基础身份信息
        if (profile.gender.isNotBlank()) lines += "Gender: ${profile.gender}"
        if (profile.age.isNotBlank()) lines += "Age: ${profile.age}"
        if (profile.occupation.isNotBlank()) lines += "Occupation: ${profile.occupation}"
        if (profile.orientation.isNotBlank()) lines += "Orientation: ${profile.orientation}"
        if (profile.relationshipStatus != RelationshipStatus.Unspecified) {
            lines += "Relationship status: ${profile.relationshipStatus.persistedValue}"
        }
        // 感情经历：明确未设定时不写入，避免模型自行补完
        profile.hasRomanticHistory?.let {
            lines += "Has romantic history: ${if (it) "yes" else "no"}"
        }
        if (profile.romanticHistory.isNotBlank()) {
            lines += "Romantic history: ${profile.romanticHistory}"
        }
        profile.hasSexualHistory?.let {
            lines += "Has sexual history: ${if (it) "yes" else "no"}"
        }
        if (profile.sexualHistory.isNotBlank()) {
            lines += "Sexual history: ${profile.sexualHistory}"
        }
        // 关系边界必须显式呈现，供模型在扮演中遵守
        if (profile.boundaries.isNotBlank()) lines += "Boundaries: ${profile.boundaries}"
        if (profile.likes.isNotBlank()) lines += "Likes: ${profile.likes}"
        if (profile.dislikes.isNotBlank()) lines += "Dislikes: ${profile.dislikes}"
        if (profile.extraNotes.isNotBlank()) lines += "Additional notes: ${profile.extraNotes}"
        return lines.joinToString("\n")
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

    companion object {
        /** extensions 中的自定义命名空间，避免与其他前端扩展冲突。 */
        const val NAMESPACE = "rpclient_profile"
    }
}
