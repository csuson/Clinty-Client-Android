package com.clinty.client.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InterruptDescriptionParserTest {
    private val sampleDescription = """
        **Subject**: Wing boarding lesson
        **From**: clint suson <clint.suson@hostview.ai>
        **To**: csuson@gmail.com
        **ID**: 19efcea6382feb98

        Hi Clint,

        Can we schedule a lesson?
    """.trimIndent()

    private val sampleWithDraft = """
        **Subject**: Re: Wing boarding lesson
        **From**: clint suson <clint.suson@hostview.ai>

        Thanks for reaching out.
        ---
        **Reply to message id**: 19efcea6382feb98
        **From**: assistant@clinty.net

        Hi Clint,

        I'd be happy to help schedule a lesson.
    """.trimIndent()

    @Test
    fun parsesEmailSection() {
        val parsed = InterruptDescriptionParser.parse(sampleDescription)
        assertNotNull(parsed)
        assertEquals("Wing boarding lesson", parsed!!.email?.subject)
        assertEquals("clint suson <clint.suson@hostview.ai>", parsed.email?.from)
        assertEquals("csuson@gmail.com", parsed.email?.to)
        assertEquals("19efcea6382feb98", parsed.email?.id)
        assertEquals("Hi Clint,\n\nCan we schedule a lesson?", parsed.email?.body)
        assertNull(parsed.draft)
    }

    @Test
    fun parsesEmailAndDraftSections() {
        val parsed = InterruptDescriptionParser.parse(sampleWithDraft)
        assertNotNull(parsed)
        assertEquals("Re: Wing boarding lesson", parsed!!.email?.subject)
        assertEquals("19efcea6382feb98", parsed.draft?.replyToMessageId)
        assertEquals("assistant@clinty.net", parsed.draft?.from)
        assertEquals(
            "Hi Clint,\n\nI'd be happy to help schedule a lesson.",
            parsed.draft?.body,
        )
    }

    @Test
    fun displayTitleAndFromUseParsedDescription() {
        val interrupt = HumanInterrupt(
            actionRequest = ActionRequest(action = "send_email_tool"),
            config = HumanInterruptConfig(
                allowIgnore = true,
                allowRespond = true,
                allowEdit = true,
                allowAccept = true,
            ),
            description = sampleDescription,
        )

        assertEquals("Wing boarding lesson", interrupt.displayTitle())
        assertEquals("clint suson <clint.suson@hostview.ai>", interrupt.displayFrom())
    }

    @Test
    fun displayTitleFallsBackToActionName() {
        val interrupt = HumanInterrupt(
            actionRequest = ActionRequest(action = "send_email_tool"),
            config = HumanInterruptConfig(
                allowIgnore = true,
                allowRespond = true,
                allowEdit = true,
                allowAccept = true,
            ),
            description = "Plain text without metadata",
        )

        assertEquals("send_email_tool", interrupt.displayTitle())
        assertNull(interrupt.displayFrom())
    }
}
