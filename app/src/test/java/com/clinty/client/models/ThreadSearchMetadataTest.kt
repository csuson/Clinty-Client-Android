package com.clinty.client.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class ThreadSearchMetadataTest {
    @Test
    fun usesGraphIdForNonUuid() {
        val metadata = ThreadSearchMetadata("kiteboarding_assistant")
        assertEquals("kiteboarding_assistant", metadata.graphId)
        assertNull(metadata.assistantId)
    }

    @Test
    fun usesAssistantIdForUuid() {
        val uuid = UUID.randomUUID().toString()
        val metadata = ThreadSearchMetadata(uuid)
        assertNull(metadata.graphId)
        assertEquals(uuid, metadata.assistantId)
    }
}
