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
    val nsfwFilterEnabled: Boolean = false,
    val ageVerified: Boolean = false
)
