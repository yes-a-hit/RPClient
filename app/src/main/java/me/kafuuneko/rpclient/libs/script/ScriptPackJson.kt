package me.kafuuneko.rpclient.libs.script

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * 剧本 JSON 到数据模型的显式映射。
 *
 * 不用 Gson 直接反序列化 data class：Gson 通过 Unsafe 建实例，不会执行 Kotlin 的默认值，
 * JSON 里没写的非空字段会变成 null，取用时直接抛 NPE。这里逐字段读取并给默认值，
 * 缺失字段按「空串 / 零 / false」处理，由上层校验决定是否致命。
 */
internal object ScriptPackJson {
    /** 把 JSON 对象映射成剧本；结构不是对象时返回 null。 */
    fun toPack(root: JsonObject): ScriptPack = ScriptPack(
        formatVersion = root.intOr("formatVersion", ScriptPack.CURRENT_FORMAT_VERSION),
        id = root.stringOr("id"),
        name = root.stringOr("name"),
        description = root.stringOr("description"),
        mode = root.modeOr(ScriptMode.Mainline),
        globalLorebook = root.objOrNull("globalLorebook")?.let(::toLorebook),
        scenes = root.arrayOr("scenes").mapNotNull { toScene(it) },
        startSceneId = root.stringOr("startSceneId"),
        startSceneIds = root.stringListOr("startSceneIds"),
        freeModeAfterClear = root.boolOr("freeModeAfterClear", true),
        enableGm = root.boolOr("enableGm", true),
        enableDice = root.boolOr("enableDice", true),
        openings = root.arrayOr("openings").mapNotNull { toOpening(it) },
        backgroundKey = root.stringOr("backgroundKey"),
        showCompletion = root.boolOr("showCompletion", true),
        shareSettings = root.boolOr("shareSettings", false),
        variables = root.arrayOr("variables").mapNotNull { toVariable(it) }
    )

    /** 映射一条开场白；内容为空时丢弃。 */
    private fun toOpening(element: com.google.gson.JsonElement): ScriptOpening? {
        val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val content = item.stringOr("content")
        if (content.isBlank()) return null
        return ScriptOpening(
            speaker = item.stringOr("speaker"),
            content = content
        )
    }

