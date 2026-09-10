package com.lirui.charchat.data.settings

/**
 * 全局 BYOK 配置。所有 Key 通过 EncryptedSharedPreferences 加密落盘，不进明文。
 * 对话 / 出图均支持任意 OpenAI 兼容网关（baseURL / key / model 全可调）。
 */
data class ApiConfig(
    val chatBaseUrl: String = "https://api.openai.com/v1",
    val chatApiKey: String = "",
    val chatModel: String = "gpt-4o",
    val imageBaseUrl: String = "https://api.openai.com/v1",
    val imageApiKey: String = "",
    val imageModel: String = "gpt-image-1",
    // ---- 语音：TTS 把角色回复合成为语音条；ASR 把玩家语音转成文字送进对话模型 ----
    /** 语音合成（TTS）Base URL，留空复用对话侧。 */
    val ttsBaseUrl: String = "",
    val ttsApiKey: String = "",
    /** 合成模型名，例如 qwen-tts / cosyvoice-v1 / speech-02-hd。 */
    val ttsModel: String = "",
    /** 音色（口音）ID，例如 Cherry / longxiaochun；留空由服务端决定。 */
    val ttsVoice: String = "",
    /** 语音识别（ASR）Base URL，留空复用 TTS 侧配置。 */
    val asrBaseUrl: String = "",
    val asrApiKey: String = "",
    /** 识别模型名，例如 mimo-v2.5-asr / whisper-1 / paraformer-v2。 */
    val asrModel: String = "",
    /** 识别语言：auto（自动）/ zh（中文）/ en（英文）。固定语言可明显提升识别准确率。 */
    val asrLanguage: String = "zh",
    val nsfwFilterEnabled: Boolean = false,
    val ageVerified: Boolean = false
)
