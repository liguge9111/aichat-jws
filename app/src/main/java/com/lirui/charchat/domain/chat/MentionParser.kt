package com.lirui.charchat.domain.chat

/**
 * 群聊 @提及解析。纯函数，便于单测。
 *
 * 规则：
 * - 只认成员全名（`@小樱`），不认含空格的片段，避免"@所有人"被误判为定向。
 * - 成员名按长度降序匹配，避免"@小樱"被"@小"这类前缀抢先（长名优先）。
 * - 无 @ 或 @ 的名字不在成员中 → 视为广播（返回空列表）。
 */
object MentionParser {

    /**
     * 解析被 @ 到的成员名（去重、保持出现顺序）。
     * @param text 玩家输入原文
     * @param memberNames 群成员显示名
     */
    fun parse(text: String, memberNames: List<String>): List<String> {
        if (memberNames.isEmpty()) return emptyList()
        val sorted = memberNames
            .filter { it.isNotBlank() }
            .distinct()
            .sortedByDescending { it.length }

        val found = LinkedHashSet<String>()
        var rest = text
        // 逐个扫描 @ 位置，用最长名优先匹配
        var idx = rest.indexOf('@')
        while (idx >= 0) {
            val after = rest.substring(idx + 1)
            val hit = sorted.firstOrNull { name -> after.startsWith(name) }
            if (hit != null) {
                found.add(hit)
                rest = rest.substring(0, idx) + rest.substring(idx + 1 + hit.length)
                idx = rest.indexOf('@', idx)
            } else {
                idx = rest.indexOf('@', idx + 1)
            }
        }
        return found.toList()
    }

    /** 是否命中了全员广播关键词（@全体成员 / @所有人 / @all），用于显式广播。 */
    fun isBroadcast(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("@全体成员") || t.contains("@所有人") || t.contains("@all")
    }

    /**
     * 剥离被识别的 @名字，得到真正要说的内容。
     * 注意：未匹配到成员的 @xxx 会保留（可能只是普通文本里的邮箱之类）。
     */
    fun strip(text: String, memberNames: List<String>): String {
        var out = text
        memberNames.filter { it.isNotBlank() }.distinct()
            .sortedByDescending { it.length }
            .forEach { name -> out = out.replace("@$name", "") }
        return out.trim()
    }
}
