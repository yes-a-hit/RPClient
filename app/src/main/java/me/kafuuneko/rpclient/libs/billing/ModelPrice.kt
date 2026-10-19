package me.kafuuneko.rpclient.libs.billing

/**
 * 单个模型的计价信息，单位为美元 / 百万 Token。
 *
 * 价格统一换成 per-million，是为了让页面展示与内部计算使用同一单位，
 * 避免 per-token 小数在格式化时反复丢精度。
 */
data class ModelPrice(
    val inputPerMillion: Double,
    val outputPerMillion: Double
) {
    /** 按输入输出 Token 计算金额；任一价格缺失时不参与统计。 */
    fun costUsd(inputTokens: Long, outputTokens: Long): Double =
        inputTokens / 1_000_000.0 * inputPerMillion +
            outputTokens / 1_000_000.0 * outputPerMillion
}

/**
 * 模型名归一化与价格匹配。
 *
 * 各服务商的模型标识写法不一致（`deepseek/deepseek-chat`、`gpt-4o-2024-08-06`、
 * `claude-3-5-sonnet@20240620`），报价表又与请求写法的前缀不完全一致，
 * 因此匹配分三步：原样、去供应商前缀、再去版本后缀。
 */
object ModelPriceMatcher {
    /** 去掉 `vendor/` 前缀。 */
    private val vendorPrefix = Regex("^[a-z0-9_.-]+/")

    /**
     * 去掉版本尾巴：`-2024-08-06`、`@20240620`、`-latest`、`:free` 这类后缀。
     *
     * 只裁剪明确的版本标记，不能把 `gemini-2.5-flash` 里的 `-flash` 当成版本号。
     */
    private val versionSuffix = Regex(
        "(@[0-9a-z.]+|:(free|extended|nitro|online|floor)" +
            "|-(\\d{4}-\\d{2}-\\d{2}|\\d{8}|latest|preview))+$"
    )

    /** 生成由精确到宽松的候选键，按顺序尝试匹配。 */
    fun candidates(model: String): List<String> {
        val raw = model.trim().lowercase()
        if (raw.isEmpty()) return emptyList()
        val withoutVendor = raw.replace(vendorPrefix, "")
        val withoutSuffix = withoutVendor.replace(versionSuffix, "")
        return listOf(raw, withoutVendor, withoutSuffix).filter { it.isNotBlank() }.distinct()
    }

    /**
     * 在报价表中查找模型价格。
     *
     * @param model 请求实际使用的模型名
     * @param prices 归一化键到价格的映射
     * @return 命中的价格；未收录时返回 null，调用方应展示为「未定价」
     */
    fun resolve(model: String, prices: Map<String, ModelPrice>): ModelPrice? {
        for (key in candidates(model)) {
            prices[key]?.let { return it }
            // 报价表键也做同样归一化，容忍表里带前缀或日期
            prices.entries.firstOrNull { normalizeKey(it.key) == key }?.let { return it.value }
        }
        return null
    }

    /** 把报价表的键统一成可比对的形式。 */
    fun normalizeKey(key: String): String =
        key.trim().lowercase().replace(vendorPrefix, "").replace(versionSuffix, "")
}