    /** 把 JSON 对象映射成剧本集。 */
    fun toCollection(root: JsonObject): ScriptCollection = ScriptCollection(
        formatVersion = root.intOr("formatVersion", ScriptPack.CURRENT_FORMAT_VERSION),
        id = root.stringOr("id"),
        name = root.stringOr("name"),
        description = root.stringOr("description"),
        packs = root.arrayOr("packs").mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject?.let(::toPack)
        },
        enableGm = root.boolOr("enableGm", true),
        enableDice = root.boolOr("enableDice", true)
    )

    /** 映射单个场景；缺少 id 的项直接丢弃。 */
    private fun toScene(element: com.google.gson.JsonElement): ScriptScene? {
        val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val id = item.stringOr("id")
        if (id.isBlank()) return null
        return ScriptScene(
            id = id,
            name = item.stringOr("name").ifBlank { id },
            entryPrompt = item.stringOr("entryPrompt"),
            lorebook = item.objOrNull("lorebook")?.let(::toLorebook),
            nextSceneIds = item.stringListOr("nextSceneIds"),
            loopEnabled = item.boolOr("loopEnabled", false),
            checkpoint = item.boolOr("checkpoint", false),
            failConditions = item.arrayOr("failConditions").mapNotNull { toCondition(it) },
            completionCondition = item.objOrNull("completionCondition")?.let(::toCondition),
            cast = item.arrayOr("cast").mapNotNull { toCastMember(it) },
            bannedCast = item.stringListOr("bannedCast"),
            maxRounds = item.intOr("maxRounds", 0).coerceAtLeast(0),
            unlockedBy = item.stringListOr("unlockedBy"),
            layout = item.objOrNull("layout")?.let { layout ->
                SceneLayout(
                    x = layout.floatOr("x", 0f),
                    y = layout.floatOr("y", 0f)
                )
            },
            backgroundKey = item.stringOr("backgroundKey"),
            inheritBackground = item.boolOr("inheritBackground", true),
            suggestedActions = item.stringListOr("suggestedActions"),
            events = item.arrayOr("events").mapNotNull { toEvent(it) },
            unlockCondition = item.objOrNull("unlockCondition")?.let(::toCondition)
        )
    }

    /** 映射一次场景事件；没写名字也没写条件与轮次的项目丢弃。 */
    private fun toEvent(element: com.google.gson.JsonElement): ScriptEvent? {
        val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val atRound = item.intOr("atRound", 0)
        val condition = item.objOrNull("condition")?.let(::toCondition)
        val name = item.stringOr("name")
        if (name.isBlank() && condition == null && atRound <= 0) return null
        return ScriptEvent(
            id = item.stringOr("id"),
            name = name,
            condition = condition,
            atRound = atRound.coerceAtLeast(0),
            addCast = item.stringListOr("addCast"),
            removeCast = item.stringListOr("removeCast"),
            gotoSceneId = item.stringOr("gotoSceneId"),
            narration = item.stringOr("narration"),
            once = item.boolOr("once", true),
            chance = item.intOr("chance", 100).coerceIn(0, 100),
            effects = item.arrayOr("effects").mapNotNull { toEffect(it) }
        )
    }


    /** 映射一个剧本变量；没写名字的丢弃。 */
    private fun toVariable(element: com.google.gson.JsonElement): ScriptVariable? {
        val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val name = item.stringOr("name")
        if (name.isBlank()) return null
        val initial = item.intOr("initial", 0)
        return ScriptVariable(
            name = name,
            initial = initial,
            min = item.intOr("min", Int.MIN_VALUE),
            max = item.intOr("max", Int.MAX_VALUE),
            label = item.stringOr("label"),
            visible = item.boolOr("visible", true)
        )
    }

    /** 映射一条副作用；没写变量名的丢弃。 */
    private fun toEffect(element: com.google.gson.JsonElement): ScriptEffect? {
        val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val variable = item.stringOr("variable")
        if (variable.isBlank()) return null
        // setTo 有写的才算「直接赋值」，没写就按增量
        val setTo = if (item.has("setTo")) item.intOr("setTo", 0) else null
        return ScriptEffect(
            variable = variable,
            delta = item.intOr("delta", 0),
            setTo = setTo
        )
    }

    /** 读取浮点字段。 */
    private fun JsonObject.floatOr(key: String, default: Float): Float =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { primitive ->
            runCatching { primitive.asFloat }.getOrDefault(default)
        } ?: default

    /** 映射出场角色；缺名称的项直接丢弃。 */
    private fun toCastMember(element: com.google.gson.JsonElement): ScriptCastMember? {
        val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val name = item.stringOr("name")
        if (name.isBlank()) return null
        return ScriptCastMember(
            name = name,
            required = item.boolOr("required", true),
            description = item.stringOr("description")
        )
    }

    /** 映射世界书与其条目。 */
    private fun toLorebook(root: JsonObject): ScriptLorebook = ScriptLorebook(
        name = root.stringOr("name"),
        entries = root.arrayOr("entries").mapNotNull { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = item.stringOr("name")
            val content = item.stringOr("content")
            if (name.isBlank() || content.isBlank()) return@mapNotNull null
            ScriptLorebookEntry(
                name = name,
                content = content,
                keys = item.stringListOr("keys"),
                constant = item.boolOr("constant", false)
            )
        }
    )

    /** 映射条件；类型无法识别时返回 null，避免留下永不触发的死条件。 */
    private fun toCondition(element: com.google.gson.JsonElement): ScriptCondition? {
        val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val type = runCatching {
            ScriptConditionType.valueOf(item.stringOr("type"))
        }.getOrNull() ?: return null
        return ScriptCondition(
            type = type,
            pattern = item.stringOr("pattern"),
            threshold = item.intOr("threshold", 0),
            variable = item.stringOr("variable"),
            failSceneId = item.stringOr("failSceneId"),
            description = item.stringOr("description")
        )
    }

    /** 读取字符串字段。 */
    private fun JsonObject.stringOr(key: String, default: String = ""): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { primitive ->
            runCatching { primitive.asString }.getOrDefault(default)
        } ?: default

    /** 读取整数字段。 */
    private fun JsonObject.intOr(key: String, default: Int): Int =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { primitive ->
            runCatching { primitive.asInt }.getOrDefault(default)
        } ?: default

    /** 读取布尔字段。 */
    private fun JsonObject.boolOr(key: String, default: Boolean): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { primitive ->
            runCatching { primitive.asBoolean }.getOrDefault(default)
        } ?: default

    /** 读取字符串数组字段。 */
    private fun JsonObject.stringListOr(key: String): List<String> {
        val array = get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            if (item.isJsonPrimitive) item.asString.trim().takeIf { it.isNotBlank() } else null
        }
    }

    /** 读取对象字段。 */
    private fun JsonObject.objOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    /** 读取数组字段，缺失时返回空数组。 */
    private fun JsonObject.arrayOr(key: String): JsonArray =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

    /** 读取模式枚举，未知取值回落到主线。 */
    private fun JsonObject.modeOr(default: ScriptMode): ScriptMode =
        runCatching { ScriptMode.valueOf(stringOr("mode")) }.getOrDefault(default)
}
