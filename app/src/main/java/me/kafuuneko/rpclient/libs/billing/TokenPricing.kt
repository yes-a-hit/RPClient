package me.kafuuneko.rpclient.libs.billing

import java.time.Instant
import java.time.ZoneId

/** 计价币种。 */
enum class PricingCurrency {
    CNY,
    USD
}

/**
 * 单个计费档位的单价，单位为「每百万 Token」。
 *
 * 缓存命中单独定价是主流服务商的通用做法，命中部分的输入按更低单价计费，
 * 因此这里把输入拆成普通输入与缓存命中两档。
 */
data class TokenRates(
    val inputPerMillion: Double,
    val outputPerMillion: Double,
    val cachedInputPerMillion: Double,
    val currency: PricingCurrency
)

/**
 * 按时段生效的价目档位。
 *
 * 硅基流动等国内服务商对夜间时段打折，档位用「小时区间左闭右开」表达；
 * 一天内没有命中任何区间时回退到该模型的默认价。
 */
data class PricingTier(
    val startHourInclusive: Int,
    val endHourExclusive: Int,
    val rates: TokenRates
) {
    /** 判断某个小时是否落在本档位内。 */
    fun contains(hour: Int): Boolean = hour >= startHourInclusive && hour < endHourExclusive
}

/** 一个模型的完整价格方案。 */
data class ModelPricing(
    /** 归一化后的模型名匹配片段，命中即视为同一模型。 */
    val modelKey: String,
    /**
     * 适用的站点主机名片段。
     *
     * 同一个模型在不同服务商的价格并不相同（转售平台有自己的峰谷价），
     * 因此内置价目必须绑定站点，避免拿 A 站的牌价去结算 B 站的账单。
     */
    val hostKey: String? = null,
    /** 分时段档位，可为空。 */
    val tiers: List<PricingTier> = emptyList(),
    /** 未命中任何档位时使用的默认价。 */
    val defaultRates: TokenRates
) {
    /** 按小时选择生效价格。 */
    fun ratesAt(hour: Int): TokenRates =
        tiers.firstOrNull { it.contains(hour) }?.rates ?: defaultRates
}

/**
 * 内置人民币价目表。
 *
 * 只收录能确认档位口径的服务商，避免用猜测的数字替代真实账单；
 * 未收录的模型仍走 OpenRouter 的美元报价，由界面按汇率折算展示。
 */
object TokenPricingCatalog {
    /** 硅基流动 DeepSeek V4 Flash（0731）：夜间 2–8 点打折，其余时段为标准价。 */
    private val siliconFlowDeepSeekV4Flash = ModelPricing(
        modelKey = "deepseek-v4-flash",
        hostKey = "siliconflow.cn",
        tiers = listOf(
            PricingTier(
                startHourInclusive = 2,
                endHourExclusive = 8,
                rates = TokenRates(
                    inputPerMillion = 1.5,
                    outputPerMillion = 4.5,
                    cachedInputPerMillion = 0.15,
                    currency = PricingCurrency.CNY
                )
            )
        ),
        defaultRates = TokenRates(
            inputPerMillion = 3.0,
            outputPerMillion = 9.0,
            cachedInputPerMillion = 0.3,
            currency = PricingCurrency.CNY
        )
    )

    private val entries = listOf(siliconFlowDeepSeekV4Flash)

    /**
     * 查找模型的人民币价目。
     *
     * @param model 请求使用的模型名，允许带厂商前缀与日期后缀
     * @param apiHost 请求实际打到的站点主机名，用于区分同模型不同价
     * @param createTime 请求时间戳，用于判断峰谷档位
     * @return 命中的价格；站点或模型未收录时返回 null
     */
    fun ratesFor(model: String, apiHost: String, createTime: Long): TokenRates? {
        val normalized = ModelPriceMatcher.normalizeKey(model)
        val host = apiHost.lowercase()
        val matched = entries.firstOrNull { pricing ->
            // 站点必须匹配，未声明站点的价目视为通用
            val hostOk = pricing.hostKey == null || host.contains(pricing.hostKey)
            hostOk && normalized.contains(pricing.modelKey)
        } ?: return null
        return matched.ratesAt(hourOf(createTime))
    }

    /** 取时间戳对应的本地小时；价目时段按设备时区解释。 */
    private fun hourOf(createTime: Long): Int =
        Instant.ofEpochMilli(createTime).atZone(ZoneId.systemDefault()).hour
}
