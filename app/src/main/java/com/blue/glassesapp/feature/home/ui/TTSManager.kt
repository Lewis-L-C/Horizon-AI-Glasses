package com.blue.glassesapp.feature.home.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 语音播报管理器
 */
class TTSManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "TTSManager"
    }

    private var tts: TextToSpeech? = null
    private val speakQueue = ConcurrentLinkedQueue<String>()
    private var isSpeaking = false
    private var isInitialized = false

    // ✅ 状态回调
    var onTTSReady: (() -> Unit)? = null
    var onTTSError: ((String) -> Unit)? = null

    // ✅ 播报开始/结束回调（用于暂停/恢复语音识别）
    var onSpeakStart: (() -> Unit)? = null
    var onSpeakDone: (() -> Unit)? = null

    init {
        initTTS()
    }

    private fun initTTS() {
        try {
            Log.e(TAG, "🔄 开始初始化 TTS...")

            tts = TextToSpeech(context) { status ->
                Log.e(TAG, "📤 TTS 初始化回调: status=$status")

                if (status == TextToSpeech.SUCCESS) {
                    // 设置中文
                    val result = tts?.setLanguage(Locale.CHINESE)
                    Log.e(TAG, "📤 设置中文结果: $result")

                    // ✅ 打印可用语言
                    val availableLanguages = tts?.availableLanguages
                    Log.e(TAG, "📤 可用语言: $availableLanguages")

                    // ✅ 打印当前语言
                    val currentLanguage = tts?.language
                    Log.e(TAG, "📤 当前语言: $currentLanguage")

                    // ✅ 打印是否支持中文
                    val isChineseSupported = tts?.isLanguageAvailable(Locale.CHINESE)
                    Log.e(TAG, "📤 中文支持: $isChineseSupported")

                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "⚠️ 中文不支持，使用英文")
                        tts?.setLanguage(Locale.US)
                    }

                    isInitialized = true
                    Log.e(TAG, "✅ TTS 初始化成功！")

                    // ✅ 测试播报
                    testSpeak()

                    // ✅ 回调通知
                    onTTSReady?.invoke()

                    processQueue()
                } else {
                    Log.e(TAG, "❌ TTS 初始化失败, status=$status")
                    isInitialized = false

                    // ✅ 回调通知
                    val errorMsg = when (status) {
                        TextToSpeech.ERROR -> "TTS 引擎错误"
                        TextToSpeech.LANG_MISSING_DATA -> "缺少语言数据"
                        TextToSpeech.LANG_NOT_SUPPORTED -> "语言不支持"
                        else -> "TTS 初始化失败: $status"
                    }
                    onTTSError?.invoke(errorMsg)
                }
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    Log.e(TAG, "🔊 开始播报")
                    // ✅ 通知外部：开始播报
                    onSpeakStart?.invoke()
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    Log.e(TAG, "🔊 播报完成")
                    // ✅ 通知外部：播报完成
                    onSpeakDone?.invoke()
                    processQueue()
                }

                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    Log.e(TAG, "❌ 播报出错")
                    onSpeakDone?.invoke()
                    processQueue()
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "❌ TTS 初始化异常: ${e.message}", e)
            onTTSError?.invoke("TTS 异常: ${e.message}")
        }
    }

    /**
     * ✅ 测试播报
     */
    private fun testSpeak() {
        try {
            Thread.sleep(500) // 等待 TTS 完全就绪
            tts?.speak("语音播报已就绪", TextToSpeech.QUEUE_FLUSH, null, "test")
            Log.e(TAG, "🔊 测试播报: 语音播报已就绪")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 测试播报失败: ${e.message}")
        }
    }

    /**
     * 播报文字（自动加入队列）
     */
    fun speak(text: String) {
        if (text.isBlank()) {
            Log.e(TAG, "⚠️ 文字为空，跳过播报")
            return
        }

        if (!isInitialized) {
            Log.e(TAG, "⚠️ TTS 未初始化，队列等待...")
            val cleanText = cleanText(text)
            if (cleanText.isNotBlank()) {
                speakQueue.offer(cleanText)
            }
            return
        }

        val cleanText = cleanText(text)
        if (cleanText.isBlank()) return

        Log.e(TAG, "🗣️ 加入播报队列: $cleanText")
        speakQueue.offer(cleanText)
        processQueue()
    }

    private fun cleanText(text: String): String {
        return text
            // ✅ 匹配所有 Emoji（Unicode 范围）
            .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]"), "")
            // ✅ 匹配常见符号（❤️⭐等）
            .replace(Regex("[\\u2600-\\u27BF]"), "")
            // ✅ 中英文括号、方括号
            .replace(Regex("[\\[\\]（）]"), "")
            .trim()
    }

    private fun processQueue() {
        if (!isInitialized) {
            Log.e(TAG, "⏳ TTS 未初始化，等待...")
            return
        }

        if (isSpeaking) {
            Log.e(TAG, "⏳ 正在播报，队列等待...")
            return
        }

        val next = speakQueue.poll()
        if (next == null) {
            Log.e(TAG, "📭 队列为空")
            return
        }

        try {
            isSpeaking = true
            Log.e(TAG, "🔊 开始播报: $next")
            // ✅ 触发 onSpeakStart 回调
            onSpeakStart?.invoke()
            tts?.speak(next, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())
        } catch (e: Exception) {
            Log.e(TAG, "❌ 播报失败: ${e.message}")
            isSpeaking = false
            onSpeakDone?.invoke()
            processQueue()
        }
    }
    /**
     * ✅ 获取 TTS 状态
     */
    fun getStatus(): String {
        return when {
            !isInitialized -> "❌ TTS 未初始化"
            isSpeaking -> "🔊 正在播报"
            else -> "✅ TTS 就绪"
        }
    }

    fun stop() {
        Log.e(TAG, "⏹️ 停止播报")
        tts?.stop()
        isSpeaking = false
        speakQueue.clear()
    }

    fun destroy() {
        Log.e(TAG, "🗑️ 销毁 TTS")
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}