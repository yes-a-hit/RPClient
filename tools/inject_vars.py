"""把可见变量注入提示词，别让模型自己编数字。"""

from pathlib import Path

ROOT = Path(r"E:\dsh-sp\rpclient\app\src\main\java\me\kafuuneko\rpclient")
SESSION = ROOT / "libs/script/ScriptPlaySession.kt"
VM = ROOT / "feature/scriptplay/ScriptPlayViewModel.kt"

# ---------- 1. PromptBuilder 接收变量 ----------
t = SESSION.read_text(encoding="utf-8")
old = """        castOverride: List<String>? = null
    ): String {"""
new = """        castOverride: List<String>? = null,
        /**
         * 当前变量快照。
         *
         * 不告诉模型的话它会自己编数值，嘴里说的电量和实际判定对不上；
         * 只注入声明里 visible 的那些，隐藏变量不该被念出来。
         */
        variables: Map<String, Int> = emptyMap(),
        declaredVariables: List<ScriptVariable> = emptyList()
    ): String {"""
if old in t:
    t = t.replace(old, new, 1)
    print("signature ok")

anchor = """        builder.append("\\n【当前场景】").append(scene.name).append('\\n')"""
insert = """        builder.append("\\n【当前场景】").append(scene.name).append('\\n')

        // 可见变量写进提示词：模型据此叙述，不能自己另编一套数值
        val shown = declaredVariables.filter { it.visible && it.name.isNotBlank() }
        if (shown.isNotEmpty()) {
            builder.append("\\n【当前状态】")
            builder.append(
                shown.joinToString("、") { variable ->
                    val label = variable.label.ifBlank { variable.name }
                    label + " " + (variables[variable.name] ?: variable.initial)
                }
            )
            builder.append("\\n以上数值由系统维护，叙述时以此为准，不要另编数字。\\n")
        }
"""
if anchor in t and "【当前状态】" not in t:
    t = t.replace(anchor, insert, 1)
    print("inject ok")

SESSION.write_text(t, encoding="utf-8", newline="\n")

# ---------- 2. 调用处传参 ----------
t = VM.read_text(encoding="utf-8")
old2 = """            castOverride = mPlay.castOverride ?: ScriptPlayEngine.effectiveCast(
                scene = scene,
                variables = mPlay.variables.orEmpty()
            )"""
new2 = """            castOverride = mPlay.castOverride ?: ScriptPlayEngine.effectiveCast(
                scene = scene,
                variables = mPlay.variables.orEmpty()
            ),
            variables = mPlay.variables.orEmpty(),
            declaredVariables = pack.variables"""
if old2 in t:
    t = t.replace(old2, new2, 1)
    print("call ok")
VM.write_text(t, encoding="utf-8", newline="\n")
