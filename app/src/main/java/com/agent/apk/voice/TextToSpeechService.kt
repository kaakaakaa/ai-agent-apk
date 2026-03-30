// File: app/src/main/java/com/agent/apk/voice/TextToSpeechService.kt
package com.agent.apk.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import java.util.UUID

/**
 * 文本转语音服务：将文本朗读出来
 *
 * 使用 Android 内置的 TextToSpeech，支持离线 TTS 引擎
 */
class TextToSpeechService(private val context: Context) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    /**
     * 初始化 TTS
     */
    fun initialize() {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.CHINESE) ?: TextToSpeech.LANG_MISSING_DATA
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // 中文不支持，回退到默认语言
                textToSpeech?.setLanguage(Locale.getDefault())
            }
            isInitialized = true
        } else {
            isInitialized = false
        }
    }

    /**
     * 释放资源
     */
    fun destroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }

    /**
     * 朗读文本（Fire and forget）
     */
    fun speak(text: String) {
        if (!isInitialized) {
            initialize()
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * 朗读文本（返回 Flow 监听进度）
     */
    fun speakAsFlow(text: String): Flow<SpeechProgress> = callbackFlow {
        if (!isInitialized) {
            trySend(SpeechProgress.Error("TTS not initialized"))
            close()
            return@callbackFlow
        }

        val utteranceId = UUID.randomUUID().toString()

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                trySend(SpeechProgress.Start)
            }

            override fun onDone(utteranceId: String?) {
                trySend(SpeechProgress.Done)
                close()
            }

            override fun onError(utteranceId: String?) {
                trySend(SpeechProgress.Error("TTS error"))
                close()
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                trySend(SpeechProgress.RangeStart(start, end))
            }
        })

        try {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            trySend(SpeechProgress.Error(e.message ?: "Unknown error"))
            close()
        }

        awaitClose {
            textToSpeech?.stop()
        }
    }

    /**
     * 停止朗读
     */
    fun stop() {
        textToSpeech?.stop()
    }

    /**
     * 设置语速 (0.5 - 2.0)
     */
    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
    }

    /**
     * 设置音调 (0.0 - 2.0)
     */
    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
    }

    /**
     * 检查是否可用
     */
    fun isAvailable(): Boolean {
        return isInitialized
    }
}

/**
 * 语音进度事件
 */
sealed class SpeechProgress {
    object Start : SpeechProgress()
    data class RangeStart(val start: Int, val end: Int) : SpeechProgress()
    object Done : SpeechProgress()
    data class Error(val message: String) : SpeechProgress()
}
