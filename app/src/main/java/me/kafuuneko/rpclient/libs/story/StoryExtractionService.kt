package me.kafuuneko.rpclient.libs.story

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.toConfig
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository

/** 一次设定提取的结果统计。 */
data class StoryExtractionResult(
    /** 新建的角色卡数量。 */
    val characterCount: Int,
    /** 新建的世界书条目数量。 */
    val entryCount: Int,
    /** 新建世界书的名称；没有条目时为空。 */
    val worldbookName: String
)

/**
 * 从小说正文提取角色与世界书。
 *
 * 只做一次非流式请求，把节选交给模型并要求严格 JSON 输出，
 * 再落成角色卡与世界书条目。提取结果直接进入全局库，
 * 用户创建故事时可以在角色与世界书列表里勾选关联。
 *
 * - 只读前若干章，避免长篇小说把上下文打满。
 * - 模型输出的 JSON 允许被 Markdown 围栏包裹，解析前统一剥离。
 * - 任何一步失败都不写入半成品，异常抛给调用方展示。
 */
class StoryExtractionService(
    private val mProviderSelectionResolver: LLMProviderSelectionResolver,
    private val mClientFactory: LLMClientFactory,
    private val mCharacterRepository: CharacterRepository,
    private val mLorebookRepository: LorebookRepository
) {
    /**
     * 提取并保存角色卡与世界书。
     *
     * @param chapters 已解析的章节列表
     * @param storyTitle 故事标题，用作世界书默认名称
     * @return 实际写入的数量；模型没有返回可用内容时数量为 0
     */
    suspend fun extract(chapters: List<NovelChapter>, storyTitle: String): StoryExtractionResult =
        withContext(Dispatchers.IO) {
            val sample = buildSample(chapters)
            if (sample.isBlank()) return@withContext StoryExtractionResult(0, 0, "")

            val provider = mProviderSelectionResolver.requireDefaultProvider()
            val client = mClientFactory.create(provider.toConfig())
            val response = client.generate(
                LLMGenerationRequest(
                    messages = listOf(
                        LLMMessage(LLMMessageRole.System, SYSTEM_PROMPT),
                        LLMMessage(LLMMessageRole.User, sample)
                    ),
                    model = provider.model
                )
            )
            val parsed = parseExtraction(response.content)
                ?: return@withContext StoryExtractionResult(0, 0, "")

            val characterCount = saveCharacters(parsed)
            val (entryCount, worldbookName) = saveWorldbook(parsed, storyTitle)
            StoryExtractionResult(
                characterCount = characterCount,
                entryCount = entryCount,
                worldbookName = worldbookName
            )
        }

    /** 拼接前几章正文作为分析样本，并截断到字符上限。 */
    private fun buildSample(chapters: List<NovelChapter>): String {
        val builder = StringBuilder()
        for (chapter in chapters) {
            if (builder.length >= MAX_SAMPLE_CHARS) break
            if (chapter.title.isNotBlank()) builder.append(chapter.title).append('\n')
            builder.append(chapter.content).append("\n\n")
        }
        return builder.toString().take(MAX_SAMPLE_CHARS)
    }

    /** 剥离代码围栏后解析 JSON；结构不符时返回 null。 */
    private fun parseExtraction(raw: String): JsonObject? {
        val trimmed = raw.trim()
        val jsonText = if (trimmed.startsWith("```")) {
            trimmed.removePrefix("```json").removePrefix("```").substringBeforeLast("```").trim()
        } else {
            // 模型偶尔会在 JSON 前后加解释，取第一个大括号到最后一个大括号
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
        }
        return runCatching { Gson().fromJson(jsonText, JsonObject::class.java) }.getOrNull()
    }

    /** 逐条写入角色卡，返回成功数量。 */
    private suspend fun saveCharacters(root: JsonObject): Int {
        val array = root.getAsJsonArray("characters") ?: return 0
        var saved = 0
        for (element in array) {
            val item = runCatching { element.asJsonObject }.getOrNull() ?: continue
            val name = item.stringOrBlank("name")
            if (name.isBlank()) continue
            // 空字段统一落空串，后续可在角色编辑页补全
            mCharacterRepository.saveCharacter(
                Character(
                    name = name,
                    avatar = "",
                    characterTags = "[]",
                    description = item.stringOrBlank("description"),
                    personality = item.stringOrBlank("personality"),
                    scenario = item.stringOrBlank("scenario"),
                    firstMessages = item.stringOrBlank("firstMessage"),
                    examplesOfDialogue = "",
                    postHistoryInstructions = ""
                )
            )
            saved++
        }
        return saved
    }

    /** 新建一本世界书并写入条目，返回条目数与书名。 */
    private suspend fun saveWorldbook(root: JsonObject, storyTitle: String): Pair<Int, String> {
        val worldbook = runCatching { root.getAsJsonObject("worldbook") }.getOrNull() ?: return 0 to ""
        val entries = worldbook.getAsJsonArray("entries") ?: return 0 to ""
        if (entries.isEmpty) return 0 to ""
        val name = worldbook.stringOrBlank("name").ifBlank { "${storyTitle}设定" }
        val worldbookId = mLorebookRepository.createLorebook(name)
        var saved = 0
        for (element in entries) {
            val item = runCatching { element.asJsonObject }.getOrNull() ?: continue
            val entryName = item.stringOrBlank("name")
            val content = item.stringOrBlank("content")
            if (entryName.isBlank() || content.isBlank()) continue
            // 关键词先留空，写入后交给仓库统一序列化
            val entryId = mLorebookRepository.saveEntry(
                LorebookEntry(
                    lorebookId = worldbookId,
                    name = entryName,
                    keywords = "[]",
                    secondaryKeywords = "[]",
                    constant = false,
                    order = 100,
                    depth = 4,
                    category = "[]",
                    content = content
                )
            )
            val keys = item.getAsJsonArray("keys")
                ?.mapNotNull { it.asString?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()
            if (keys.isNotEmpty()) mLorebookRepository.updateEntryKeywords(entryId, keys)
            saved++
        }
        return saved to name
    }

    /** 读取字符串字段；缺失或类型不符时返回空串。 */
    private fun JsonObject.stringOrBlank(key: String): String {
        val element = get(key) ?: return ""
        if (!element.isJsonPrimitive) return ""
        return runCatching { element.asString }.getOrDefault("").trim()
    }

    private companion object {
        /** 送去分析的正文上限，约等于两章长度。 */
        const val MAX_SAMPLE_CHARS = 8000

        /** 固定指令：要求严格 JSON，避免模型输出解释性文字。 */
        val SYSTEM_PROMPT = """
你是小说设定整理助手。下面会给出一部小说的正文节选，请提取其中的角色与世界观设定。

输出要求：
- 只输出一个 JSON 对象，不要解释、不要代码块以外的文字。
- 结构如下：
{"characters":[{"name":"角色名","description":"外貌与身份","personality":"性格","scenario":"出场情境","firstMessage":"一句示例台词"}],
 "worldbook":{"name":"设定集名称","entries":[{"name":"条目名","keys":["触发关键词"],"content":"设定正文"}]}}
- 角色最多 6 个，只保留在节选中反复出现的主要人物。
- 世界书条目最多 8 条，覆盖地点、势力、专有名词、规则设定。
- 每条 content 控制在 200 字以内，写清与剧情相关的关键信息。
- 原文没有依据的内容不要编造，字段留空字符串。
""".trimIndent()
    }
}
