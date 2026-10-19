package me.kafuuneko.rpclient.libs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 剧本 JSON 解析测试。
 *
 * 剧本是外部输入，校验必须既严格又给出可定位的原因，
 * 因此这里覆盖格式错误、版本过高、缺字段、悬空引用四类失败路径，
 * 以及一次完整的成功解析。
 */
class ScriptPackCodecTest {
    private val codec = ScriptPackCodec()

    @Test
    fun `parses minimal pack`() {
        val json = """
            {
              "id": "demo",
              "name": "演示剧本",
              "mode": "Mainline",
              "scenes": [
                {"id": "s1", "name": "序章"}
              ]
            }
        """.trimIndent()

        val result = codec.decode(json)

        assertTrue(result is ScriptPackParseResult.Success)
        val pack = (result as ScriptPackParseResult.Success).pack
        assertEquals("demo", pack.id)
        assertEquals(ScriptMode.Mainline, pack.mode)
        // 未显式指定起始场景时回落到第一个场景
        assertEquals("s1", pack.startSceneId)
    }

    @Test
    fun `parses lorebook and conditions`() {
        val json = """
            {
              "id": "demo",
              "name": "演示",
              "mode": "Loop",
              "globalLorebook": {
                "name": "全局设定",
                "entries": [
                  {"name": "王国", "content": "北方王国", "keys": ["王国"], "constant": false}
                ]
              },
              "scenes": [
                {
                  "id": "s1",
                  "name": "序章",
                  "lorebook": {"name": "局部", "entries": []},
                  "nextSceneIds": ["s2"],
                  "checkpoint": true,
                  "failConditions": [{"type": "RoundLimit", "threshold": 20, "description": "回合耗尽"}]
                },
                {"id": "s2", "name": "终章", "completionCondition": {"type": "Keyword", "pattern": "离开"}}
              ]
            }
        """.trimIndent()

        val result = codec.decode(json)
        assertTrue(result is ScriptPackParseResult.Success)
        val pack = (result as ScriptPackParseResult.Success).pack

        assertEquals(ScriptMode.Loop, pack.mode)
        assertEquals(1, pack.globalLorebook?.entries?.size)
        assertEquals(20, pack.scenes[0].failConditions[0].threshold)
        assertEquals("离开", pack.scenes[1].completionCondition?.pattern)
    }

    @Test
    fun `strips markdown fence`() {
        val json = """
            ```json
            {"id": "a", "name": "b", "scenes": [{"id": "s1", "name": "c"}]}
            ```
        """.trimIndent()

        assertTrue(codec.decode(json) is ScriptPackParseResult.Success)
    }

    @Test
    fun `rejects unsupported version`() {
        val json = """
            {"formatVersion": 99, "id": "a", "name": "b", "scenes": [{"id": "s1", "name": "c"}]}
        """.trimIndent()

        val result = codec.decode(json)
        assertTrue(result is ScriptPackParseResult.Failure)
        assertEquals(
            ScriptPackError.UnsupportedVersion,
            (result as ScriptPackParseResult.Failure).error
        )
    }

    @Test
    fun `rejects missing fields`() {
        val result = codec.decode("""{"name": "无 id"}""")

        assertTrue(result is ScriptPackParseResult.Failure)
        assertEquals(ScriptPackError.MissingField, (result as ScriptPackParseResult.Failure).error)
    }

    @Test
    fun `rejects dangling next scene`() {
        val json = """
            {"id": "a", "name": "b", "scenes": [{"id": "s1", "name": "c", "nextSceneIds": ["ghost"]}]}
        """.trimIndent()

        val result = codec.decode(json)
        assertTrue(result is ScriptPackParseResult.Failure)
        assertEquals(
            ScriptPackError.BrokenReference,
            (result as ScriptPackParseResult.Failure).error
        )
        assertTrue(result.detail.contains("ghost"))
    }

    @Test
    fun `rejects broken json`() {
        val result = codec.decode("{oops}")

        assertTrue(result is ScriptPackParseResult.Failure)
        assertEquals(ScriptPackError.NotJson, (result as ScriptPackParseResult.Failure).error)
    }

    @Test
    fun `encode then decode round trip`() {
        val pack = ScriptPack(
            id = "round",
            name = "往返",
            scenes = listOf(
                ScriptScene(
                    id = "s1",
                    name = "序章",
                    failConditions = listOf(
                        ScriptCondition(ScriptConditionType.Keyword, pattern = "死亡")
                    )
                )
            )
        )

        val decoded = codec.decode(codec.encode(pack))

        assertTrue(decoded is ScriptPackParseResult.Success)
        val restored = (decoded as ScriptPackParseResult.Success).pack
        assertEquals("round", restored.id)
        assertEquals("死亡", restored.scenes[0].failConditions[0].pattern)
    }

