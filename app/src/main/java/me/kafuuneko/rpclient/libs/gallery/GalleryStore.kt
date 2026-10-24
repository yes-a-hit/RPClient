package me.kafuuneko.rpclient.libs.gallery

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 图库存储。
 *
 * 每张图一个 JSON 元数据文件，图片本体交给 FileRepository 按 UUID 管理，
 * 这样角色头像、消息图片和图库共用同一套文件清理逻辑。
 */
class GalleryStore(
    private val context: Context,
    private val gson: Gson
) {
    private val root: File
        get() = File(context.filesDir, "gallery").apply { if (!isDirectory) mkdirs() }

    /** 列出全部图片，按加入时间倒序。 */
    suspend fun list(): List<GalleryImage> = withContext(Dispatchers.IO) {
        root.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { readFile(it) }
            .sortedByDescending { it.addedAt }
    }

    /** 按 ID 取一张。 */
    suspend fun find(id: String): GalleryImage? = withContext(Dispatchers.IO) {
        readFile(File(root, "$id.json"))
    }

    /**
     * 取某个角色的外观参考图。
     *
     * 这些图不是用来发的，是给模型认脸的——让它知道「自己长这样」，
     * 用户发来同形象的 meme 时才认得出是自己，而不是别的同类。
     *
     * @param characterName 角色名；空表示没有绑定角色，返回空。
     * @param limit 最多取几张。视觉 token 不便宜，默认三张封顶。
     */
    suspend fun appearanceImages(
        characterName: String,
        limit: Int = 3
    ): List<GalleryImage> = withContext(Dispatchers.IO) {
        if (characterName.isBlank()) return@withContext emptyList()
        list()
            .filter { it.isAppearance && it.characterName == characterName }
            .take(limit)
    }

    /**
     * 按标签或名字挑图，供模型自主发图时检索。
     *
     * 匹配顺序：名字精确 > 标签命中 > 说明包含关键字。
     * 角色绑定的图只在 [characterName] 匹配时返回。
     */
    suspend fun search(keyword: String, characterName: String = ""): List<GalleryImage> =
        withContext(Dispatchers.IO) {
            val all = list()
            val key = keyword.trim()
            all.filter { image ->
                val ownable = image.isPublic ||
                    characterName.isBlank() ||
                    image.characterName.isBlank() ||
                    image.characterName == characterName
                if (!ownable) return@filter false
                if (key.isBlank()) return@filter true
                image.name.contains(key, ignoreCase = true) ||
                    image.labels.any { it.contains(key, ignoreCase = true) } ||
                    image.description.contains(key, ignoreCase = true)
            }
        }

    /** 保存或更新一张图的元数据。 */
    suspend fun save(image: GalleryImage): GalleryImage = withContext(Dispatchers.IO) {
        val stored = if (image.id.isBlank()) {
            image.copy(id = UUID.randomUUID().toString(), addedAt = System.currentTimeMillis())
        } else {
            image
        }
        File(root, "${stored.id}.json").writeText(gson.toJson(toJson(stored)))
        stored
    }

    /** 删除一张图及其元数据。 */
    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        File(root, "$id.json").delete()
    }

    /**
     * 从外部 URI 导入一张图。
     *
     * 图片本体交由 [saveFile] 落盘，元数据由调用方补齐标签与角色。
     */
    suspend fun importFromUri(
        uri: Uri,
        saveFile: suspend (Uri) -> String?,
        name: String,
        labels: List<String> = emptyList(),
        characterName: String = "",
        isPublic: Boolean = true,
        description: String = ""
    ): GalleryImage? = withContext(Dispatchers.IO) {
        val uuid = saveFile(uri) ?: return@withContext null
        save(
            GalleryImage(
                id = "",
                name = name.ifBlank { "image" },
                fileUuid = uuid,
                labels = labels,
                characterName = characterName,
                isPublic = isPublic,
                description = description
            )
        )
    }

    private fun readFile(file: File): GalleryImage? {
        if (!file.isFile) return null
        return runCatching {
            val obj = JsonParser.parseString(file.readText()).asJsonObject
            GalleryImage(
                id = obj.get("id")?.asString.orEmpty(),
                name = obj.get("name")?.asString.orEmpty(),
                fileUuid = obj.get("fileUuid")?.asString.orEmpty(),
                labels = obj.getAsJsonArray("labels")
                    ?.mapNotNull { it.asString }
                    .orEmpty(),
                characterName = obj.get("characterName")?.asString.orEmpty(),
                isPublic = obj.get("isPublic")?.asBoolean ?: true,
                description = obj.get("description")?.asString.orEmpty(),
                addedAt = obj.get("addedAt")?.asLong ?: 0L
            )
        }.getOrNull()
    }

    private fun toJson(image: GalleryImage): JsonObject = JsonObject().apply {
        addProperty("id", image.id)
        addProperty("name", image.name)
        addProperty("fileUuid", image.fileUuid)
        add("labels", gson.toJsonTree(image.labels))
        addProperty("characterName", image.characterName)
        addProperty("isPublic", image.isPublic)
        addProperty("description", image.description)
        addProperty("addedAt", image.addedAt)
    }
}
