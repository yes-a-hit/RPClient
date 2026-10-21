package me.kafuuneko.rpclient.libs.script

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException

/** 剧本解析失败的原因分类，供界面给出可操作的提示。 */
enum class ScriptPackError {
    /** 空内容或不是 JSON。 */
    NotJson,

    /** 结构版本高于当前应用支持。 */
    UnsupportedVersion,

    /** 缺少必需字段。 */
    MissingField,

    /** 场景引用关系不成立。 */
    BrokenReference
}

/** 解析结果。 */
sealed class ScriptPackParseResult {
    data class Success(val pack: ScriptPack) : ScriptPackParseResult()
    data class Failure(val error: ScriptPackError, val detail: String) : ScriptPackParseResult()
}

/** 剧本集解析结果。 */
sealed class ScriptCollectionParseResult {
    data class Success(val collection: ScriptCollection) : ScriptCollectionParseResult()
    data class Failure(val error: ScriptPackError, val detail: String) : ScriptCollectionParseResult()
}

/**
 * 剧本 JSON 编解码。
 *
 * 格式约定与解析顺序：
 * - 只接受单个 JSON 对象，允许被 Markdown 代码围栏包裹；
 * - 先校验结构版本，再校验必需字段，最后校验场景引用完整性；
 * - 任何一步失败都返回具体原因，不做静默兜底——剧本解析错一半比直接报错更难排查。
 */
class ScriptPackCodec(private val mGson: Gson = defaultGson()) {
    /** 序列化为便于阅读和手工编辑的 JSON。 */
    fun encode(pack: ScriptPack): String = mGson.toJson(pack)

    /**
     * 解析剧本 JSON。
     *
     * @param raw 用户粘贴或从文件读取的原始文本
     * @return 成功时返回校验过的剧本，失败时返回具体原因
     */
    fun decode(raw: String): ScriptPackParseResult {
        val text = stripCodeFence(raw)
        if (text.isBlank()) return fail(ScriptPackError.NotJson, "内容为空")

        // 先解析成 JsonObject 再显式映射，避免 Gson 直接反序列化 data class 时丢失默认值
        val root = runCatching { mGson.fromJson(text, JsonObject::class.java) }
            .getOrElse { throwable ->
                val reason = if (throwable is JsonSyntaxException) "JSON 语法错误" else "无法解析"
                return fail(ScriptPackError.NotJson, "$reason：${throwable.message.orEmpty()}")
            }
            ?: return fail(ScriptPackError.NotJson, "解析结果为空")

        val pack = ScriptPackJson.toPack(root)

        validate(pack)?.let { (error, detail) -> return fail(error, detail) }
        return ScriptPackParseResult.Success(
            pack.copy(startSceneId = resolvedStartId(pack))
        )
    }

    /**
     * 解析剧本集。
     *
     * 集内每个剧本包都按单包规则完整校验；任一包不合法即整体失败，
     * 避免导入一个半残的剧本集。
     *
     * @param raw 剧本集 JSON 文本
     * @return 成功时返回校验过的剧本集
     */
    fun decodeCollection(raw: String): ScriptCollectionParseResult {
        val text = stripCodeFence(raw)
        if (text.isBlank()) {
            return ScriptCollectionParseResult.Failure(ScriptPackError.NotJson, "内容为空")
        }
        val root = runCatching { mGson.fromJson(text, JsonObject::class.java) }
            .getOrElse { throwable ->
                val reason = if (throwable is JsonSyntaxException) "JSON 语法错误" else "无法解析"
                return ScriptCollectionParseResult.Failure(
                    ScriptPackError.NotJson,
                    "$reason：${throwable.message.orEmpty()}"
                )
            }
            ?: return ScriptCollectionParseResult.Failure(ScriptPackError.NotJson, "解析结果为空")

        val collection = ScriptPackJson.toCollection(root)
        if (collection.id.isBlank() || collection.name.isBlank()) {
            return ScriptCollectionParseResult.Failure(ScriptPackError.MissingField, "缺少 id 或 name")
        }
        if (collection.formatVersion > ScriptPack.CURRENT_FORMAT_VERSION) {
            return ScriptCollectionParseResult.Failure(
                ScriptPackError.UnsupportedVersion,
                "剧本集版本 ${collection.formatVersion} 高于支持的 ${ScriptPack.CURRENT_FORMAT_VERSION}"
            )
        }
        if (collection.packs.isEmpty()) {
            return ScriptCollectionParseResult.Failure(ScriptPackError.MissingField, "剧本集没有任何剧本")
        }
        // 复用单包校验，保证集内每个剧本都能独立运行
        for (pack in collection.packs) {
            val validation = validate(pack)
            if (validation != null) {
                return ScriptCollectionParseResult.Failure(
                    validation.first,
                    "[${pack.id}] ${validation.second}"
                )
            }
        }
        val packIds = collection.packs.map { it.id }
        if (packIds.size != packIds.distinct().size) {
            return ScriptCollectionParseResult.Failure(
                ScriptPackError.BrokenReference,
                "剧本集内存在重复的剧本 ID"
            )
        }
        return ScriptCollectionParseResult.Success(collection)
    }

