package com.lirui.charchat.data.remote

import com.lirui.charchat.data.remote.model.ChatMsg
import com.lirui.charchat.data.remote.model.ChatRequest
import com.lirui.charchat.data.remote.model.ModelsListResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAIChatClientTest {

    /** SSE 增量分片正常提取 content。 */
    @Test
    fun parseContentChunk_returnsContent() {
        val data = """{"choices":[{"delta":{"content":"你好"}}]}"""
        assertEquals("你好", OpenAIChatClient.parseContentChunk(data))
    }

    /** [DONE] 信号与无内容分片应返回 null；有 content 的分片照常返回（即使带 finish_reason）。 */
    @Test
    fun parseContentChunk_handlesNonContent() {
        assertNull(OpenAIChatClient.parseContentChunk("[DONE]"))
        assertNull(OpenAIChatClient.parseContentChunk("""{"choices":[{"delta":{}}]}"""))
        assertNull(OpenAIChatClient.parseContentChunk("""{"choices":[{"delta":{"content":""}}]}"""))
        assertEquals(
            "x",
            OpenAIChatClient.parseContentChunk(
                """{"choices":[{"delta":{"content":"x"},"finish_reason":"stop"}]}"""
            )
        )
    }

    /** 多 choices（部分网关）取第一个有 content 的。 */
    @Test
    fun parseContentChunk_firstChoice() {
        val data = """{"choices":[{"delta":{}},{"delta":{"content":"第二条"}}]}"""
        assertEquals("第二条", OpenAIChatClient.parseContentChunk(data))
    }
}

class ChatModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** ChatRequest 序列化往返稳定。 */
    @Test
    fun chatRequest_roundTrip() {
        val req = ChatRequest(
            model = "gpt-4o",
            messages = listOf(ChatMsg("system", "你是A"), ChatMsg("user", "hi")),
            temperature = 0.9,
            stream = true
        )
        val s = json.encodeToString(ChatRequest.serializer(), req)
        val back = json.decodeFromString(ChatRequest.serializer(), s)
        assertEquals(req, back)
    }

    /** 未知字段（如上游扩展）不解析失败。 */
    @Test
    fun chatRequest_ignoresUnknownFields() {
        val s = """{"model":"x","messages":[{"role":"user","content":"y","extra":1}],"foo":"bar"}"""
        val req = json.decodeFromString(ChatRequest.serializer(), s)
        assertEquals("x", req.model)
        assertEquals(1, req.messages.size)
    }

    /** /models 响应解析。 */
    @Test
    fun modelsList_decode() {
        val s = """{"object":"list","data":[{"id":"gpt-4o","object":"model"},{"id":"gpt-4o-mini"}]}"""
        val r = json.decodeFromString(ModelsListResponse.serializer(), s)
        assertEquals(2, r.data.size)
        assertEquals("gpt-4o", r.data[0].id)
    }
}
