package me.kafuuneko.rpclient.libs.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 报价匹配测试。
 *
 * 请求里的模型名与报价表里的键写法经常不一致，这里固定住归一化规则：
 * 去供应商前缀、去明确版本尾巴，且不能误伤模型本身的词根。
 */
class ModelPriceMatcherTest {
    private val prices = mapOf(
        "openai/gpt-4o" to ModelPrice(2.5, 10.0),
        "deepseek-chat" to ModelPrice(0.27, 1.10),
        "google/gemini-2.5-flash" to ModelPrice(0.30, 2.50)
    )

    @Test
    fun `candidates drop vendor prefix`() {
        val result = ModelPriceMatcher.candidates("openai/gpt-4o")

        assertTrue(result.contains("gpt-4o"))
    }

    @Test
    fun `candidates drop date suffix`() {
        val result = ModelPriceMatcher.candidates("openai/gpt-4o-2024-08-06")

        assertTrue(result.contains("gpt-4o"))
    }

    @Test
    fun `candidates drop at signed version`() {
        val result = ModelPriceMatcher.candidates("anthropic/claude-3-5-sonnet@20240620")

        assertTrue(result.contains("claude-3-5-sonnet"))
    }

    @Test
    fun `model word is not treated as version`() {
        val result = ModelPriceMatcher.candidates("gemini-2.5-flash")

        assertTrue(result.contains("gemini-2.5-flash"))
        assertTrue(!result.contains("gemini-2.5"))
    }

    @Test
    fun `resolve matches through vendor prefix`() {
        val price = ModelPriceMatcher.resolve("openai/gpt-4o-2024-08-06", prices)

        assertEquals(2.5, price?.inputPerMillion ?: 0.0, 0.0001)
        assertEquals(10.0, price?.outputPerMillion ?: 0.0, 0.0001)
    }

    @Test
    fun `resolve matches plain model name`() {
        val price = ModelPriceMatcher.resolve("deepseek-chat", prices)

        assertEquals(0.27, price?.inputPerMillion ?: 0.0, 0.0001)
    }

    @Test
    fun `resolve returns null for unknown model`() {
        assertNull(ModelPriceMatcher.resolve("totally-unknown-model", prices))
    }

    @Test
    fun `cost uses per million pricing`() {
        val price = ModelPrice(2.5, 10.0)

        // 100 万输入 + 50 万输出 = 2.5 + 5.0
        assertEquals(7.5, price.costUsd(1_000_000, 500_000), 0.0001)
    }
}
