package me.kafuuneko.rpclient.libs.imagegen

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** 图片生成服务使用的请求协议；持久化采用稳定标识，不依赖枚举顺序。 */
enum class ImageGenProtocol(val persistedValue: String) {
    /** OpenAI `/images/generations` 兼容协议，多数第三方聚合服务均支持。 */
    OpenAIImages("openai_images");

    companion object {
        /** 按持久化标识恢复协议；未知值回落到默认协议。 */
        fun fromPersistedValue(value: String?): ImageGenProtocol =
            entries.firstOrNull { it.persistedValue == value } ?: OpenAIImages
    }
}

/** 生图接口可选画幅；仅作为请求参数，实际支持范围由服务端决定。 */
enum class ImageGenAspect(val persistedValue: String, val size: String) {
    Square("square", "1024x1024"),
    Portrait("portrait", "1024x1536"),
    Landscape("landscape", "1536x1024");

    companion object {
        /** 按持久化标识恢复画幅；未知值回落到方形。 */
        fun fromPersistedValue(value: String?): ImageGenAspect =
            entries.firstOrNull { it.persistedValue == value } ?: Square
    }
}

/** 生图请求失败时用于界面提示的错误类型。 */
sealed class ImageGenFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 尚未配置服务地址或模型名。 */
    class NotConfigured : ImageGenFailure("Image generation is not configured")

    /** 服务端返回了非 2xx 状态码。 */
    class HttpStatus(val statusCode: Int, detail: String) :
        ImageGenFailure("Image generation failed with HTTP $statusCode: $detail")

    /** 响应中既没有内联图片数据也没有可下载链接。 */
    class EmptyResult : ImageGenFailure("The image service returned no image data")

    /** 响应结构无法解析。 */
    class InvalidResponse(detail: String) : ImageGenFailure("Unreadable image response: $detail")
}

/**
 * 图片生成服务的连接与默认参数。
 *
 * 全部字段以一个 JSON 串整体持久化，避免并发请求读到混合配置；解析失败时回落到默认值，
 * 保证损坏的偏好不会阻断聊天页启动。
 */
data class ImageGenSettings(
    /** 服务基础地址，例如 `https://api.openai.com/v1`；不带尾部斜杠。 */
    val baseUrl: String = DEFAULT_BASE_URL,
    /** 接口鉴权用的 API Key；为空时请求不携带 Authorization 头。 */
    val apiKey: String = "",
    /** 服务端模型名。 */
    val model: String = DEFAULT_MODEL,
    /** 默认画幅。 */
    val aspect: ImageGenAspect = ImageGenAspect.Square,
    /** 额外请求头 JSON 对象，用于兼容需要自定义头的服务。 */
    val customHeadersJson: String = "",
    /** 附加到请求体的 JSON 片段，用于补充 `response_format` 等厂商专有字段。 */
    val requestBodyPatchJson: String = "",
    /** 生成提示词的包装模板，可用 `{{prompt}}` 引用用户输入。 */
    val promptTemplate: String = DEFAULT_PROMPT_TEMPLATE,
    /** 请求协议。 */
    val protocol: ImageGenProtocol = ImageGenProtocol.OpenAIImages
) {
    /** 是否具备发起请求的最低配置。 */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()

    /** 序列化为可原子保存的偏好值。 */
    fun encode(): String = Gson().toJson(this)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-image-1"

        /** 默认提示词包装；追加画质约束以减少低分辨率与文字水印类产出。 */
        const val DEFAULT_PROMPT_TEMPLATE = """
{{prompt}}

High quality illustration, coherent composition, no text overlay, no watermark.
"""

        /**
         * 读取偏好值。
         *
         * @param value 已持久化的 JSON 串
         * @return 解析成功的配置；空串或损坏内容返回默认配置
         */
        fun decode(value: String): ImageGenSettings {
            if (value.isBlank()) return ImageGenSettings()
            return runCatching { Gson().fromJson(value, ImageGenSettings::class.java) }
                .getOrNull() ?: ImageGenSettings()
        }
    }
}

/** 单次生图请求。 */
data class ImageGenRequest(
    /** 已包装好的最终提示词正文。 */
    val prompt: String,
    /** 请求画幅。 */
    val aspect: ImageGenAspect,
    /** 期望返回的图片数量；由调用方限制在安全范围内。 */
    val count: Int = 1
)

/** 生图服务返回的单张图片。 */
sealed class ImageGenPayload {
    /** 服务端直接内联返回的图片字节。 */
    class Inline(val bytes: ByteArray, val mimeType: String) : ImageGenPayload()

    /** 需要二次下载的图片链接。 */
    class Remote(val url: String) : ImageGenPayload()
}

/**
 * 合并用户附加的请求体片段。
 *
 * 基础请求体只包含跨厂商通用的 `model`/`prompt`/`n`/`size`，其余字段由用户按服务文档补充，
 * 避免把某一家厂商的默认字段强加给其他兼容服务。
 */
internal fun buildImageRequestBody(
    settings: ImageGenSettings,
    request: ImageGenRequest
): JsonObject {
    val body = JsonObject().apply {
        addProperty("model", settings.model)
        addProperty("prompt", request.prompt)
        addProperty("n", request.count.coerceIn(1, MAX_IMAGES_PER_REQUEST))
        addProperty("size", request.aspect.size)
    }
    val patch = parseJsonObject(settings.requestBodyPatchJson)
    patch?.entrySet()?.forEach { (key, value) -> body.add(key, value) }
    return body
}

/** 解析额外请求头 JSON；空串或非对象内容返回空映射。 */
internal fun parseHeaders(raw: String): Map<String, String> {
    val json = parseJsonObject(raw) ?: return emptyMap()
    return json.entrySet().mapNotNull { (key, value) ->
        val header = value.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        header?.let { key to it }
    }.toMap()
}

/** 安全解析 JSON 对象；内容不是对象时返回 null。 */
private fun parseJsonObject(raw: String): JsonObject? {
    if (raw.isBlank()) return null
    return runCatching {
        JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject
    }.getOrNull()
}

/** 单次请求允许的最大图片数量，与消息附件上限保持一致。 */
internal const val MAX_IMAGES_PER_REQUEST = 4

/** 单张图片允许的最大字节数，超出视为服务端返回异常。 */
internal const val MAX_IMAGE_BYTES = 24L * 1024 * 1024
