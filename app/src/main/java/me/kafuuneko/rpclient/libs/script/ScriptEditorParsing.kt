package me.kafuuneko.rpclient.libs.script

/**
 * 编辑器里的文本输入转成数据。
 *
 * 这些解析规则散在 ViewModel 里就没法单测，而它们最容易出错的地方
 * 恰恰是边界：多一个空格、少一个数字、中文标点混进来。
 * 抽成纯函数，用例才好钉。
 */

/**
 * 解析数值副作用，每行一条，如「电量 -10」「好感 +2」。
 *
 * 只写变量名时按 +1 处理——作者写「好感」通常就是想加一点，
 * 让它静默失败反而更让人困惑。整行解析不出变量名才丢弃。
 */
fun parseEffectLines(raw: String): List<ScriptEffect> = raw
    .lines()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .mapNotNull { line ->
        val match = Regex("""(\S+)\s*([+-]?\s*\d+)?""").find(line) ?: return@mapNotNull null
        val variable = match.groupValues[1]
        if (variable.isBlank()) return@mapNotNull null
        val delta = match.groupValues.getOrNull(2)
            ?.replace(" ", "")
            ?.toIntOrNull()
            ?: 1
        ScriptEffect(variable = variable, delta = delta)
    }

/**
 * 解析线索行，每行「变量名 内容」。
 *
 * 内容里允许有空格，所以只按第一个空白切分；
 * 缺变量名或缺内容的行直接跳过——线索写错半条不如不记。
 */
fun parseClueLines(raw: String): List<ScriptEffect> = raw
    .lines()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .mapNotNull { line ->
        val separator = line.indexOfFirst { it.isWhitespace() }
        if (separator <= 0) return@mapNotNull null
        val variable = line.substring(0, separator).trim()
        val content = line.substring(separator).trim()
        if (variable.isBlank() || content.isBlank()) {
            null
        } else {
            ScriptEffect(variable = variable, appendText = content)
        }
    }
