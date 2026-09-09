package com.lirui.charchat.domain.chat

/**
 * 出图提示词拼装器（纯函数，便于单测）。
 *
 * 将出图提示词拆成三层，解决「图不像她 / 图不贴剧情」：
 * 1. 【人物形象】= 角色视觉档案（固定不变，跨图一致性来源）；档案缺失时回落到属性面板的外貌/穿着。
 * 2. 【当前情境】= 最近若干轮对话（相关性来源，让画面落在当下剧情里）。
 * 3. 【这张照片的内容】= 本轮模型写的 [[PHOTO:…]] 画面描述，或 PhotoIntent 兜底出的场景。
 *
 * 统一中文直白描述（对阿里百炼 wanx / qwen-image 类中文文生图模型效果最好）。
 */
object PhotoPromptComposer {

    private const val MAX_ANCHOR = 200
    private const val MAX_HISTORY_LINES = 8   // ≈ 最近 4 轮（角色+玩家各算一条）
    private const val MAX_LINE = 60
    private const val MAX_SCENE = 300

    /**
     * @param name            角色名
     * @param visualAnchor    提炼出的固定视觉档案；为空则走 fallbackAppearance
     * @param fallbackAppearance 卡属性面板里的外貌/穿着摘要（档案缺失时降级用）
     * @param historyLines    最近几轮对话原文（逐条字符串，内部再做长度裁剪）
     * @param scene           本轮画面描述（模型 [[PHOTO:…]] 或 PhotoIntent 兜底）
     */
    fun compose(
        name: String,
        visualAnchor: String,
        fallbackAppearance: String = "",
        historyLines: List<String> = emptyList(),
        scene: String = ""
    ): String {
        val anchor = visualAnchor.trim().take(MAX_ANCHOR)
        val identity = when {
            anchor.isNotBlank() -> anchor
            fallbackAppearance.trim().isNotBlank() -> fallbackAppearance.trim().take(MAX_ANCHOR)
            else -> "（没有可用的外貌设定，按常见动漫美少女形象发挥，形象一旦出现需在后续图片保持一致）"
        }
        val history = historyLines
            .map { it.replace(Regex("\\s+"), " ").trim().take(MAX_LINE) }
            .filter { it.isNotBlank() }
            .take(MAX_HISTORY_LINES)
        val sceneClean = scene.replace(Regex("\\s+"), " ").trim().take(MAX_SCENE)

        return buildString {
            appendLine("中文绘图描述。这是${name.ifBlank { "角色" }}发来的一张照片，请据此生成图片。")
            appendLine()
            appendLine("【人物形象（每张图必须与此一致，严禁改变外形）】")
            appendLine(identity)
            appendLine()
            if (history.isNotEmpty()) {
                appendLine("【当前情境（最近对话，画面要贴合这里发生的剧情）】")
                history.forEach { appendLine("- $it") }
                appendLine()
            }
            appendLine("【这张照片的内容】")
            appendLine(if (sceneClean.isNotBlank()) sceneClean else "${name.ifBlank { "角色" }}以自然姿态出现在当前情境中")
            appendLine()
            appendLine("要求：人物的发型、发色、瞳色、脸型、体型、服装风格必须与【人物形象】一致；只允许出现与【这张照片的内容】相符的姿势、表情、动作与临时道具。画面清晰、细节精致、构图自然。")
        }
    }

    /** 由角色属性面板直接产出兜底形象描述（视觉档案为空时的降级输入）。 */
    fun fallbackAppearance(appearance: String, clothing: String): String {
        val a = appearance.trim().takeIf { it.isNotBlank() && it != "未设定" }
        val c = clothing.trim().takeIf { it.isNotBlank() && it != "日常便装" }
        return when {
            a != null && c != null -> "$a，常穿$c"
            a != null -> a
            c != null -> "衣着$c"
            else -> ""
        }
    }
}
