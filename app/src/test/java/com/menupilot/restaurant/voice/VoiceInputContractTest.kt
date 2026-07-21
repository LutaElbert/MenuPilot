package com.menupilot.restaurant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceInputContractTest {

    @Test
    fun `normalizes the first usable transcript`() {
        val transcript = firstUsableVoiceTranscript(
            listOf("  ", "  I’m allergic\nto peanuts.  ", "unused"),
        )

        assertEquals("I’m allergic to peanuts.", transcript)
    }

    @Test
    fun `rejects blank recognition candidates`() {
        assertNull(firstUsableVoiceTranscript(null))
        assertNull(firstUsableVoiceTranscript(listOf("", " \n\t ")))
    }

    @Test
    fun `bounds transcript length before it reaches ordering state`() {
        val transcript = firstUsableVoiceTranscript(
            listOf("a".repeat(MAX_VOICE_TRANSCRIPT_LENGTH + 100)),
        )

        assertEquals(MAX_VOICE_TRANSCRIPT_LENGTH, transcript?.length)
    }
}
