// File: app/src/test/java/com/agent/apk/voice/TextToSpeechServiceTest.kt
package com.agent.apk.voice

import org.junit.Test
import org.junit.Assert.*

class TextToSpeechServiceTest {

    @Test
    fun `SpeechProgress sealed class covers all cases`() {
        val start = SpeechProgress.Start
        val done = SpeechProgress.Done
        val rangeStart = SpeechProgress.RangeStart(0, 10)
        val error = SpeechProgress.Error("tts error")

        assertTrue(start is SpeechProgress.Start)
        assertTrue(done is SpeechProgress.Done)
        assertEquals(0, (rangeStart as SpeechProgress.RangeStart).start)
        assertEquals(10, rangeStart.end)
    }
}
