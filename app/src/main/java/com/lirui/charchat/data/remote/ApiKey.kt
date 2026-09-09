package com.lirui.charchat.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * API Key 归一化：解决"复制粘贴导致的 401"。
 *
 * 常见粘贴污染：
 * - 首尾空格 / 换行（手机输入法与网页复制极易带入）
 * - 连 "Bearer " 前缀一起复制 → 请求头变成 "Bearer Bearer sk-xxx"
 * - 编辑器自动加的引号
 * - 中间被插入的不可见空白
 */
object ApiKey {

    fun normalize(raw: String): String = raw
        .trim()
        .removePrefix("Bearer")
        .removePrefix("bearer")
        .trim()
        .trim('"', '\'', '“', '”', '‘', '’')
        .replace(Regex("\\s+"), "")

    /**
     * 网关与 Key 类型不匹配的定性提示。
     *
     * 典型：小米 MiMo 的按量付费 Key（sk-）走 api.xiaomimimo.com，
     * Token Plan Key（tp-）必须走控制台给出的 token-plan-xxx.xiaomimimo.com，
     * 两者混用一律 401（官方错误码表明确列出）。
     */
    fun mismatchHint(key: String, baseUrl: String): String? {
        if (key.isBlank()) return null
        val isTokenPlan = key.startsWith("tp-", ignoreCase = true)
        val isPayAsYouGo = key.startsWith("sk-", ignoreCase = true)
        val tokenHost = baseUrl.contains("token-plan", ignoreCase = true)
        return when {
            isTokenPlan && !tokenHost ->
                "你填的是 Token Plan 专用 Key（tp- 开头），必须搭配控制台给出的 token-plan-xxx 专属 Base URL，不能和按量付费地址混用"
            isPayAsYouGo && tokenHost ->
                "你填的是按量付费 Key（sk- 开头），不应搭配 Token Plan 专属 Base URL"
            else -> null
        }
    }
}

/** 把网关返回的错误体（JSON）翻译成人能看懂的一句话。 */
object ApiError {

    private val json = Json { ignoreUnknownKeys = true }

    /** 尽量提取 error.message / message / msg，取不到就原样截断返回。 */
    fun describe(body: String, maxLen: Int = 200): String {
        if (body.isBlank()) return ""
        val direct = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val err = root["error"]
            val fromErr = err?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: err?.jsonPrimitive?.contentOrNull
            fromErr
                ?: root["message"]?.jsonPrimitive?.contentOrNull
                ?: root["msg"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val text = direct ?: body
        return text.replace(Regex("\\s+"), " ").trim().take(maxLen)
    }
}
