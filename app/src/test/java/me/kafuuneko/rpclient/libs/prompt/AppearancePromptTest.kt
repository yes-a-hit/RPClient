package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.prompt.model.PromptBuildContext
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外观参考的说明文字。
 *
 * 这块的唯一目的是让模型把图里的形象认成「自己」。
 * 光给图不够——它会当成用户发来的参考图，甚至当成别的同类，
 * 于是就有了「发自己的梗图，角色吃自己的醋」。
 */
class AppearancePromptTest {

    private fun context(vararg notes: String) = PromptBuildContext(
        userName = "你",
        userDescription = "",
        character = Character(
            name = "小蓝",
            avatar = "",
            characterTags = "[]",
            description = "",
            creatorNotes = "",
            personality = "",
            scenario = "",
            firstMessages = "",
            examplesOfDialogue = "",
            postHistoryInstructions = "",
            systemPrompt = "",
            creator = "",
            characterVersion = ""
        ),
        session = ChatSession(
            id = 1,
            characterId = 1,
            createTime = 0,
            latestTime = 0,
            lorebookEntrySet = "[]",
            title = "",
            userNote = "",
            userName = "",
            userDescription = "",
            creatorNotes = null,
            worldInfoStateJson = "{}",
            autoSummaryPaused = false
        ),
        summary = "",
        messages = emptyList(),
        currentUserMessage = null,
        candidateLorebookEntries = emptyList(),
        provider = null,
        maxContextTokens = 4096,
        maxResponseTokens = 1024,
        appearanceImages = notes.map { fakeRef(it) },
        appearanceNotes = notes.toList()
    )

    private fun fakeRef(seed: String) = LLMImageReference(
        uuid = seed,
        mimeType = "image/png",
        width = 1,
        height = 1,
        byteCount = 1
    )

    @Test
    fun `declares the images are the character itself`() {
        val text = buildAppearancePrompt(context("立绘"))
        assertTrue(text.contains("小蓝"))
        assertTrue(text.contains("这些形象就是你"))
    }

    @Test
    fun `forbids treating it as another character`() {
        // 这条是核心：不然认成「别的鱼」
        val text = buildAppearancePrompt(context("立绘"))
        assertTrue(text.contains("不要当成别的角色"))
        assertTrue(text.contains("不要认成别人的同类"))
    }

    @Test
    fun `numbers the notes`() {
        val text = buildAppearancePrompt(context("立绘", "被摸头时的表情"))
        assertTrue(text.contains("第 1 张"))
        assertTrue(text.contains("立绘"))
        assertTrue(text.contains("第 2 张"))
        assertTrue(text.contains("被摸头时的表情"))
    }

    @Test
    fun `empty note still lists the image`() {
        val text = buildAppearancePrompt(context(""))
        assertTrue(text.contains("第 1 张"))
    }
}
