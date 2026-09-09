package com.lirui.charchat.domain.chat

/**
 * 单轮回复解析（Q5=A 单次切分）：
 * - 用 [[NEXT]] 切成最多 5 条气泡
 * - 每条中提取 [[PHOTO: ...]] 作为出图提示，并从正文移除
 * - 剥离 [[AFF]]/[[REL]]/[[STATUS]]/[[NOTE]]/[[MEM]] 等隐藏信号（由 AttributeEngine 单独解析，不进气泡）
 * - 统一清洗：markdown 符号、模型偶发的方括号/黑括号标记变体、多余空白
 *
 * 纯函数，便于单测。
 */
object RoundController {

    const val MAX_BUBBLES = 5

    data class Bubble(val text: String, val photoPrompt: String? = null)
    data class ParsedRound(val bubbles: List<Bubble>, val photoCount: Int)

    /** 消息分隔：兼容 [[NEXT]] / [NEXT] / 【NEXT】 三种写法。 */
    private val NEXT = Regex("(?is)(?:\\[{1,2}\\s*NEXT\\s*\\]{1,2})|(?:【\\s*NEXT\\s*】)")
    /** 出图提示：半角与全角方括号都认。 */
    private val PHOTO = Regex("(?is)\\[{1,2}\\s*PHOTO\\s*[:：]\\s*(.*?)\\s*\\]{1,2}")
    private val PHOTO_CJK = Regex("(?is)【\\s*PHOTO\\s*[:：]\\s*(.*?)\\s*】")
    /**
     * 所有控制类标记（含变体与中文黑括号写法）。模型偶尔输出 [NEXT]、[[AFF +3]]、
     * 【STATUS: …】等写法，若只认标准形式就会把残片显示给玩家（"莫名其妙的字符"）。
     */
    private val CONTROL = Regex(
        "(?is)(\\[{1,2}\\s*|【\\s*)(AFF|REL|STATUS|NOTE|MEM)\\s*[:：]?[^\\]\\[【】]*(\\]{1,2}|】)"
    )
    private val CONTROL_PHOTO_CJK = Regex("(?is)【\\s*PHOTO\\s*[:：][^【】]*】")
    /** 兜底：任何形如 [[XXX: …]] 的未知控制标记（模型自造标签时也不会露给玩家）。 */
    private val STRAY_TAG = Regex("(?is)\\[{1,2}\\s*[A-Za-z]{2,8}\\s*[:：][^\\n]{0,300}?\\]{1,2}")
    /**
     * 全角方括号兜底：模型偶尔会用【SFW/x/y/z】、【tag:value】这类自创标签，
     * 没在半角控制正则里覆盖。已知控制标签白名单（NEXT/PHOTO/AFF/REL/STATUS/NOTE/MEM）放行，其余整段移除。
     */
    private val FULLWIDTH_BRACKETED = Regex("(?is)【\\s*([^】]+?)\\s*】")
    private val KNOWN_TAGS = setOf("NEXT", "PHOTO", "AFF", "REL", "STATUS", "NOTE", "MEM")

    private val MD_BOLD = Regex("\\*{2,}|_{2,}")
    private val MD_ITALIC = Regex("(?s)\\*([^*\\n]{1,24})\\*")
    private val MD_CODE = Regex("`+")
    private val MD_HEAD = Regex("(?m)^#{1,6}\\s*")
    /** 水平分割线：整行 ---/***/___，剥离后留下空行避免上下粘连。 */
    private val MD_HR = Regex("(?m)^[ \\t]*(?:---+|\\*{3,}|_{3,})[ \\t]*$")
    private val STRAY_BRACKETS = Regex("\\[{1,2}\\s*\\]{1,2}")

    fun parseRound(raw: String): ParsedRound {
        // 先按 NEXT 切分（含半角与全角），再在每段里：先抠 PHOTO 提示，再剥控制，最后清洗可见文本。
        val segments = NEXT.split(raw)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val capped = if (segments.size > MAX_BUBBLES) {
            val head = segments.take(MAX_BUBBLES - 1)
            val tail = segments.drop(MAX_BUBBLES - 1).joinToString(" ")
            head + tail
        } else segments

        val bubbles = capped.map { seg ->
            val halfMatch = PHOTO.find(seg)
            val fullMatch = halfMatch ?: PHOTO_CJK.find(seg)
            val prompt = fullMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            // 抠掉 PHOTO 后再去控制信号（AFF/STATUS/NOTE/MEM 等），最后 sanitizeVisible
            val photoStripped = if (halfMatch != null) PHOTO.replace(seg) { " " }
                                else if (fullMatch != null) PHOTO_CJK.replace(seg) { " " }
                                else seg
            val cleaned = stripControl(photoStripped)
            Bubble(sanitizeVisible(cleaned), prompt)
        }
        return ParsedRound(bubbles, bubbles.count { it.photoPrompt != null })
    }

    /** 流式预览：去掉所有控制标记与杂质，避免把 [[NEXT]]/[[PHOTO]]/[[MEM]] 等显示给用户。 */
    fun sanitizePreview(raw: String): String {
        val s0 = stripControl(raw)
        val s1 = NEXT.replace(s0) { " " }
        val s2 = PHOTO.replace(s1) { " " }
        val s3 = PHOTO_CJK.replace(s2) { " " }
        return sanitizeVisible(s3)
    }

    /**
     * 玩家可见文本的最终清洗：
     * 去 markdown 记号、去残留空括号、折行压成空格（微信式单条气泡）、压缩连续空白。
     */
    fun sanitizeVisible(text: String): String {
        var s = stripUnknownFullwidthTags(text)        // 全角【未知标签】先剥
        s = CONTROL_PHOTO_CJK.replace(s) { " " }        // 兜底再过一遍（已知控制）
        s = MD_HEAD.replace(s) { "" }
        s = MD_HR.replace(s) { "\n" }
        s = MD_BOLD.replace(s) { "" }
        s = MD_ITALIC.replace(s) { it.groupValues[1] }
        s = MD_CODE.replace(s) { "" }
        s = STRAY_BRACKETS.replace(s) { "" }
        s = s.replace("\r", " ").replace("\n", " ")
        s = Regex("\\s{2,}").replace(s) { " " }
        return s.trim()
    }

    private fun stripControl(raw: String): String {
        val s0 = CONTROL.replace(raw) { "" }
        val s1 = CONTROL_PHOTO_CJK.replace(s0) { " " }
        // 全角兜底在 sanitizeVisible 阶段执行（不剥 NEXT——NEXT 由 NEXT.split 提前处理）
        return STRAY_TAG.replace(s1) { m ->
            if (m.value.contains("PHOTO", ignoreCase = true)) m.value else ""
        }
    }

    /** 全角方括号兜底：已知控制标签放行，其余整段移除（如【SFW/qingyue/gxcp/calm/17】）。 */
    private fun stripUnknownFullwidthTags(raw: String): String {
        return FULLWIDTH_BRACKETED.replace(raw) { m ->
            val inside = m.groupValues[1].trim()
            // 取首个非空 token 作为"标签头"
            val head = inside.split(Regex("[\\s:：/，,]+")).firstOrNull()?.uppercase().orEmpty()
            if (head in KNOWN_TAGS) m.value else ""
        }
    }
}
