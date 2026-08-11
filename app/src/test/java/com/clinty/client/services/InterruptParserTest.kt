package com.clinty.client.services

import com.clinty.client.models.AppJson
import com.clinty.client.models.LangGraphThreadSummary
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class InterruptParserTest {
    private val sampleResponse = """
        [{
            "thread_id": "473d5eb9-5a83-15f2-db46-e90f729f5c86",
            "created_at": "2026-06-25T04:06:27.456440+00:00",
            "updated_at": "2026-06-25T05:18:07.549304+00:00",
            "status": "interrupted",
            "interrupts": {
                "ab691760-1e62-3f9c-ee9a-4615a590106a": [{
                    "id": "a487f5c7a33ada7420e346457bc7b700",
                    "value": [{
                        "action_request": {
                            "action": "send_email_tool",
                            "args": {
                                "email_address": "csuson@gmail.com",
                                "email_id": "19efcea6382feb98",
                                "response_text": "Hi Clint"
                            }
                        },
                        "config": {
                            "allow_accept": true,
                            "allow_edit": true,
                            "allow_ignore": true,
                            "allow_respond": true
                        },
                        "description": "Test description"
                    }]
                }]
            }
        }]
    """.trimIndent()

    @Test
    fun parsesInterruptFromThreadSearchResponse() {
        val threads = AppJson.instance.decodeFromString(
            ListSerializer(LangGraphThreadSummary.serializer()),
            sampleResponse,
        )
        assertEquals(1, threads.size)
        assertNotNull(threads[0].interrupts)

        val parsed = InterruptParser.interruptsFromThread(threads[0])
        assertNotNull("interrupts map: ${threads[0].interrupts}", parsed)
        assertEquals(
            "expected send_email_tool but got ${parsed!!.firstOrNull()?.actionRequest?.action}",
            1,
            parsed.size,
        )
        assertEquals("send_email_tool", parsed[0].actionRequest.action)

        val processed = InterruptParser.processInterruptedThread(threads[0])
        assertNotNull(processed)
        org.junit.Assert.assertFalse(processed!!.invalidSchema)
    }

    private val sampleResponseWithSubject = """
        [{
            "thread_id": "473d5eb9-5a83-15f2-db46-e90f729f5c86",
            "created_at": "2026-06-25T04:06:27.456440+00:00",
            "updated_at": "2026-06-25T05:18:07.549304+00:00",
            "status": "interrupted",
            "values": {
                "email_input": {
                    "subject": "Wing boarding lesson",
                    "from": "clint suson <clint.suson@hostview.ai>"
                }
            },
            "interrupts": {
                "ab691760-1e62-3f9c-ee9a-4615a590106a": [{
                    "id": "a487f5c7a33ada7420e346457bc7b700",
                    "value": [{
                        "action_request": {
                            "action": "send_email_tool",
                            "args": {"email_address": "csuson@gmail.com"}
                        },
                        "config": {
                            "allow_accept": true,
                            "allow_edit": true,
                            "allow_ignore": true,
                            "allow_respond": true
                        },
                        "description": "**Subject**: Wing boarding lesson\\n**From**: clint suson"
                    }]
                }]
            }
        }]
    """.trimIndent()

    @Test
    fun inboxSubjectAndDateFromSearchResponse() {
        val threads = AppJson.instance.decodeFromString(
            ListSerializer(LangGraphThreadSummary.serializer()),
            sampleResponseWithSubject,
        )
        val threadData = com.clinty.client.models.ThreadData(
            thread = threads[0],
            status = "interrupted",
            interrupts = InterruptParser.interruptsFromThread(threads[0]),
            invalidSchema = false,
        )
        assertEquals("Wing boarding lesson", threadData.inboxSubject())
        assertNotNull(threadData.inboxDate())
    }

    @Test
    fun decodesFullThreadSearchResponse() {
        val sampleResponse = """
            [{
                "thread_id": "473d5eb9-5a83-15f2-db46-e90f729f5c86",
                "created_at": "2026-06-25T04:06:27.456440+00:00",
                "updated_at": "2026-06-25T04:14:28.945626+00:00",
                "state_updated_at": "2026-06-25T04:14:28.945626+00:00",
                "metadata": {
                    "assistant_id": "140a05f8-c0fc-56fa-8dd8-c8fbdc457f0e",
                    "email_id": "19efcea6382feb98",
                    "graph_id": "kiteboarding_assistant"
                },
                "status": "interrupted",
                "values": {
                    "email_input": {
                        "subject": "Wing boarding lesson"
                    }
                },
                "interrupts": {
                    "75d82232-4af4-0fee-9800-2bf103942820": [{
                        "id": "731301a57405a950462087fda0d30016",
                        "value": [{
                            "action_request": {
                                "action": "send_email_tool",
                                "args": {
                                    "email_address": "csuson@gmail.com"
                                }
                            },
                            "config": {
                                "allow_accept": true,
                                "allow_edit": true,
                                "allow_ignore": true,
                                "allow_respond": true
                            },
                            "description": "Test"
                        }]
                    }]
                }
            }]
        """.trimIndent()

        val threads = AppJson.instance.decodeFromString(
            ListSerializer(LangGraphThreadSummary.serializer()),
            sampleResponse,
        )
        assertEquals(1, threads.size)
        assertEquals("interrupted", threads[0].status)
        assertEquals("Wing boarding lesson", threads[0].messageSubject())
        assertNotNull(InterruptParser.interruptsFromThread(threads[0]))
    }
}
