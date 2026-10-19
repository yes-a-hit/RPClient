package me.kafuuneko.rpclient.libs.billing

import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageRecord

/** 一次请求的费用折算结果。 */
data class UsageCost(
    /** 折算后的人民币金额。 */
    val amountCny: Double,
    /** 是否命中人民币官方价目；false 表示由美元报价按汇率换算。 */
    val fromLocalPricing: Boolean
)

/**
 * 用量金额折算。
 *
 * 计价顺序与真实账单对齐：
 * - 先查内置人民币价目（含夜间折扣与缓存命中价），命中即按官方牌价计费；
 * - 未收录的模型回退到 OpenRouter 美元报价，并按配置的汇率折算成人民币；
 * - 两者都没有的模型返回 null，由页面计入「未定价」，不用 0 冒充。
 */
class UsageCostCalculator(
    private val mPricingRepository: PricingRepository
) {
    /**
     * 计算单条记录的费用。
     *
     * @param record 一次成功请求的用量快照
     * @param usdToCny 美元兑人民币汇率，仅用于美元报价折算
     * @return 命中任一套价目时返回金额，否则返回 null
     */
    fun costOf(record: LLMTokenUsageRecord, usdToCny: Double): UsageCost? {
        // 优先使用与站点绑定的人民币官方价目，含夜间档与缓存命中价
        val localRates = TokenPricingCatalog.ratesFor(
            model = record.effectiveModel,
            apiHost = record.apiHost,
            createTime = record.createTime
        )
        if (localRates != null) {
            return UsageCost(amountCny = compute(record, localRates), fromLocalPricing = true)
        }
        // 回退到 OpenRouter 美元报价，按汇率折成人民币
        val usd = ModelPriceMatcher.resolve(record.effectiveModel, mPricingRepository.cached().prices)
            ?: return null
        val rates = TokenRates(
            inputPerMillion = usd.inputPerMillion,
            outputPerMillion = usd.outputPerMillion,
            cachedInputPerMillion = usd.inputPerMillion,
            currency = PricingCurrency.USD
        )
        return UsageCost(amountCny = compute(record, rates) * usdToCny, fromLocalPricing = false)
    }

    /** 按「普通输入 + 缓存命中 + 输出」三段计算金额。 */
    private fun compute(record: LLMTokenUsageRecord, rates: TokenRates): Double {
        val cached = (record.cachedInputTokens ?: 0L).coerceIn(0L, record.inputTokens)
        val plainInput = (record.inputTokens - cached).coerceAtLeast(0L)
        return plainInput / ONE_MILLION * rates.inputPerMillion +
            cached / ONE_MILLION * rates.cachedInputPerMillion +
            record.outputTokens / ONE_MILLION * rates.outputPerMillion
    }

    private companion object {
        const val ONE_MILLION = 1_000_000.0
    }
}
