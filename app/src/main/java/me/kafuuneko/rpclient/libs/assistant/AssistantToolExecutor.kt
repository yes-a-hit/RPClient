package me.kafuuneko.rpclient.libs.assistant

import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.assistant.AssistantToolCatalog.longOrNull
import me.kafuuneko.rpclient.libs.assistant.AssistantToolCatalog.stringList
import me.kafuuneko.rpclient.libs.assistant.AssistantToolCatalog.stringOrNull
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.script.ScriptImportOutcome
import me.kafuuneko.rpclient.libs.script.ScriptPackCodec
import me.kafuuneko.rpclient.libs.gallery.GalleryStore
import me.kafuuneko.rpclient.libs.script.ScriptStore

/**
 * 助手工具执行器：在受限沙箱内完成文件操作与角色卡编辑。
 *
 * 权限边界只有两条：
 * - 文件路径经规范化后必须落在沙箱根目录内部，越界请求直接拒绝。
 * - 角色卡只能通过 Repository 修改白名单字段，删除角色与修改绑定关系不在授权范围内。
 *
 * 所有失败都转成可直接回填给模型的中文提示，不抛异常打断对话。
 */
class AssistantToolExecutor(
    private val mSandboxRoot: File,
    private val mCharacterRepository: CharacterRepository,
    private val mLorebookRepository: LorebookRepository,
    private val mScriptStore: ScriptStore,
    private val mGalleryStore: GalleryStore
) {

    /** 剧本编解码器，仅用于把已有剧本回填给模型查看。 */
    private val mScriptCodec = ScriptPackCodec()
    /** 执行一次调用，返回回填给模型的文本结果。 */
    suspend fun execute(call: AssistantToolCall): String = withContext(Dispatchers.IO) {
        runCatching {
            when (call.name) {
                "list_files" -> listFiles(call.arguments)
                "read_file" -> readFile(call.arguments)
                "write_file" -> writeFile(call.arguments)
                "move_file" -> moveFile(call.arguments)
                "delete_file" -> deleteFile(call.arguments)
                "list_characters" -> listCharacters()
                "get_character" -> getCharacter(call.arguments)
                "create_character" -> createCharacter(call.arguments)
                "update_character" -> updateCharacter(call.arguments)
                "list_worldbooks" -> listWorldbooks()
                "create_worldbook" -> createWorldbook(call.arguments)
                "add_worldbook_entry" -> addWorldbookEntry(call.arguments)
                "get_worldbook_entries" -> getWorldbookEntries(call.arguments)
                "list_scripts" -> listScripts()
            "list_gallery" -> listGallery()
            "send_image" -> sendImage(call.arguments)
            "add_image_label" -> addImageLabel(call.arguments)
                "get_script" -> getScript(call.arguments)
                "create_script" -> createScript(call.arguments)
                else -> "未知工具：${call.name}"
            }
        }.getOrElse { error ->
            "工具执行失败：${error.message ?: error::class.simpleName}"
        }
    }

    /** 列出沙箱目录；路径越界或不存在时返回说明文本。 */
    private fun listFiles(arguments: JsonObject): String {
        val target = resolveSandboxPath(arguments.stringOrNull("path") ?: ".") ?: return OUT_OF_SANDBOX
        if (!target.exists()) return "路径不存在：${relativePath(target)}"
        if (!target.isDirectory) return "不是目录：${relativePath(target)}"
        val entries = target.listFiles()?.sortedBy { it.name } ?: emptyList()
        if (entries.isEmpty()) return "目录为空：${relativePath(target)}"
        val lines = entries.map { entry ->
            val kind = if (entry.isDirectory) "dir " else "file"
            val size = if (entry.isDirectory) "-" else entry.length().toString()
            "$kind\t$size\t${entry.name}"
        }
        return "目录 ${relativePath(target)} 共 ${entries.size} 项：\n" + lines.joinToString("\n")
    }

    /** 读取文本文件；超长内容按上限截断并注明。 */
    private fun readFile(arguments: JsonObject): String {
        val path = arguments.stringOrNull("path") ?: return "缺少参数 path"
        val target = resolveSandboxPath(path) ?: return OUT_OF_SANDBOX
        if (!target.isFile) return "文件不存在：$path"
        if (target.length() > AssistantToolCatalog.MAX_FILE_BYTES) {
            return "文件过大（${target.length()} 字节），超过读取上限。"
        }
        val text = target.readText()
        if (text.length <= AssistantToolCatalog.MAX_READ_CHARS) return text
        return text.take(AssistantToolCatalog.MAX_READ_CHARS) +
            "\n…（已截断，原文件 ${text.length} 字符）"
    }

    /** 写入文本文件，自动创建父目录。 */
    private fun writeFile(arguments: JsonObject): String {
        val path = arguments.stringOrNull("path") ?: return "缺少参数 path"
        val content = arguments.stringOrNull("content") ?: return "缺少参数 content"
        if (content.toByteArray().size > AssistantToolCatalog.MAX_FILE_BYTES) {
            return "内容超过写入上限 ${AssistantToolCatalog.MAX_FILE_BYTES} 字节。"
        }
        val target = resolveSandboxPath(path) ?: return OUT_OF_SANDBOX
        if (target.isDirectory) return "目标是目录，无法写入：$path"
        target.parentFile?.mkdirs()
        target.writeText(content)
        return "已写入 ${relativePath(target)}（${content.length} 字符）"
    }

    /** 移动或重命名文件，源与目标都必须位于沙箱内。 */
    private fun moveFile(arguments: JsonObject): String {
        val from = arguments.stringOrNull("from") ?: return "缺少参数 from"
        val to = arguments.stringOrNull("to") ?: return "缺少参数 to"
        val source = resolveSandboxPath(from) ?: return "源路径越界：$from"
        val destination = resolveSandboxPath(to) ?: return "目标路径越界：$to"
        if (!source.exists()) return "源文件不存在：$from"
        if (destination.exists()) return "目标已存在，未覆盖：$to"
        destination.parentFile?.mkdirs()
        if (!source.renameTo(destination)) return "移动失败：$from -> $to"
        return "已移动 ${relativePath(source)} -> ${relativePath(destination)}"
    }

    /** 删除文件；目录不在此工具的授权范围内。 */
    private fun deleteFile(arguments: JsonObject): String {
        val path = arguments.stringOrNull("path") ?: return "缺少参数 path"
        val target = resolveSandboxPath(path) ?: return OUT_OF_SANDBOX
        if (!target.exists()) return "文件不存在：$path"
        if (target.isDirectory) return "该工具只删除文件，目录请逐个清理。"
        return if (target.delete()) "已删除 ${relativePath(target)}" else "删除失败：$path"
    }

    /** 列出全部角色卡的精简信息，供模型挑选要编辑的对象。 */
    private suspend fun listCharacters(): String {
        val characters = mCharacterRepository.getAllCharacters()
        if (characters.isEmpty()) return "当前没有任何角色卡。"
        val lines = characters.map { character ->
            "id=${character.id}\t${character.name}\t${describe(character)}"
        }
        return "共 ${characters.size} 张角色卡：\n" + lines.joinToString("\n")
    }

    /** 读取单张角色卡的完整字段。 */
    private suspend fun getCharacter(arguments: JsonObject): String {
        val id = arguments.longOrNull("id") ?: return "缺少参数 id"
        val character = mCharacterRepository.getCharacterById(id)
            ?: return "没有 id=$id 的角色卡"
        return listOf(
            "id=${character.id}",
            "name=${character.name}",
            "description=${character.description}",
            "personality=${character.personality}",
            "scenario=${character.scenario}",
            "firstMessages=${character.firstMessages}",
            "postHistoryInstructions=${character.postHistoryInstructions}",
            "creatorNotes=${character.creatorNotes}",
            "systemPrompt=${character.systemPrompt}"
        ).joinToString("\n")
    }

    /** 列出已保存的剧本，含场景数量。 */
    private suspend fun listScripts(): String {
        val items = mScriptStore.list()
        if (items.isEmpty()) return "还没有剧本"
        return items.joinToString("\n") { "${it.id} · ${it.name} · ${it.sceneCount} 个场景" }
    }

    /** 读取指定剧本的完整 JSON，便于模型在其基础上改。 */
    private suspend fun getScript(arguments: JsonObject): String {
        val id = arguments.stringOrNull("id")?.trim().orEmpty()
        if (id.isBlank()) return "缺少参数 id"
        val pack = mScriptStore.load(id) ?: return "剧本不存在：$id"
        return mScriptCodec.encode(pack).take(MAX_SCRIPT_CHARS)
    }

    /**
     * 保存模型生成的剧本 JSON。
     *
     * 校验与落库都交给 ScriptStore：解析失败会返回具体原因，
     * 模型据此修正后重试，避免把半残剧本写进磁盘。
     */
    private suspend fun createScript(arguments: JsonObject): String {
        val json = arguments.stringOrNull("json")?.trim().orEmpty()
        if (json.isBlank()) return "缺少参数 json"
        return when (val outcome = mScriptStore.importFromText(json)) {
            is ScriptImportOutcome.Success ->
                "已保存剧本 ${outcome.packName}（${outcome.sceneCount} 个场景，id=${outcome.packId}）"

            is ScriptImportOutcome.Failure -> "剧本校验失败：${outcome.detail}"
        }
    }

    /** 新建一张角色卡。
     *
     * 只有名称是必填项；其余字段留空时按空字符串落库，之后可以用 update_character 继续补。
     * 头像留空表示使用应用默认头像。
     */
    private suspend fun createCharacter(arguments: JsonObject): String {
        val name = arguments.stringOrNull("name")?.trim().orEmpty()
        if (name.isBlank()) return "缺少参数 name"
        val character = Character(
            name = name,
            avatar = "",
            characterTags = "[]",
            description = arguments.stringOrNull("description")?.trim().orEmpty(),
            personality = arguments.stringOrNull("personality")?.trim().orEmpty(),
            scenario = arguments.stringOrNull("scenario")?.trim().orEmpty(),
            firstMessages = arguments.stringOrNull("firstMessage")?.trim().orEmpty(),
            examplesOfDialogue = arguments.stringOrNull("exampleDialogue")?.trim().orEmpty(),
            postHistoryInstructions = arguments.stringOrNull("postHistoryInstructions")?.trim().orEmpty()
        )
        // 同名就停下，不动已有的那张卡。
        //
        // 之前的写法是「同名则合并」：新值非空就盖掉原字段。
        // 这等于让模型一句话就能改写用户精心写好的角色卡——
        // 而且模型并不知道自己在覆盖东西，它只以为在建新卡。
        //
        // 要改内容请用 update_character 明确指定 id 与字段，
        // 那是双方都知情的一次修改，不是建卡顺手带出来的副作用。
        val existing = runCatching {
            mCharacterRepository.getAllCharacters().firstOrNull { it.name == name }
        }.getOrNull()
        if (existing != null) {
            return buildString {
                append("已存在同名角色卡「")
                append(name)
                append("」（id=")
                append(existing.id)
                append("），本次未创建，也没有改动它。")
                append("如果要改这张卡，请用 update_character 指定 id=")
                append(existing.id)
                append(" 和 fields；如果要另建一个，请换个名字。")
            }
        }
        val id = mCharacterRepository.saveCharacter(character)
        return "已创建角色卡 id=$id（名称：$name）"
    }

    /** 按白名单字段更新角色卡；未提供的字段保持原值。 */
    private suspend fun updateCharacter(arguments: JsonObject): String {
        val id = arguments.longOrNull("id") ?: return "缺少参数 id"
        val fields = arguments.get("fields")
        if (fields == null || !fields.isJsonObject) return "缺少对象参数 fields"
        val patch = fields.asJsonObject
        val current = mCharacterRepository.getCharacterById(id)
            ?: return "没有 id=$id 的角色卡"

        val updated = current.copy(
            name = patch.stringOrNull("name") ?: current.name,
            description = patch.stringOrNull("description") ?: current.description,
            personality = patch.stringOrNull("personality") ?: current.personality,
            scenario = patch.stringOrNull("scenario") ?: current.scenario,
            firstMessages = patch.stringOrNull("firstMessages") ?: current.firstMessages,
            postHistoryInstructions = patch.stringOrNull("postHistoryInstructions")
                ?: current.postHistoryInstructions,
            creatorNotes = patch.stringOrNull("creatorNotes") ?: current.creatorNotes,
            systemPrompt = patch.stringOrNull("systemPrompt") ?: current.systemPrompt
        )
        if (updated == current) return "没有需要修改的字段。"
        mCharacterRepository.updateCharacter(updated)
        val changed = changedFields(current, updated)
        return "已更新角色卡 id=$id，修改字段：${changed.joinToString(", ")}"
    }

    /** 计算实际发生变化的字段名，便于模型确认结果。 */
    private fun changedFields(before: Character, after: Character): List<String> = buildList {
        if (before.name != after.name) add("name")
        if (before.description != after.description) add("description")
        if (before.personality != after.personality) add("personality")
        if (before.scenario != after.scenario) add("scenario")
        if (before.firstMessages != after.firstMessages) add("firstMessages")
        if (before.postHistoryInstructions != after.postHistoryInstructions) add("postHistoryInstructions")
        if (before.creatorNotes != after.creatorNotes) add("creatorNotes")
        if (before.systemPrompt != after.systemPrompt) add("systemPrompt")
    }

    /** 列出全部世界书及条目数量，供助手判断新建还是追加。 */
    private suspend fun listWorldbooks(): String {
        val lorebooks = mLorebookRepository.getAllLorebooks()
        if (lorebooks.isEmpty()) return "当前没有任何世界书。"
        val counts = mLorebookRepository.getLorebookEntryCounts()
        val lines = lorebooks.map { lorebook ->
            "id=${lorebook.id}\t${lorebook.name}\t${counts[lorebook.id] ?: 0} 条"
        }
        return "共 ${lorebooks.size} 本世界书：\n" + lines.joinToString("\n")
    }

    /** 新建一本空世界书，返回 ID 供后续追加条目。 */
    private suspend fun createWorldbook(arguments: JsonObject): String {
        val name = arguments.stringOrNull("name")?.trim().orEmpty()
        if (name.isBlank()) return "缺少参数 name"
        val id = mLorebookRepository.createLorebook(name)
        return "已创建世界书 id=$id，名称：$name"
    }

    /** 向指定世界书追加一条条目；没有关键词时写入常驻条目。 */
    private suspend fun addWorldbookEntry(arguments: JsonObject): String {
        val worldbookId = arguments.longOrNull("worldbookId") ?: return "缺少参数 worldbookId"
        val name = arguments.stringOrNull("name")?.trim().orEmpty()
        if (name.isBlank()) return "缺少参数 name"
        val content = arguments.stringOrNull("content")?.trim().orEmpty()
        if (content.isBlank()) return "缺少参数 content"
        val lorebook = mLorebookRepository.getLorebookById(worldbookId)
            ?: return "没有 id=$worldbookId 的世界书"
        val keys = arguments.stringList("keys")
        // 关键词先留空，写入后由仓库接口统一序列化，避免两处格式不一致
        val entry = LorebookEntry(
            lorebookId = worldbookId,
            name = name,
            keywords = "[]",
            secondaryKeywords = "[]",
            constant = keys.isEmpty(),
            order = DEFAULT_ENTRY_ORDER,
            depth = DEFAULT_ENTRY_DEPTH,
            category = "[]",
            content = content
        )
        val entryId = mLorebookRepository.saveEntry(entry)
        if (keys.isNotEmpty()) mLorebookRepository.updateEntryKeywords(entryId, keys)
        val scope = if (keys.isEmpty()) "常驻条目" else "关键词：${keys.joinToString("、")}"
        return "已写入世界书 ${lorebook.name}：$name（$scope）"
    }

    /** 读取世界书已有条目，避免助手重复写入同一设定。 */
    private suspend fun getWorldbookEntries(arguments: JsonObject): String {
        val worldbookId = arguments.longOrNull("worldbookId") ?: return "缺少参数 worldbookId"
        val lorebook = mLorebookRepository.getLorebookById(worldbookId)
            ?: return "没有 id=$worldbookId 的世界书"
        val entries = mLorebookRepository.getEntriesByLorebookId(worldbookId)
        if (entries.isEmpty()) return "世界书 ${lorebook.name} 还没有条目。"
        val lines = entries.map { entry ->
            val keywords = mLorebookRepository.getEntryKeywords(entry.id)
            "id=${entry.id}\t${entry.name}\t${keywords.joinToString("、").ifBlank { "常驻" }}"
        }
        return "世界书 ${lorebook.name} 共 ${entries.size} 条：\n" + lines.joinToString("\n")
    }

    /** 角色列表里的一句话摘要，避免把整张卡灌进上下文。 */    private fun describe(character: Character): String {
        val source = character.description.ifBlank { character.personality }
        val flat = source.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= 60) flat else flat.take(60) + "…"
    }

    /** 把相对路径解析为沙箱内的绝对文件，越界返回 null。 */
    private fun resolveSandboxPath(raw: String): File? {
        val root = mSandboxRoot.canonicalFile
        root.mkdirs()
        val candidate = File(root, raw).canonicalFile
        val inside = candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)
        return if (inside) candidate else null
    }

    /** 相对沙箱根的展示路径。 */
    private fun relativePath(file: File): String {
        val root = mSandboxRoot.canonicalFile.path
        val path = file.canonicalFile.path
        if (path == root) return "."
        return path.removePrefix(root + File.separator)
    }

    private companion object {
        /** 单次回填给模型的剧本 JSON 上限，避免撑爆上下文。 */
        const val MAX_SCRIPT_CHARS = 12000

        const val OUT_OF_SANDBOX = "拒绝访问：路径超出沙箱范围。"

        /** 助手写入条目的默认排序权重，与界面新建条目的初值一致。 */
        const val DEFAULT_ENTRY_ORDER = 100

        /** 助手写入条目的默认插入深度。 */
        const val DEFAULT_ENTRY_DEPTH = 4
    }


    /** 列出图库图片，供模型挑选。 */
    private suspend fun listGallery(): String {
        val images = mGalleryStore.list()
        if (images.isEmpty()) return "图库是空的。"
        return images.joinToString("\n") { "- ${it.id}｜${it.toBrief()}" }
    }

    /**
     * 按标签或名称挑一张图发出去。
     *
     * 命中多张时取第一张（列表按加入时间倒序，新图优先），
     * 并把图片标记写回结果，界面据此渲染。
     */
    private suspend fun sendImage(arguments: JsonObject): String {
        val keyword = arguments.get("keyword")?.asString.orEmpty()
        val characterName = arguments.get("character")?.asString.orEmpty()
        val hits = mGalleryStore.search(keyword, characterName)
        if (hits.isEmpty()) {
            return "图库里没有匹配「${keyword.ifBlank { "任意" }}」的图片。"
        }
        val picked = hits.first()
        return "已发送图片：${picked.name}" + nlLiteral() +
            "<image uuid=\"${picked.fileUuid}\" name=\"${picked.name}\" />"
    }

    /** 给图片追加标签。 */
    private suspend fun addImageLabel(arguments: JsonObject): String {
        val id = arguments.get("id")?.asString.orEmpty()
        val image = mGalleryStore.find(id) ?: return "找不到图片：$id"
        val extra = arguments.getAsJsonArray("labels")
            ?.mapNotNull { it.asString }
            .orEmpty()
        if (extra.isEmpty()) return "没有需要追加的标签。"
        val merged = (image.labels + extra).distinct()
        mGalleryStore.save(image.copy(labels = merged))
        return "已更新标签：${merged.joinToString("、")}"
    }

    /** 换行符，避免在拼接里反复写转义。 */
    private fun nlLiteral(): String = "\n"

}
