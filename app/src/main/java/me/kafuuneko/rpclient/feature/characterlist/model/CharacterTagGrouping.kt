package me.kafuuneko.rpclient.feature.characterlist.model

import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterTagGroup

/**
 * 按标签把角色分组。
 *
 * 规则：覆盖角色最多的标签先成组，已被分走的角色不再参与后面的组，
 * 剩下没归到任何组的平铺在最后。
 *
 * 只有一张卡挂着的标签不单独成组——那种分组只是把列表切碎，
 * 反而更难找。所以至少要两个角色才认。
 *
 * 抽成顶层函数是为了能直接测：分组规则一旦错了，
 * 表现是「某些角色凭空消失」，在界面上很难看出来。
 */
fun groupCharactersByTag(characters: List<CharacterListItem>): List<CharacterTagGroup> {
    if (characters.size < 2) return emptyList()

    val byTag = LinkedHashMap<String, MutableList<CharacterListItem>>()
    characters.forEach { item ->
        item.tags.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { tag ->
                byTag.getOrPut(tag) { mutableListOf() }.add(item)
            }
    }

    val candidates = byTag.entries
        .filter { it.value.size >= 2 }
        .sortedWith(compareByDescending<Map.Entry<String, MutableList<CharacterListItem>>> {
            it.value.size
        }.thenBy { it.key })

    if (candidates.isEmpty()) return emptyList()

    val assigned = mutableSetOf<Long>()
    val groups = mutableListOf<CharacterTagGroup>()
    candidates.forEach { (tag, members) ->
        val fresh = members.filterNot { it.id in assigned }
        if (fresh.isEmpty()) return@forEach
        groups += CharacterTagGroup(tag = tag, characters = fresh)
        fresh.forEach { assigned += it.id }
    }

    val rest = characters.filterNot { it.id in assigned }
    if (rest.isNotEmpty()) {
        groups += CharacterTagGroup(tag = "", characters = rest)
    }

    // 只有一个组再加一堆散的时候并不比平铺更好，直接不算分组
    if (groups.size < 2) return emptyList()
    return groups
}
