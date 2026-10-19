package me.kafuuneko.rpclient.libs.billing

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.AppModel
import okhttp3.OkHttpClient
import okhttp3.Request

/** 一次报价快照：价格表、抓取时间与来源。 */
data class PricingSnapshot(
    val prices: Map<String, ModelPrice> = emptyMap(),
    val fetchedAt: Long = 0L,
    val sourceUrl: String = PricingRepository.SOURCE_URL
) {
    val isEmpty: Boolean
        get() = prices.isEmpty()

    /** 超过一天视为过期，页面会提示刷新。 */
    val isStale: Boolean
        get() = fetchedAt <= 0L || System.currentTimeMillis() - fetchedAt > STALE_MILLIS

    private companion object {
        const val STALE_MILLIS = 24L * 60 * 60 * 1000
    }
}

/**
 * 模型报价仓库。
 *
 * 报价只有一个来源：OpenRouter 的公开模型列表，它同时收录了 OpenAI、Anthropic、
 * Google、DeepSeek 等主流模型的 ID 与 per-token 单价，因此不必在应用内硬编码价格表。
 * 抓取结果按 JSON 缓存在偏好里，离线时继续使用上次快照。
 */
class PricingRepository(
    private val mOkHttpClient: OkHttpClient,
    private val mGson: Gson
) {
    /** 串行化刷新，避免并发请求覆盖缓存。 */
    private val mRefreshMutex = Mutex()

    /** 读取缓存快照；从未抓取过时返回空快照。 */
    fun cached(): PricingSnapshot {
        val raw = AppModel.pricingSnapshotJson
        if (raw.isBlank()) return PricingSnapshot()
        val type = object : TypeToken<Map<String, ModelPrice>>() {}.type
        val prices = runCatching { mGson.fromJson<Map<String, ModelPrice>>(raw, type) }
            .getOrNull()
            ?: return PricingSnapshot()
        return PricingSnapshot(
            prices = prices,
            fetchedAt = AppModel.pricingFetchedAt,
            sourceUrl = AppModel.pricingSourceUrl.ifBlank { SOURCE_URL }
        )
    }

    /**
     * 在线刷新报价。
     *
     * @return 成功时返回新快照；网络或解析失败时返回异常，调用方展示缓存结果
     */
    suspend fun refresh(): PricingSnapshot = mRefreshMutex.withLock {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(SOURCE_URL)
                .header("Accept", "application/json")
                .build()
            val bodyText = mOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("报价接口返回 ${response.code}")
                }
                response.body?.string().orEmpty()
            }
            val prices = parsePrices(bodyText)
            if (prices.isEmpty()) throw IOException("报价接口未返回可用价格")

            AppModel.pricingSnapshotJson = mGson.toJson(prices)
            AppModel.pricingFetchedAt = System.currentTimeMillis()
            AppModel.pricingSourceUrl = SOURCE_URL
            PricingSnapshot(prices = prices, fetchedAt = AppModel.pricingFetchedAt, sourceUrl = SOURCE_URL)
        }
    }

    /** 解析模型列表，把 per-token 单价换算成 per-million。 */
    private fun parsePrices(bodyText: String): Map<String, ModelPrice> {
        val root = runCatching { mGson.fromJson(bodyText, com.google.gson.JsonObject::class.java) }
            .getOrNull() ?: return emptyMap()
        val data = root.getAsJsonArray("data") ?: return emptyMap()
        val result = mutableMapOf<String, ModelPrice>()
        for (element in data) {
            val item = element.asJsonObject
            val id = item.get("id")?.asString?.trim()?.lowercase() ?: continue
            val pricing = item.getAsJsonObject("pricing") ?: continue
            val prompt = pricing.get("prompt")?.asString?.toDoubleOrNull() ?: continue
            val completion = pricing.get("completion")?.asString?.toDoubleOrNull() ?: continue
            // 价格为 0 的是免费模型，仍然记录，用于显示「免费」
            result[id] = ModelPrice(
                inputPerMillion = prompt * ONE_MILLION,
                outputPerMillion = completion * ONE_MILLION
            )
        }
        return result
    }

    companion object {
        /** OpenRouter 公开模型列表，包含各模型的 per-token 单价。 */
        const val SOURCE_URL = "https://openrouter.ai/api/v1/models"

        private const val ONE_MILLION = 1_000_000.0
    }
}
