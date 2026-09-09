package com.lirui.charchat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class ApiKeyTest {

    @Test
    fun `粘贴污染被清洗`() {
        assertEquals("sk-abc123", ApiKey.normalize("  sk-abc123\n"))
        assertEquals("sk-abc123", ApiKey.normalize("Bearer sk-abc123"))
        assertEquals("sk-abc123", ApiKey.normalize("bearer sk-abc123"))
        assertEquals("sk-abc123", ApiKey.normalize("\"sk-abc123\""))
        assertEquals("sk-abc123", ApiKey.normalize("sk-abc 123"))
    }

    @Test
    fun `MiMo 的 sk 与 tp 不得混用域名`() {
        assertNotNull(
            "tp- Key 配按量付费域名应提示",
            ApiKey.mismatchHint("tp-xxx", "https://api.xiaomimimo.com/v1")
        )
        assertNull(
            "tp- Key 配 token-plan 域名应放行",
            ApiKey.mismatchHint("tp-xxx", "https://token-plan-cn.xiaomimimo.com/v1")
        )
        assertNotNull(
            "sk- Key 配 token-plan 域名应提示",
            ApiKey.mismatchHint("sk-xxx", "https://token-plan-cn.xiaomimimo.com/v1")
        )
        assertNull(ApiKey.mismatchHint("sk-xxx", "https://api.openai.com/v1"))
    }

    @Test
    fun `错误体提取人话`() {
        val body = """{"error":{"message":"Incorrect API key provided","code":"invalid_api_key"}}"""
        assertEquals("Incorrect API key provided", ApiError.describe(body))
        assertEquals("服务不可用", ApiError.describe("""{"msg":"服务不可用"}"""))
        assertEquals("", ApiError.describe(""))
    }
}
