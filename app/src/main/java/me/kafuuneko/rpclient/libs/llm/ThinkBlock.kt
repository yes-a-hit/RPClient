package me.kafuuneko.rpclient.libs.llm

/**
 * 思维链与正文的隔离。
 *
 * 模型返回的思考过程用 `<think>…</think>` 包在正文前面，拼成一整段存进消息。
 * 渲染时前端会把它拆成可折叠的块，但**编辑**那条路走的是数据库原文——
 * 于是用户点开编辑框，看到的是一大段推理加上正文，改起来无从下手，
 * 一不小心还会把整段思维链删掉。
 *
 * 这里只做一件事：把两者切干净。
 */

private const val OPEN_TAG = "<think>"
private const val CLOSE_TAG = "</think>"

/** 消息里是否带思维链块。 */
fun hasThinkBlock(content: String): Boolean =
    content.contains(OPEN_TAG) && content.contains(CLOSE_TAG)

/** 取出思维链正文；没有就返回空串。 */
fun extractThinkBlock(content: String): String {
    val start = content.indexOf(OPEN_TAG)
    if (start < 0) return ""
    val close = content.indexOf(CLOSE_TAG, start)
    if (close < 0) return ""
    return content.substring(start + OPEN_TAG.length, close).trim()
}

/**
 * 剥掉思维链，只留正文。
 *
 * 编辑框里应该只出现这个结果——推理过程不是给用户改的东西。
 *
 * 被打断的思考（有开标记没闭标记）同样要剥：那种情况下标记之后
 * 全是没写完的推理，正文还没开始。第一版对未闭合标记「放行」，
 * 结果是半截推理永远赖在消息里，既删不掉也没法编辑。
 */
fun stripThinkBlock(content: String): String {
    var result = content
    while (true) {
        val start = result.indexOf(OPEN_TAG)
        if (start < 0) break
        val close = result.indexOf(CLOSE_TAG, start)
        if (close < 0) {
            // 没闭合：从标记到末尾都是没写完的思考
            result = result.substring(0, start)
            break
        }
        result = result.removeRange(start, close + CLOSE_TAG.length)
    }
    return result.trim()
}

/**
 * 用新的正文替换原有的正文，思维链原样保留。
 *
 * 保存编辑时走这里：用户改的是正文，推理过程不该跟着一起被覆盖，
 * 也不该因为编辑而丢失——它是这条回复当时怎么想出来的记录。
 */
fun replaceContentKeepingThink(original: String, newContent: String): String =
    composeThinkContent(extractThinkBlock(original), newContent)

/**
 * 把思维链与正文拼回一条消息。
 *
 * 编辑时两者分开展示，保存时用这个拼回去。
 * 思维链清空就不写标签——用户删掉推理，就该真的没有推理。
 */
fun composeThinkContent(think: String, body: String): String {
    val reasoning = think.trim()
    val content = body.trim()
    if (reasoning.isBlank()) return content
    return OPEN_TAG + "\n" + reasoning + "\n" + CLOSE_TAG + "\n\n" + content
}
