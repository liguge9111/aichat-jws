package com.lirui.charchat.data.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BackupModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val sample = BackupFile(
        version = BackupFile.BACKUP_VERSION,
        exportedAt = 1_700_000_000_000L,
        cards = listOf(
            BackupCard(
                id = "c1",
                name = "小樱",
                description = "咖啡师",
                scenario = "雨夜的咖啡店",
                firstMes = "欢迎光临～",
                avatarBase64 = "aGVsbG8=",
                attributesJson = "{\"affection\":42}",
                playerJson = "{\"name\":\"阿明\"}",
                affection = 42,
                relationship = "朋友",
                messages = listOf(
                    BackupMessage(role = "CHARACTER", text = "欢迎光临～", createdAt = 1L),
                    BackupMessage(role = "USER", text = "来杯拿铁", createdAt = 2L)
                )
            )
        )
    )

    @Test
    fun `备份文件序列化往返一致`() {
        val raw = json.encodeToString(BackupFile.serializer(), sample)
        val back = json.decodeFromString(BackupFile.serializer(), raw)
        assertEquals(sample.version, back.version)
        assertEquals(sample.exportedAt, back.exportedAt)
        assertEquals(1, back.cards.size)
        val c = back.cards.first()
        assertEquals("小樱", c.name)
        assertEquals("雨夜的咖啡店", c.scenario)
        assertEquals("aGVsbG8=", c.avatarBase64)
        assertEquals(42, c.affection)
        assertEquals("朋友", c.relationship)
        assertEquals(2, c.messages.size)
        assertEquals("来杯拿铁", c.messages.last().text)
    }

    @Test
    fun `缺少消息字段时回落空列表`() {
        val raw = """
            {"version":1,"exportedAt":1,"cards":[{"id":"c2","name":"无名"}]}
        """.trimIndent()
        val back = json.decodeFromString(BackupFile.serializer(), raw)
        val c = back.cards.single()
        assertEquals("无名", c.name)
        assertTrue("消息缺省为空", c.messages.isEmpty())
        assertNull("头像缺省为 null", c.avatarBase64)
    }

    @Test
    fun `空备份可正常解析`() {
        val back = json.decodeFromString(BackupFile.serializer(), """{"version":1}""")
        assertTrue(back.cards.isEmpty())
    }

    @Test
    fun `未知字段被忽略`() {
        val raw = """
            {"version":1,"cards":[],"futureField":{"x":1}}
        """.trimIndent()
        val back = json.decodeFromString(BackupFile.serializer(), raw)
        assertTrue(back.cards.isEmpty())
    }
}
