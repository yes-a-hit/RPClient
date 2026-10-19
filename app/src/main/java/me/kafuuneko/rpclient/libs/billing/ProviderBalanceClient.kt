package me.kafuuneko.rpclient.libs.billing

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import okhttp3.OkHttpClient
import okhttp3.Request

/** 一次余额查询的结果。 */
sealed class BalanceResult {
    /** 查询成功。 */
    data class Available(
        /** 账户币种，如 USD、CNY。 */
        val currency: String,
        /** 当前可用余额。 */
        val remaining: Double,
        /** 赠送额度，部分服务商会单独列出。 */
        val granted: Double? = null,
        /** 充值余额，部分服务商会单独列出。 */
        val toppedUp: Double? = null,
        /** 已用额度，仅 OpenRouter 这类提供累计用量的服务商返回。 */
        val used: Double? = null
    ) : BalanceResult()

    /** 该服务商没有公开的余额查询接口。 */
    data class Unsupported(val reason: String) : BalanceResult()

    /** 查询失败，通常是密钥无效或网络问题。 */
    data class Failed(val reason: String) : BalanceResult()
}

/**
 * 服务商余额查询。
 *
 * 只实现在官方文档中明确存在的接口：
 * - OpenRouter：`GET /api/v1/credits`，返回累计充值额度与累计用量，余额由两者相减得出。
 * - DeepSeek：`GET /user/balance`，直接返回可用、赠送与充值余额。
 *
 * 其他服务商没有公开余额接口（只能从控制台查看），这里返回 [BalanceResult.Unsupported]，
 * 页面据此显示说明而不是错误。
 */
class ProviderBalanceClient(
    private val mOkHttpClient: OkHttpClient,
    private val mGson: Gson
) {
    /** 查询指定模型配置所属账户的余额。 */
    suspend fun query(provider: LLMProviderConfig): BalanceResult = withContext(Dispatchers.IO) {
        val origin = resolveOrigin(provider.baseUrl)
            ?: return@withContext BalanceResult.Failed("模型服务地址无法解析")
        if (provider.apiKey.isBlank()) {
            return@withContext BalanceResult.Failed("该配置未填写 API Key")
        }
        runCatching {
            when {
                origin.contains("openrouter.ai") -> queryOpenRouter(origin, provider.apiKey)
                origin.contains("deepseek.com") -> queryDeepSeek(origin, provider.apiKey)
                else -> BalanceResult.Unsupported(
                    "该服务商未提供余额查询接口，请在服务商控制台查看"
                )
            }
        }.getOrElse { error ->
            BalanceResult.Failed(error.message ?: "余额查询失败")
        }
    }

    /** OpenRouter：余额 = 累计充值 - 累计用量。 */
    private fun queryOpenRouter(origin: String, apiKey: String): BalanceResult {
        val json = getJson("$origin/api/v1/credits", apiKey)
        val data = json.getAsJsonObject("data")
            ?: return BalanceResult.Failed("返回结构缺少 data 字段")
        val totalCredits = data.get("total_credits")?.asDouble
            ?: return BalanceResult.Failed("返回结构缺少 total_credits")
        val totalUsage = data.get("total_usage")?.asDouble ?: 0.0
        return BalanceResult.Available(
            currency = "USD",
            remaining = (totalCredits - totalUsage).coerceAtLeast(0.0),
            used = totalUsage
        )
    }

    /** DeepSeek：直接返回余额明细，币种由服务端给出。 */
    private fun queryDeepSeek(origin: String, apiKey: String): BalanceResult {
        val json = getJson("$origin/user/balance", apiKey)
        val infos = json.getAsJsonArray("balance_infos")
            ?: return BalanceResult.Failed("返回结构缺少 balance_infos")
        val first = infos.firstOrNull()?.asJsonObject
            ?: return BalanceResult.Failed("账户没有余额信息")
        return BalanceResult.Available(
            currency = first.get("currency")?.asString ?: "CNY",
            remaining = first.get("total_balance")?.asString?.toDoubleOrNull() ?: 0.0,
            granted = first.get("granted_balance")?.asString?.toDoubleOrNull(),
            toppedUp = first.get("topped_up_balance")?.asString?.toDoubleOrNull()
        )
    }

    /** 发起带鉴权的 GET 请求并解析 JSON。 */
    private fun getJson(url: String, apiKey: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()
        val text = mOkHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("接口返回 ${response.code}")
            }
            response.body?.string().orEmpty()
        }
        return mGson.fromJson(text, JsonObject::class.java)
            ?: throw IOException("返回内容不是 JSON 对象")
    }

    /** 取协议与主机名，基地址可能带路径或 /v1 后缀。 */
    private fun resolveOrigin(baseUrl: String): String? {
        val normalized = baseUrl.trim().let { if (it.endsWith("/")) it.dropLast(1) else it }
        if (normalized.isBlank()) return null
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        return "$scheme://$host"
    }
}