    /**
     * 校验单个剧本包的完整性。
     *
     * @return 通过时返回 null，否则返回错误类型与可读说明
     */
    private fun validate(pack: ScriptPack): Pair<ScriptPackError, String>? {
        if (pack.formatVersion > ScriptPack.CURRENT_FORMAT_VERSION) {
            return ScriptPackError.UnsupportedVersion to
                "剧本版本 ${pack.formatVersion} 高于支持的 ${ScriptPack.CURRENT_FORMAT_VERSION}"
        }
        if (pack.id.isBlank() || pack.name.isBlank()) {
            return ScriptPackError.MissingField to "缺少 id 或 name"
        }
        if (pack.scenes.isEmpty()) {
            return ScriptPackError.MissingField to "剧本没有任何场景"
        }
        // 场景 ID 必须唯一，否则后继引用会指向不确定的目标
        val ids = pack.scenes.map { it.id }
        if (ids.size != ids.distinct().size) {
            return ScriptPackError.BrokenReference to "场景 ID 存在重复"
        }
        val startId = resolvedStartId(pack)
        if (startId !in ids) {
            return ScriptPackError.BrokenReference to "起始场景 $startId 不存在"
        }
        // 后继场景必须都存在，悬空引用会让主线在中途断掉
        val dangling = pack.scenes.flatMap { it.nextSceneIds }.filterNot { it in ids }
        if (dangling.isNotEmpty()) {
            return ScriptPackError.BrokenReference to
                "后继场景不存在：${dangling.distinct().joinToString(", ")}"
        }
        // 解锁前置与多入口也必须指向真实场景，否则多线剧本会有点永远打不开
        val unresolved = (
            pack.scenes.flatMap { it.unlockedBy } + pack.startSceneIds
            ).filterNot { it in ids }
        if (unresolved.isNotEmpty()) {
            return ScriptPackError.BrokenReference to
                "未定义的前置或入口场景：${unresolved.distinct().joinToString(", ")}"
        }
        // 禁止出场的名字不能同时出现在允许名单里，否则运行时无法判断谁能说话
        val conflicts = pack.scenes.flatMap { scene ->
            val allowed = scene.cast.map { it.name }
            scene.bannedCast.filter { it in allowed }
        }
        if (conflicts.isNotEmpty()) {
            return ScriptPackError.BrokenReference to
                "角色同时出现在允许与禁止名单：${conflicts.distinct().joinToString(", ")}"
        }
        // 后继图不允许出现死循环：环上没有 loopEnabled 场景时直接拒绝，
        // 否则玩家会在一组场景里反复空转，永远走不到终点
        findDeadLoop(pack)?.let { loop ->
            return ScriptPackError.BrokenReference to
                "场景后继构成死循环：${loop.joinToString(" → ")}（如需循环请给环上场景开启 loopEnabled）"
        }
        // 可达性：每个场景都必须能从入口走过去。
        // 常见坏剧本是一堆互不相连的场景，运行时除了第一个其余永远进不去。
        val unreachable = findUnreachableScenes(pack)
        if (unreachable.isNotEmpty()) {
            return ScriptPackError.BrokenReference to
                "这些场景从入口无法到达：${unreachable.joinToString(", ")}" +
                "（用 nextSceneIds 或 unlockedBy 把它们接进来）"
        }
        return null
    }

