package com.lirui.charchat.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * 语音能力封装（全部走系统能力，不额外申请云端配额）：
 * - STT：Android SpeechRecognizer，把玩家说的话转成文字（需要 RECORD_AUDIO 权限）。
 * - TTS：Android TextToSpeech，把角色的回复读出来（依赖系统已装中文语音引擎）。
 *
 * 生命周期：由界面 remember 持有，并在 DisposableEffect 里 release()。
 */
class SpeechKit(private val context: Context) {

    /** 识别完成：返回识别出的文本（可能为空）。 */
    var onResult: ((String) -> Unit)? = null
    /** 识别/播报错误提示（直接可展示给玩家）。 */
    var onError: ((String) -> Unit)? = null
    /** 识别过程中（isListening 变化）回调，供 UI 展示"正在聆听"。 */
    var onListeningChange: ((Boolean) -> Unit)? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    // ---------- 朗读（TTS） ----------

    fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.CHINESE)
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("legacy")
                    override fun onError(utteranceId: String?) {}
                })
                ttsReady = true
            } else {
                onError?.invoke("系统语音播报不可用（请检查是否已安装中文语音引擎）")
            }
        }
    }

    fun speak(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (tts == null) initTts()
        if (ttsReady) {
            tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "chat-${UUID.randomUUID()}")
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    val isSpeaking: Boolean get() = tts?.isSpeaking ?: false

    // ---------- 听写（STT） ----------

    fun isRecognitionAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isRecognitionAvailable()) {
            onError?.invoke("本机不支持系统语音识别")
            return
        }
        stopListening()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onListeningChange?.invoke(true)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    onListeningChange?.invoke(false)
                }
                override fun onError(error: Int) {
                    onListeningChange?.invoke(false)
                    onError?.invoke("语音识别失败（${describeError(error)}）")
                }
                override fun onResults(results: Bundle?) {
                    onListeningChange?.invoke(false)
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onResult?.invoke(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onResult?.invoke(text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure { onError?.invoke("无法启动语音识别：${it.message}") }
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        onListeningChange?.invoke(false)
    }

    fun release() {
        stopListening()
        stopSpeaking()
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
    }

    private fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "录音故障"
        SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有录音权限"
        SpeechRecognizer.ERROR_NETWORK -> "网络不可用"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
        SpeechRecognizer.ERROR_NO_MATCH -> "没听清，再说一次"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙"
        SpeechRecognizer.ERROR_SERVER -> "识别服务异常"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到声音"
        else -> "错误码 $code"
    }
}
