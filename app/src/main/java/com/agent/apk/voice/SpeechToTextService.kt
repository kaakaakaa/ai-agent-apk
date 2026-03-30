// File: app/src/main/java/com/agent/apk/voice/SpeechToTextService.kt
package com.agent.apk.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 语音识别服务：将语音转换为文本
 *
 * 使用 Android 内置的 SpeechRecognizer，支持云端识别（Google、百度等）
 */
class SpeechToTextService(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * 初始化语音识别器
     */
    fun initialize() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    }

    /**
     * 释放资源
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    /**
     * 开始监听语音（返回 Flow）
     */
    fun listenAsFlow(): Flow<SpeechRecognitionResult> = callbackFlow {
        val recognizer = speechRecognizer ?: run {
            trySend(SpeechRecognitionResult.Error("SpeechRecognizer not initialized"))
            close()
            return@callbackFlow
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechRecognitionResult.Ready)
            }

            override fun onBeginningOfSpeech() {
                trySend(SpeechRecognitionResult.Beginning)
            }

            override fun onRmsChanged(rmsdB: Float) {
                trySend(SpeechRecognitionResult.VolumeChange(rmsdB))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                trySend(SpeechRecognitionResult.End)
            }

            override fun onError(error: Int) {
                trySend(SpeechRecognitionResult.Error(errorToString(error)))
                close()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(SpeechRecognitionResult.Success(matches[0]))
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(SpeechRecognitionResult.Partial(matches[0]))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        recognizer.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            trySend(SpeechRecognitionResult.Error(e.message ?: "Unknown error"))
            close()
        }

        awaitClose {
            recognizer.cancel()
        }
    }

    /**
     * 开始监听语音（返回 suspend 结果）
     */
    suspend fun listenOnce(): Result<String> = suspendCoroutine { continuation ->
        val recognizer = speechRecognizer ?: run {
            continuation.resume(Result.failure(IllegalStateException("SpeechRecognizer not initialized")))
            return@suspendCoroutine
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                continuation.resume(Result.failure(Exception(errorToString(error))))
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    continuation.resume(Result.success(matches[0]))
                } else {
                    continuation.resume(Result.failure(Exception("No speech recognized")))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        recognizer.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * 检查是否可用
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * 错误码转字符串
     */
    private fun errorToString(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
            SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
            SpeechRecognizer.ERROR_NETWORK -> "网络错误"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
            SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙"
            SpeechRecognizer.ERROR_SERVER -> "服务器错误"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "说话超时"
            else -> "未知错误 ($error)"
        }
    }
}

/**
 * 语音识别结果
 */
sealed class SpeechRecognitionResult {
    object Ready : SpeechRecognitionResult()
    object Beginning : SpeechRecognitionResult()
    data class VolumeChange(val rmsdB: Float) : SpeechRecognitionResult()
    object End : SpeechRecognitionResult()
    data class Success(val text: String) : SpeechRecognitionResult()
    data class Partial(val text: String) : SpeechRecognitionResult()
    data class Error(val message: String) : SpeechRecognitionResult()
}
