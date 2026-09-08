package com.wegood.app

import com.wegood.app.bt.BtMsg
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 蓝牙直连协议契约：两端字段名/默认值必须稳定，否则跨版本解析会静默丢消息 */
class BtMsgTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun heart_roundtrip() {
        val m = BtMsg(t = "heart", kind = "kiss", from = "宝贝", ts = 1_700_000_000_000)
        val s = json.encodeToString(BtMsg.serializer(), m)
        assertEquals(m, json.decodeFromString(BtMsg.serializer(), s))
    }

    @Test
    fun hello_defaults() {
        val m = json.decodeFromString(BtMsg.serializer(), """{"t":"hello"}""")
        assertEquals("hello", m.t)
        assertNull(m.kind)
        assertNull(m.from)
        assertNull(m.name)
        assertEquals(0L, m.ts)
    }

    @Test
    fun ignores_unknown_keys_for_forward_compat() {
        val m = json.decodeFromString(BtMsg.serializer(), """{"t":"heart","kind":"hug","newField":1}""")
        assertEquals("hug", m.kind)
    }
}
