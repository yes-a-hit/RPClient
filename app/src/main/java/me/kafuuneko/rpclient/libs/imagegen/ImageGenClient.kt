package me.kafuuneko.rpclient.libs.imagegen

import com.google.gson.JsonParser
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.llm.adapter.JsonMediaType
import me.kafuuneko.rpclient.libs.llm.adapter.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 外接生图服务客户端。
 *
 * 只依赖 OpenAI `/images/generations` 的通用字段，响应同时兼容内联 `b64_json`
 * 与可下载 `url` 两种返回形式，从而覆盖绝大多数第三方聚合服务。
 *
 * @param mOkHttpClient 复用的全局 HTTP 客户端
 */
class ImageGenClient(
    private val mOkHttpClient: OkHttpClient
) {
    /**
     * 发起一次生图请求。
     *
     * @param settings 服务连接与默认参数
     * @param request 单次请求的提示词与画幅
     * @return 按服务端返回顺序排列的图片载荷
     * @throws ImageGenFailure 未配置、状态码异常或响应无法解析
     */
    suspend fun generate(
        settings: ImageGenSettings,
        request: ImageGenRequest
    ): List<ImageGenPayload> {
        if (!settings.isConfigured) throw ImageGenFailure.NotConfigured()
        val endpoint = settings.baseUrl.trimEnd('/') + GENERATIONS_PATH
        val body = buildImageRequestBody(settings, request).toString()
        val httpRequest = Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody(JsonMediaType))
            .apply {
                if (settings.apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${settings.apiKey.trim()}")
                }
                parseHeaders(settings.customHeadersJson).forEach { (name, value) ->
                    header(name, value)
                }
            }
            .build()
        // 网络读取本身是挂起调用，无需再切换到 IO 调度器
        val raw = mOkHttpClient.await(httpRequest)
        return parseResponse(raw)
    }

    /**
     * 下载远程图片链接的内容。
     *
     * 部分服务只返回短期有效的 URL，因此这里立即读取字节，避免消息落库后链接失效。
     *
     * @param url 服务端返回的图片地址
     * @return 图片字节与推断出的 MIME 类型
     */
    suspend fun download(url: String): ImageGenPayload.Inline {
        val httpRequest = Request.Builder().url(url).get().build()
        val call = mOkHttpClient.newCall(httpRequest)
        val response = withContext(Dispatchers.IO) { call.execute() }
        return response.use {
            if (!it.isSuccessful) {
                throw ImageGenFailure.HttpStatus(it.code, it.message)
            }
            val bytes = it.body?.bytes() ?: throw ImageGenFailure.EmptyResult()
            requireBoundedSize(bytes.size.toLong())
            val mime = it.header("Content-Type")?.substringBefore(';')?.takeIf { type ->
                type.startsWith("image/")
            } ?: DEFAULT_MIME_TYPE
            ImageGenPayload.Inline(bytes, mime)
        }
    }

    /** 解析响应体中的 `data` 数组，兼容内联与远程两种载荷。 */
    private fun parseResponse(raw: String): List<ImageGenPayload> {
        val root = runCatching { JsonParser.parseString(raw) }
            .getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw ImageGenFailure.InvalidResponse(raw.take(ERROR_SNIPPET_LENGTH))
        val data = root.getAsJsonArray("data")
            ?: throw ImageGenFailure.InvalidResponse("Missing data array")
        val payloads = data.mapNotNull { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val inline = item.get("b64_json")?.takeIf { it.isJsonPrimitive }?.asString
            if (!inline.isNullOrBlank()) {
                val bytes = runCatching { Base64.getDecoder().decode(inline) }.getOrNull()
                    ?: return@mapNotNull null
                requireBoundedSize(bytes.size.toLong())
                return@mapNotNull ImageGenPayload.Inline(bytes, DEFAULT_MIME_TYPE)
            }
            item.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.isNotBlank() }
                ?.let { ImageGenPayload.Remote(it) }
        }
        if (payloads.isEmpty()) throw ImageGenFailure.EmptyResult()
        return payloads
    }

    /** 拒绝异常大的响应，避免一次性占用过多内存。 */
    private fun requireBoundedSize(size: Long) {
        if (size <= 0L) throw ImageGenFailure.EmptyResult()
        if (size > MAX_IMAGE_BYTES) {
            throw ImageGenFailure.InvalidResponse("Image exceeds the size limit")
        }
    }

    private companion object {
        const val GENERATIONS_PATH = "/images/generations"
        const val DEFAULT_MIME_TYPE = "image/png"
        const val ERROR_SNIPPET_LENGTH = 200
    }
}
