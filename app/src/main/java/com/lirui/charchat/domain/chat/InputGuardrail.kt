package com.lirui.charchat.domain.chat

/**
 * 玩家输入护栏（Q2=A，零额外调用）：发送前剥离
 * 1) 第三人称描述角色（她/他/{name} 起头的叙述句）
 * 2) 试图修改锁定数值（好感度/关系/亲密 的赋值或命令）
 * 3) 明显的越权元指令（"忽略以上"/"你是助手"等）
 * 纯函数，便于单测。启发式，可能漏判（已在计划文档 §5.7 标注）。
 */
object InputGuardrail {

    data class Result(val text: String, val stripped: Boolean)

    // 锁定数值直接赋值：好感度/好感/关系/亲密/等级 后跟数字
    private val NUMERIC = Regex(
        "(好感度|好感|关系|亲密|等级|relationship|affection)\\s*[:：=]?\\s*(\\d{1,3})"
    )
    // 锁定数值命令式：…变成/改为/设为…
    private val NUMERIC_CMD = Regex(
        "(好感度|好感|关系|亲密|等级)\\s*[^，。\\n]{0,12}?(变成|改为|设为|设置|调整为|调到)\\s*[^，。\\n]{1,10}"
    )
    // 越权元指令
    private val META = Regex("(忽略(以上|前面|之前).{0,20})|(系统提示|system\\s*prompt|你是.{0,10}助手)")

    fun sanitize(input: String, charName: String = ""): Result {
        var had = false
        var text = input

        if (NUMERIC.containsMatchIn(text)) {
            text = NUMERIC.replace(text) { "" }
            had = true
        }
        if (NUMERIC_CMD.containsMatchIn(text)) {
            text = NUMERIC_CMD.replace(text) { "" }
            had = true
        }
        if (META.containsMatchIn(text)) {
            text = META.replace(text) { "" }
            had = true
        }

        val lines = text.split("\n").map { line ->
            if (isThirdPersonLine(line, charName)) {
                had = true
                "" // 删除该叙述行
            } else line
        }
        text = lines.filter { it.isNotBlank() }.joinToString("\n").trim()

        // 有过滤发生时返回处理结果（可能被清空，由编排层兜底提示）；
        // 无过滤时原样返回，避免误改。
        return Result(if (had) text else input.trim(), had)
    }

    private fun isThirdPersonLine(line: String, charName: String): Boolean {
        val t = line.trim()
        if (t.length < 2) return false
        val starters = listOf("她", "他", "它") +
                if (charName.isNotBlank()) listOf(charName) else emptyList()
        if (!starters.any { t.startsWith(it) }) return false
        val verb = Regex("(在|正在|穿着|说|说道|觉得|想|认为|看起来|已经|慢慢|突然|轻轻|微笑|脸红|低声|转身|走|坐|站|看|伸|摸|抱|吻|靠|靠在)")
        return verb.containsMatchIn(t)
    }
}