    /**
     * 找出从入口出发走不到的场景。
     *
     * 后继与解锁关系都算连接：`unlockedBy` 表示「经历过前置才解锁」，
     * 两个方向都意味着玩家能在这两个场景之间移动。
     *
     * @return 不可达场景的 ID；全部可达时返回空列表
     */
    private fun findUnreachableScenes(pack: ScriptPack): List<String> {
        if (pack.scenes.isEmpty()) return emptyList()
        // 自由模式本来就不靠链接推进，玩家想去哪去哪，不做链式可达校验
        if (pack.mode == ScriptMode.Free) return emptyList()
        val entries = buildSet {
            addAll(pack.startSceneIds)
            if (pack.startSceneId.isNotBlank()) add(pack.startSceneId)
            if (isEmpty()) add(pack.scenes.first().id)
        }
        // 构造成邻接表，后继与解锁互为通路
        val adjacency = mutableMapOf<String, MutableSet<String>>()
        pack.scenes.forEach { scene ->
            adjacency.getOrPut(scene.id) { mutableSetOf() }.addAll(scene.nextSceneIds)
            // 事件里的强制转场同样是通路，漏掉会把正常剧本判成孤岛
            scene.events.forEach { event ->
                if (event.gotoSceneId.isNotBlank()) {
                    adjacency.getOrPut(scene.id) { mutableSetOf() }.add(event.gotoSceneId)
                }
            }
            // 战败场景只能从失败条件进，同样算连接
            scene.failConditions.forEach { condition ->
                if (condition.failSceneId.isNotBlank()) {
                    adjacency.getOrPut(scene.id) { mutableSetOf() }.add(condition.failSceneId)
                }
            }
            scene.unlockedBy.forEach { source ->
                adjacency.getOrPut(scene.id) { mutableSetOf() }.add(source)
                adjacency.getOrPut(source) { mutableSetOf() }.add(scene.id)
            }
        }
        val visited = entries.toMutableSet()
        val queue = ArrayDeque(entries)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            adjacency[current].orEmpty().forEach { next ->
                if (visited.add(next)) queue.add(next)
            }
        }
        return pack.scenes
            .filterNot { it.id in visited }
            // 靠状态解锁的场景本来就不该出现在链上：
            // 它的开放条件是变量，不是从哪条路走进来
            .filterNot { it.unlockCondition != null }
            .map { it.id }
    }

    /**
     * 在场景后继图里找一条死循环。
     *
     * 只有环上存在允许循环的场景时才放过，其余情况视为剧本缺陷。
     *
     * @return 构成环的场景 ID 序列；没有死循环时返回 null
     */
    private fun findDeadLoop(pack: ScriptPack): List<String>? {
        val byId = pack.scenes.associateBy { it.id }
        val visited = mutableSetOf<String>()
        val stack = mutableListOf<String>()

        fun walk(sceneId: String): List<String>? {
            if (sceneId in stack) {
                // 环起点到当前位置即为循环路径
                return stack.drop(stack.indexOf(sceneId)) + sceneId
            }
            if (!visited.add(sceneId)) return null
            val scene = byId[sceneId] ?: return null
            stack.add(sceneId)
            for (next in scene.nextSceneIds) {
                val loop = walk(next)
                if (loop != null) {
                    // 环上只要有一个允许循环的场景，就当作有意的设计
                    val loopsAllowed = loop.any { byId[it]?.loopEnabled == true }
                    if (!loopsAllowed) {
                        stack.removeAt(stack.lastIndex)
                        return loop.mapNotNull { byId[it]?.name ?: it }
                    }
                    return null
                }
            }
            stack.removeAt(stack.lastIndex)
            return null
        }

        pack.scenes.forEach { scene ->
            walk(scene.id)?.let { return it }
        }
        return null
    }

    /**
     * 起始场景的归一化结果。
     *
     * 优先取多入口列表的第一个；退而取单值写法；都没有时回落到第一个场景，
     * 保证「只有一个场景」的轻量剧本不用写任何入口字段也能跑。
     */
    private fun resolvedStartId(pack: ScriptPack): String =
        pack.startSceneIds.firstOrNull()
            ?: pack.startSceneId.ifBlank { pack.scenes.first().id }

    /** 去掉模型或文档常见的 Markdown 代码围栏。 */
    private fun stripCodeFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed.removePrefix("```json").removePrefix("```").substringBeforeLast("```").trim()
    }

    private fun fail(error: ScriptPackError, detail: String): ScriptPackParseResult =
        ScriptPackParseResult.Failure(error, detail)

    companion object {
        /** 关闭 HTML 转义，保证中文与引号在导出文件里保持可读。 */
        fun defaultGson(): Gson = GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
    }
}