    @Test
    fun `parses cast and round limit`() {
        val json = """
            {
              "id": "cast-demo",
              "name": "角色名单",
              "scenes": [
                {
                  "id": "s1",
                  "name": "序章",
                  "maxRounds": 30,
                  "cast": [{"name": "老陈", "required": true, "description": "引水员"}],
                  "bannedCast": ["叛徒"]
                }
              ]
            }
        """.trimIndent()

        val result = codec.decode(json)
        assertTrue(result is ScriptPackParseResult.Success)
        val scene = (result as ScriptPackParseResult.Success).pack.scenes.first()
        assertEquals(30, scene.maxRounds)
        assertEquals("老陈", scene.cast.first().name)
        assertTrue(scene.cast.first().required)
        assertEquals(listOf("叛徒"), scene.bannedCast)
    }

    @Test
    fun `zero round limit means unlimited`() {
        val json = """
            {"id": "a", "name": "b", "scenes": [{"id": "s1", "name": "c"}]}
        """.trimIndent()

        val result = codec.decode(json)
        val scene = (result as ScriptPackParseResult.Success).pack.scenes.first()
        assertEquals(0, scene.maxRounds)
    }

    @Test
    fun `rejects character in both lists`() {
        val json = """
            {
              "id": "a", "name": "b",
              "scenes": [{"id": "s1", "name": "c", "cast": [{"name": "老陈"}], "bannedCast": ["老陈"]}]
            }
        """.trimIndent()

        val result = codec.decode(json)
        assertTrue(result is ScriptPackParseResult.Failure)
        assertEquals(
            ScriptPackError.BrokenReference,
            (result as ScriptPackParseResult.Failure).error
        )
    }

    @Test
    fun `parses collection with gm and dice switches`() {
        val json = """
            {
              "id": "series", "name": "系列",
              "enableGm": false, "enableDice": true,
              "packs": [
                {"id": "p1", "name": "第一篇", "scenes": [{"id": "s1", "name": "序章"}]},
                {"id": "p2", "name": "第二篇", "scenes": [{"id": "s1", "name": "序章"}]}
              ]
            }
        """.trimIndent()

        val result = codec.decodeCollection(json)
        assertTrue(result is ScriptCollectionParseResult.Success)
        val collection = (result as ScriptCollectionParseResult.Success).collection
        assertEquals(2, collection.packs.size)
        assertEquals(false, collection.enableGm)
        assertEquals(true, collection.enableDice)
        assertEquals("p2", collection.packById("p2")?.id)
    }

    @Test
    fun `collection rejects duplicated pack id`() {
        val json = """
            {
              "id": "series", "name": "系列",
              "packs": [
                {"id": "p1", "name": "一", "scenes": [{"id": "s1", "name": "序章"}]},
                {"id": "p1", "name": "二", "scenes": [{"id": "s1", "name": "序章"}]}
              ]
            }
        """.trimIndent()

        val result = codec.decodeCollection(json)
        assertTrue(result is ScriptCollectionParseResult.Failure)
        assertEquals(
            ScriptPackError.BrokenReference,
            (result as ScriptCollectionParseResult.Failure).error
        )
    }

    @Test
    fun `single scene pack needs no routing fields`() {
        // 轻量单线剧本：一个场景、不写入口、不写后继
        val json = """
            {"id": "light", "name": "轻松小品", "scenes": [{"id": "only", "name": "唯一场景"}]}
        """.trimIndent()

        val result = codec.decode(json)

        assertTrue(result is ScriptPackParseResult.Success)
        val pack = (result as ScriptPackParseResult.Success).pack
        assertEquals("only", pack.startSceneId)
        assertTrue(pack.scenes.first().nextSceneIds.isEmpty())
    }

    @Test
    fun `parses multi entry and unlock relations`() {
        val json = """
            {
              "id": "gal", "name": "多女主",
              "startSceneIds": ["a-start", "b-start"],
              "scenes": [
                {"id": "a-start", "name": "A 线开始"},
                {"id": "b-start", "name": "B 线开始"},
                {"id": "a-mid", "name": "A 线中段", "unlockedBy": ["a-start"]},
                {"id": "b-mid", "name": "B 线中段", "unlockedBy": ["b-start"]},
                {"id": "final", "name": "汇合", "unlockedBy": ["a-mid", "b-mid"]}
              ]
            }
        """.trimIndent()

        val result = codec.decode(json)

        assertTrue(result is ScriptPackParseResult.Success)
        val pack = (result as ScriptPackParseResult.Success).pack
        assertEquals(2, pack.startSceneIds.size)
        assertEquals("a-start", pack.startSceneId)
        assertEquals(listOf("a-start"), pack.scenes[2].unlockedBy)
    }

