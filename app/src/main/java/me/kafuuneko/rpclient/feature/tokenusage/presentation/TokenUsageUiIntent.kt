package me.kafuuneko.rpclient.feature.tokenusage.presentation

/** 消耗统计页的用户意图。 */
sealed class TokenUsageUiIntent {
    data object Init : TokenUsageUiIntent()
    data object Back : TokenUsageUiIntent()
    data class SelectPeriod(val period: TokenUsagePeriod) : TokenUsageUiIntent()
    data class SelectTrendPoint(val key: String?) : TokenUsageUiIntent()
    data class SelectChartMode(val mode: TokenUsageChartMode) : TokenUsageUiIntent()
    data object ShowClearConfirmDialog : TokenUsageUiIntent()
    data object ConfirmClearRecords : TokenUsageUiIntent()
    data object DismissDialog : TokenUsageUiIntent()

    /** 刷新当前模型配置的账户余额。 */
    data object RefreshBalance : TokenUsageUiIntent()

    /** 在线拉取最新模型报价并重算金额。 */
    data object RefreshPricing : TokenUsageUiIntent()
}
