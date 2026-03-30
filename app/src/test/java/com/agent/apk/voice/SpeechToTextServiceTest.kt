// File: app/src/test/java/com/agent/apk/voice/SpeechToTextServiceTest.kt
package com.agent.apk.voice

import org.junit.Test
import org.junit.Assert.*

class SpeechToTextServiceTest {

    @Test
    fun `SpeechRecognitionResult sealed class covers all cases`() {
        val ready = SpeechRecognitionResult.Ready
        val beginning = SpeechRecognitionResult.Beginning
        val end = SpeechRecognitionResult.End
        val volumeChange = SpeechRecognitionResult.VolumeChange(10.5f)
        val success = SpeechRecognitionResult.Success("hello")
        val partial = SpeechRecognitionResult.Partial("hel")
        val error = SpeechRecognitionResult.Error("test error")

        assertTrue(ready is SpeechRecognitionResult.Ready)
        assertTrue(success is SpeechRecognitionResult.Success)
        assertEquals("hello", (success as SpeechRecognitionResult.Success).text)
    }
}