    @Test
    fun `rejects unknown unlock reference`() {
        val json = """
            {
              "id": "x", "name": "x",
              "scenes": [{"id": "s1", "name": "c", "unlockedBy": ["ghost"]}]
            }
        """.trimIndent()

        val result = codec.decode(json)

        assertTrue(result is ScriptPackParseResult.Failure)
        assertEquals(
            ScriptPackError.BrokenReference,
            (result as ScriptPackParseResult.Failure).error
        )
    }

    @Test
    fun `parses layout coordinates`() {
        val json = """
            {
              "id": "graph", "name": "图",
              "scenes": [{"id": "s1", "name": "c", "layout": {"x": 120.5, "y": -40}}]
            }
        """.trimIndent()

        val result = codec.decode(json)
        val scene = (result as ScriptPackParseResult.Success).pack.scenes.first()

        assertEquals(120.5f, scene.layout?.x ?: 0f, 0.01f)
        assertEquals(-40f, scene.layout?.y ?: 0f, 0.01f)
    }

    @Test
    fun `rejects dead loop without loop enabled`() {
        // A -> B -> A 且两者都不允许循环，属于场景堆砌产生的死循环
        val json = """
            {
              "id": "loop", "name": "死循环",
              "scenes": [
                {"id": "a", "name": "甲", "nextSceneIds": ["b"]},
                {"id": "b", "name": "乙", "nextSceneIds": ["a"]}
              ]
            }
        """.trimIndent()

        val result = codec.decode(json)

        assertTrue(result is ScriptPackParseResult.Failure)
        assertEquals(ScriptPackError.BrokenReference, (result as ScriptPackParseResult.Failure).error)
    }

    @Test
    fun `allows loop when scene opts in`() {
        // 环上有 loopEnabled 的场景时按有意的循环处理
        val json = """
            {
              "id": "loop-ok", "name": "有意循环", "mode": "Loop",
              "scenes": [
                {"id": "a", "name": "甲", "nextSceneIds": ["b"], "loopEnabled": true},
                {"id": "b", "name": "乙", "nextSceneIds": ["a", "end"]},
                {"id": "end", "name": "终点"}
              ]
            }
        """.trimIndent()

        assertTrue(codec.decode(json) is ScriptPackParseResult.Success)
    }

    @Test
    fun `rejects isolated scenes`() {
        // 典型坏剧本：多个场景各自独立，没有任何连接
        val json = """
            {
              "id": "islands", "name": "孤岛剧本", "startSceneId": "s1",
              "scenes": [
                {"id": "s1", "name": "序章"},
                {"id": "s2", "name": "孤岛甲"},
                {"id": "s3", "name": "孤岛乙"}
              ]
            }
        """.trimIndent()

        val result = codec.decode(json)

        assertTrue(result is ScriptPackParseResult.Failure)
        assertEquals(ScriptPackError.BrokenReference, (result as ScriptPackParseResult.Failure).error)
        assertTrue(result.detail.contains("s2") && result.detail.contains("s3"))
    }

    @Test
    fun `accepts scenes connected by unlock relation`() {
        // unlockedBy 也是通路：解锁关系能到达的场景不算孤岛
        val json = """
            {
              "id": "unlock-chain", "name": "解锁链", "startSceneId": "s1",
              "scenes": [
                {"id": "s1", "name": "序章"},
                {"id": "s2", "name": "支线", "unlockedBy": ["s1"]},
                {"id": "s3", "name": "终章", "unlockedBy": ["s2"]}
              ]
            }
        """.trimIndent()

        assertTrue(codec.decode(json) is ScriptPackParseResult.Success)
    }

    @Test
    fun `accepts multi entry reaching all scenes`() {
        // 多入口时，只要入口集合覆盖所有场景即可
        val json = """
            {
              "id": "multi", "name": "多入口",
              "startSceneIds": ["a", "b"],
              "scenes": [
                {"id": "a", "name": "甲线"},
                {"id": "b", "name": "乙线"}
              ]
            }
        """.trimIndent()

        assertTrue(codec.decode(json) is ScriptPackParseResult.Success)
    }
}
