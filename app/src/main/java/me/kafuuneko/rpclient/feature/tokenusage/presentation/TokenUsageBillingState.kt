package me.kafuuneko.rpclient.feature.tokenusage.presentation

/**
 * 账户余额的展示状态。
 *
 * 只有官方提供余额接口的服务商才能拿到数字，其余情况必须显式区分「不支持」与「查询失败」，
 * 避免把「不知道该服务商有没有余额」误显示成 0。
 */
sealed class TokenUsageBalanceState {
    /** 尚未查询，页面不展示余额卡。 */
    data object Hidden : TokenUsageBalanceState()

    /** 查询中。 */
    data object Loading : TokenUsageBalanceState()

    /** 查询成功。 */
    data class Available(
        /** 模型配置名称，说明余额属于哪个账户。 */
        val providerName: String,
        /** 币种符号或代码。 */
        val currency: String,
        /** 当前可用余额。 */
        val remaining: Double,
        /** 赠送额度，仅部分服务商返回。 */
        val granted: Double? = null,
        /** 充值余额，仅部分服务商返回。 */
        val toppedUp: Double? = null,
        /** 累计已用额度，仅 OpenRouter 返回。 */
        val used: Double? = null
    ) : TokenUsageBalanceState()

    /** 该服务商没有公开余额接口。 */
    data class Unsupported(val reason: String) : TokenUsageBalanceState()

    /** 查询失败。 */
    data class Failed(val reason: String) : TokenUsageBalanceState()
}

/**
 * 当前统计周期内的金额估算。
 *
 * 单价来自报价快照，未收录的模型不计入金额，因此额外记录未定价模型与覆盖率，
 * 让用户能判断这个数字覆盖了多少实际用量。
 */
data class TokenUsageCostState(
    /** 折算后的人民币金额。 */
    val totalCny: Double = 0.0,
    /** 参与计价的输入 Token。 */
    val pricedInputTokens: Long = 0L,
    /** 参与计价的输出 Token。 */
    val pricedOutputTokens: Long = 0L,
    /** 当前周期总 Token，用于计算覆盖率。 */
    val totalTokens: Long = 0L,
    /** 报价表中缺失的模型名。 */
    val unpricedModels: List<String> = emptyList(),
    /** 命中官方人民币价目（含夜间档）的请求数。 */
    val localPricedRequests: Int = 0,
    /** 报价快照时间，0 表示尚未获取过报价。 */
    val pricedAt: Long = 0L,
    /** 报价快照来源地址。 */
    val priceSource: String = ""
) {
    /** 是否已有可用报价。 */
    val hasPrice: Boolean
        get() = pricedAt > 0L

    /** 已计价 Token 占总量的比例，范围 0..1。 */
    val coverage: Float
        get() = if (totalTokens > 0L) {
            ((pricedInputTokens + pricedOutputTokens).toFloat() / totalTokens).coerceIn(0f, 1f)
        } else {
            0f
        }
}
