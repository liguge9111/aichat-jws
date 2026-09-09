package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.CharacterCard

/**
 * 角色视觉档案（visual anchor）的提炼逻辑（纯函数，便于单测）。
 *
 * 目的：图像生成要"同一个人长得一致"，但角色卡大多没有结构化外貌信息，且模型每次
 * 自由发挥容易漂移。这里在首次出图前让文本模型通读整张卡，提炼一份固定的中文外貌
 * 基线（≤200 字）落库；之后每次出图把它拼进提示词，锁死"她长什么样"。
 */
object VisualAnchor {

    /** 提炼素材源：汇总卡里可能承载外貌信息的段落（控制总长，防上下文爆炸）。 */
    fun buildSource(card: CharacterCard): String {
        val a = card.attributes
        val parts = buildList {
            add("名字：${card.name}")
            if (card.description.isNotBlank()) add("角色设定：${oneLine(card.description).take(500)}")
            if (card.personality.isNotBlank()) add("性格：${oneLine(card.personality).take(200)}")
            if (card.scenario.isNotBlank()) add("背景设定：${oneLine(card.scenario).take(200)}")
            a.appearance.takeIf { it.isNotBlank() && it != "未设定" }?.let { add("外貌（属性面板）：${oneLine(it)}") }
            a.clothing.takeIf { it.isNotBlank() && it != "日常便装" }?.let { add("穿着（属性面板）：${oneLine(it)}") }
            a.location.takeIf { it.isNotBlank() && it != "未设定" }?.let { add("所在位置：${oneLine(it)}") }
            a.doing.takeIf { it.isNotBlank() && it != "空闲" }?.let { add("正在做什么：${oneLine(it)}") }
            if (card.firstMes.isNotBlank()) add("开场白示例：${oneLine(card.firstMes).take(160)}")
            if (card.mesExample.isNotBlank()) add("对话示例：${oneLine(card.mesExample).take(240)}")
        }
        return parts.joinToString("\n").take(2400)
    }

    /** 提炼请求：一条 user 消息即可（不含历史，只用卡资料，保证档案是"卡的本源形象"）。 */
    fun buildPrompt(card: CharacterCard): String = buildString {
        appendLine("下面是一张角色扮演角色卡的全部设定。请提炼一份「角色视觉档案」，专门用于 AI 绘图，让角色在不同图片中保持同一形象。")
        appendLine("只提炼可以视觉化的内容：发型与发色、瞳色、五官与脸型、体型、肤色、气质神态、常服的款式与配色、整体画风（如古风仙侠、现代都市、西幻等）。")
        appendLine("设定里没有写到的外貌细节一律省略，严禁自行编造或脑补。宁可少写，不要编造。")
        appendLine("输出格式（必须严格按此格式，除此之外不要输出任何内容）：")
        appendLine("<视觉档案>用第三人称写一段 100~150 字的中文形象设定，像交给画师的人物设定说明</视觉档案>")
        appendLine("========== 角色卡设定 ==========")
        append(buildSource(card))
    }

    /**
     * 从模型输出里解析视觉档案文本；格式失效时把原文当档案，仍不可用则返回空串。
     * 结果统一压成一行并裁剪到 200 字。
     */
    fun parse(raw: String): String {
        val text = raw.trim()
        if (text.isBlank()) return ""
        // 1) 优先取 <视觉档案>…</视觉档案> 标签内容
        var content = Regex("(?s)<\\s*视觉档案\\s*>(.*?)<\\s*/\\s*视觉档案\\s*>")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        // 2) 无标签：若模型把整段包在 markdown 代码块里，取代码块内部文本
        if (content.isNullOrBlank() && text.startsWith("```")) {
            val inner = text
                .removePrefix("```")
                .removeSuffix("```")
                .removePrefix("\n")
                .removeSuffix("\n")
                .trim()
            content = inner
        }
        val src = content?.takeIf { it.isNotBlank() } ?: text
        return src.replace(Regex("\\s+"), " ").trim().take(200)
    }

    private fun oneLine(s: String): String = s.replace(Regex("\\s+"), " ").trim()
}
