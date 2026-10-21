package me.kafuuneko.rpclient.feature.characterlist

import androidx.compose.ui.graphics.Color
import me.kafuuneko.rpclient.feature.characterlist.model.CharacterListItem
import me.kafuuneko.rpclient.feature.characterlist.model.groupCharactersByTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 角色卡标签分组。
 *
 * 规则错了的表现是「某些角色从列表里消失」，界面上很难看出来，
 * 所以这里把边界钉死。
 */
class CharacterTagGroupingTest {

    private fun item(id: Long, name: String, vararg tags: String) = CharacterListItem(
        id = id,
        name = name,
        description = "",
        tags = tags.toList(),
        avatarText = name.take(1),
        avatarColor = Color.Gray
    )

    @Test
    fun `characters sharing a tag form one group`() {
        // 「玩偶」把四只归到一块，剩下的散装归最后
        val list = listOf(
            item(1, "邦尼", "玩偶"),
            item(2, "奇卡", "玩偶"),
            item(3, "霍斯", "玩偶"),
            item(4, "弗莱迪", "玩偶"),
            item(5, "路人")
        )

        val groups = groupCharactersByTag(list)

        assertEquals(2, groups.size)
        assertEquals("玩偶", groups[0].tag)
        assertEquals(4, groups[0].characters.size)
        assertEquals("", groups[1].tag)
        assertEquals(listOf(5L), groups[1].characters.map { it.id })
    }

    @Test
    fun `bigger tag wins when a character carries several`() {
        // 一张卡多个标签时归覆盖人数更多的那个组，避免同时出现在两处
        val list = listOf(
            item(1, "A", "玩偶", "反派"),
            item(2, "B", "玩偶"),
            item(3, "C", "玩偶"),
            item(4, "D", "反派"),
            item(5, "E", "反派")
        )

        val groups = groupCharactersByTag(list)

        assertEquals("反派", groups[0].tag)
        assertEquals(3, groups[0].characters.size)
        assertEquals("玩偶", groups[1].tag)
        // A 已经被反派组拿走，这里只剩 B 和 C
        assertEquals(listOf(2L, 3L), groups[1].characters.map { it.id })
    }

    @Test
    fun `every character appears exactly once`() {
        // 分组最容易出的错是漏人或者重复，这里逐个点名
        val list = listOf(
            item(1, "A", "玩偶", "主角"),
            item(2, "B", "玩偶"),
            item(3, "C", "主角"),
            item(4, "D"),
            item(5, "E", "玩偶", "主角")
        )

        val groups = groupCharactersByTag(list)
        val ids = groups.flatMap { group -> group.characters.map { it.id } }

        assertEquals(list.size, ids.size)
        assertEquals(list.map { it.id }.toSet(), ids.toSet())
    }

    @Test
    fun `single character list is not grouped`() {
        val groups = groupCharactersByTag(listOf(item(1, "独苗", "玩偶")))

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `one tag on one character does not create a group`() {
        // 只有一张卡挂着的标签不单独成组，否则列表被切得更碎
        val list = listOf(
            item(1, "A", "独有标签"),
            item(2, "B"),
            item(3, "C")
        )

        assertTrue(groupCharactersByTag(list).isEmpty())
    }

    @Test
    fun `no tags means no grouping`() {
        val list = listOf(item(1, "A"), item(2, "B"), item(3, "C"))

        assertTrue(groupCharactersByTag(list).isEmpty())
    }

    @Test
    fun `blank tags are ignored`() {
        // 导入的角色卡常带空标签，不能因此凭空多出一组
        val list = listOf(
            item(1, "A", "", "  "),
            item(2, "B", "", "  ")
        )

        assertTrue(groupCharactersByTag(list).isEmpty())
    }
}
