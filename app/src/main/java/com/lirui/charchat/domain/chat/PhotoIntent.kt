package com.lirui.charchat.domain.chat

/**
 * 照片意图识别（纯函数，便于单测）。
 *
 * 用途：模型偶尔不写 [[PHOTO:…]]（尤其是小模型），玩家说"发张照片看看"却只得到文字描述，
 * 体验断裂。这里在客户端兜底判断"玩家本轮是否想要一张图"，若是且模型没给 PHOTO，
 * 就自动补出一张图，保证"要照片"必定有照片。
 */
object PhotoIntent {

    private val KEYWORDS = listOf(
        "照片", "自拍", "拍张", "拍个", "发张", "发个图", "发图", "图片", "图来看看",
        "看看你", "看下你", "看看你的", "你的样子", "长什么样", "长啥样", "穿的什么",
        "穿什么", "给我看看", "发我看看", "让我看看", "想看你", "要看你", "看看嘛",
        "photo", "selfie", "picture", "pic", "send me a pic"
    )

    /** 玩家本轮文本是否表达"想要一张图"。 */
    fun wantsPhoto(userText: String): Boolean {
        val t = userText.lowercase()
        if (t.isBlank()) return false
        return KEYWORDS.any { t.contains(it) }
    }

    /**
     * 兜底出图场景描述（中文）：模型漏写 [[PHOTO:…]] 时，作为【这张照片的内容】喂给
     * PhotoPromptComposer 拼装。保留角色名与设定摘要（角色设定给模型参考当前处境），
     * 并带上玩家本轮诉求；整体长度受限，避免提示词过长。
     */
    fun fallbackPrompt(cardName: String, description: String, userText: String): String {
        val name = cardName.trim().ifBlank { "角色" }
        val desc = description.replace(Regex("\\s+"), " ").trim().take(100)
        val ask = userText.replace(Regex("\\s+"), " ").trim().take(80)
        return buildString {
            append("玩家正在请$name")
            if (desc.isNotBlank()) append("（$desc）")
            append("发一张照片/自拍。玩家的原话：")
            append(if (ask.isNotBlank()) "\"$ask\"" else "想看你的样子")
            append("。请据此生成一张贴合当下情境的照片。")
        }
    }
}
