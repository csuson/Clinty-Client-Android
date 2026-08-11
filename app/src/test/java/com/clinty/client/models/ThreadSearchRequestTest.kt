package com.clinty.client.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadSearchRequestTest {
    @Test
    fun encodesGraphIdWithoutNullAssistantId() {
        val json = AppJson.encodeToString(
            ThreadSearchRequest(offset = 0, limit = 25, graphId = "kiteboarding_assistant"),
        )

        assertTrue(json.contains("\"graph_id\":\"kiteboarding_assistant\""))
        assertTrue(json.contains("\"status\":\"interrupted\""))
        assertFalse(json.contains("assistant_id"))
    }

    @Test
    fun encodesAssistantIdForUuidGraphId() {
        val uuid = "140a05f8-c0fc-56fa-8dd8-c8fbdc457f0e"
        val json = AppJson.encodeToString(
            ThreadSearchRequest(offset = 0, limit = 25, graphId = uuid),
        )

        assertTrue(json.contains("\"assistant_id\":\"$uuid\""))
        assertFalse(json.contains("graph_id"))
    }
}
