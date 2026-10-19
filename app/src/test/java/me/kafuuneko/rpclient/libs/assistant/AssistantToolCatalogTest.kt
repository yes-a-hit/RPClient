package me.kafuuneko.rpclient.libs.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具协议解析测试。
 *
 * 协议是纯文本，模型输出的写法并不稳定，因此重点覆盖：
 * 多个调用、跨行参数、空参数、以及正文与调用块混杂时的剥离结果。
 */
class AssistantToolCatalogTest {
    @Test
    fun `parses single tool call`() {
        val reply = """
            我先看一下沙箱。
            <tool name="list_files">{"path": "."}</tool>
        """.trimIndent()

        val calls = AssistantToolCatalog.parse(reply)

        assertEquals(1, calls.size)
        assertEquals("list_files", calls[0].name)
        assertEquals(".", calls[0].arguments.get("path").asString)
    }

    @Test
    fun `parses multiple tool calls in order`() {
        val reply = """
            <tool name="read_file">{"path":"a.txt"}</tool>
            <tool name="write_file">{"path":"b.txt","content":"hello"}</tool>
        """.trimIndent()

        val calls = AssistantToolCatalog.parse(reply)

        assertEquals(2, calls.size)
        assertEquals("read_file", calls[0].name)
        assertEquals("write_file", calls[1].name)
    }

    @Test
    fun `parses multi line arguments`() {
        val reply = """
            <tool name="write_file">{
              "path": "note.md",
              "content": "第一行\n第二行"
            }</tool>
        """.trimIndent()

        val calls = AssistantToolCatalog.parse(reply)

        assertEquals(1, calls.size)
        assertEquals("note.md", calls[0].arguments.get("path").asString)
    }

    @Test
    fun `missing arguments fall back to empty object`() {
        val calls = AssistantToolCatalog.parse("""<tool name="list_characters"></tool>""")

        assertEquals(1, calls.size)
        assertTrue(calls[0].arguments.entrySet().isEmpty())
    }

    @Test
    fun `invalid json does not drop the call`() {
        val calls = AssistantToolCatalog.parse("""<tool name="list_files">{oops}</tool>""")

        assertEquals(1, calls.size)
        assertEquals("list_files", calls[0].name)
    }

    @Test
    fun `plain text yields no calls`() {
        assertTrue(AssistantToolCatalog.parse("只是普通回复，没有工具调用。").isEmpty())
    }

    @Test
    fun `strip removes only tool blocks`() {
        val reply = """
            准备写入文件。
            <tool name="write_file">{"path":"a.txt","content":"x"}</tool>
            写完了。
        """.trimIndent()

        val visible = AssistantToolCatalog.stripToolBlocks(reply)

        assertEquals("准备写入文件。\n\n写完了。", visible)
    }

    @Test
    fun `long and string number parsing`() {
        val call = AssistantToolCatalog.parse("""<tool name="get_character">{"id":"7"}</tool>""").first()

        assertEquals(7L, call.arguments.get("id").asLong)
    }
}
