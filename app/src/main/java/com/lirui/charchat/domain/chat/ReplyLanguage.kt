package com.lirui.charchat.domain.chat

/**
 * 回复语言判定：跟随玩家本轮输入的语言。
 *
 * 背景：SillyTavern 生态的英文角色卡占比很高，模型容易被卡原文的语言带跑——
 * 玩家用中文发第一条消息，却收到英文回复。提示词里写死"用中文"也不对，
 * 因为玩家完全可能想用英文跟某个角色对话。所以按输入实时判定。
 */
object ReplyLanguage {

    const val CHINESE = "简体中文"
    const val ENGLISH = "English"

    private val HAN = Regex("[一-鿿]")
    private val KANA = Regex("[぀-ヿ]")
    private val HANGUL = Regex("[가-힯]")

    /**
     * @param text 玩家本轮输入（已过护栏）。
     * @return 用于提示词的语言名，如「简体中文」「English」。空输入回落到中文。
     */
    fun detect(text: String): String {
        if (text.isBlank()) return CHINESE
        val han = HAN.findAll(text).count()
        val kana = KANA.findAll(text).count()
        val hangul = HANGUL.findAll(text).count()
        return when {
            // 假名/谚文是各自语种的强特征，汉字则是中日的弱特征
            hangul > 0 && han < hangul -> "한국어"
            kana > 0 -> "日本語"
            han > 0 -> CHINESE
            else -> ENGLISH
        }
    }
}
