package com.blue.glassesapp.feature.home.ui

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class VoiceVoskService(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartial: (String) -> Unit
) {

    private val TAG = "VoiceVosk"
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var isPaused = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val noiseProcessor = WebRtcNoiseProcessor()

    enum class ModelType {
        CHINESE,
        ENGLISH
    }
    private var currentModelType: ModelType = ModelType.CHINESE

    private var silenceCount = 0
    private var lastSpeechTime = 0L
    private val SILENCE_FRAME_THRESHOLD = 300
    private val SPEECH_TIMEOUT_MS = 4000L

    private val KEYWORDS = listOf("翻译", "翻译一下", "帮我翻译", "翻译这个", "解释一下")

    init {
        noiseProcessor.initialize()
        initModel(ModelType.CHINESE)
    }

    private fun initModel(modelType: ModelType) {
        try {
            val modelName = when (modelType) {
                ModelType.CHINESE -> "model"
                ModelType.ENGLISH -> "model_en"
            }
            val modelDir = File(context.filesDir, modelName)

            if (!modelDir.exists() || !File(modelDir, "am").exists()) {
                Log.d(TAG, "从 assets 复制模型: $modelName")
                modelDir.mkdirs()
                copyAssets(modelName)
            }

            if (!File(modelDir, "am").exists()) {
                Log.e(TAG, "❌ 模型复制失败: $modelName")
                return
            }

            recognizer?.close()
            model?.close()

            Log.d(TAG, "✅ 加载模型: $modelName")
            model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, 16000.0f)

            currentModelType = modelType
            Log.d(TAG, "✅ 模型切换成功: ${if (modelType == ModelType.CHINESE) "中文" else "英文"}")
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败: ${e.message}")
            e.printStackTrace()
        }
    }

    fun switchToEnglish() {
        if (currentModelType == ModelType.ENGLISH) {
            Log.d(TAG, "已经是英文模型")
            return
        }
        Log.d(TAG, "🔄 切换到英文模型...")

        try {
            // ✅ 暂停识别循环
            isPaused = true

            // ✅ 先释放旧的 recognizer
            val oldRecognizer = recognizer
            recognizer = null
            oldRecognizer?.close()

            // ✅ 延迟等待，确保资源完全释放
            Thread.sleep(200)

            // 加载英文模型
            val modelName = "model_en"
            val modelDir = File(context.filesDir, modelName)
            if (!modelDir.exists() || !File(modelDir, "am").exists()) {
                Log.e(TAG, "❌ 英文模型不存在，请先下载")
                isPaused = false
                return
            }

            model?.close()
            model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, 16000.0f)

            currentModelType = ModelType.ENGLISH
            isPaused = false
            silenceCount = 0
            lastSpeechTime = System.currentTimeMillis()
            Log.d(TAG, "✅ 模型切换成功: 英文")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换英文模型失败: ${e.message}")
            isPaused = false
            // ✅ 出错时重置 recognizer
            resetRecognizer()
        }
    }

    fun switchToChinese() {
        if (currentModelType == ModelType.CHINESE) {
            Log.d(TAG, "已经是中文模型")
            return
        }
        Log.d(TAG, "🔄 切换到中文模型...")

        try {
            // ✅ 暂停识别循环
            isPaused = true

            // ✅ 先释放旧的 recognizer
            val oldRecognizer = recognizer
            recognizer = null
            oldRecognizer?.close()

            // ✅ 延迟等待
            Thread.sleep(200)

            // 加载中文模型
            val modelName = "model"
            val modelDir = File(context.filesDir, modelName)
            if (!modelDir.exists() || !File(modelDir, "am").exists()) {
                Log.e(TAG, "❌ 中文模型不存在")
                isPaused = false
                return
            }

            model?.close()
            model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, 16000.0f)

            currentModelType = ModelType.CHINESE
            isPaused = false
            silenceCount = 0
            lastSpeechTime = System.currentTimeMillis()
            Log.d(TAG, "✅ 模型切换成功: 中文")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换中文模型失败: ${e.message}")
            isPaused = false
            resetRecognizer()
        }
    }

    fun getCurrentModelType(): ModelType = currentModelType

    private fun copyAssets(modelName: String) {
        val assetManager = context.assets
        val targetDir = File(context.filesDir, modelName)
        targetDir.mkdirs()

        try {
            val items = assetManager.list(modelName) ?: return
            for (item in items) {
                val assetPath = "$modelName/$item"
                val targetFile = File(targetDir, item)
                copyFileOrFolder(assetPath, targetFile)
            }
            Log.d(TAG, "✅ 模型复制完成: $modelName")
        } catch (e: Exception) {
            Log.e(TAG, "复制失败: ${e.message}")
        }
    }

    private fun copyFileOrFolder(assetPath: String, targetFile: File) {
        val assetManager = context.assets
        try {
            assetManager.open(assetPath).use { input ->
                if (!targetFile.exists()) {
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (input.read(buffer).also { len = it } > 0) {
                            output.write(buffer, 0, len)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            if (!targetFile.exists()) {
                targetFile.mkdirs()
            }
            try {
                val items = assetManager.list(assetPath) ?: return
                for (item in items) {
                    copyFileOrFolder("$assetPath/$item", File(targetFile, item))
                }
            } catch (e2: Exception) {
                Log.e(TAG, "复制文件夹失败: ${e2.message}")
            }
        }
    }

    // ============================================================
    // ✅ 暂停/恢复语音识别
    // ============================================================

    fun pauseListening() {
        if (!isRunning) {
            Log.d(TAG, "⏸️ 语音识别未运行，无需暂停")
            return
        }
        if (isPaused) {
            Log.d(TAG, "⏸️ 语音识别已暂停")
            return
        }
        try {
            audioRecord?.stop()
            isPaused = true
            Log.d(TAG, "⏸️ 语音识别已暂停（TTS 播报中）")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 暂停失败: ${e.message}")
        }
    }

    fun resumeListening() {
        if (!isRunning) {
            Log.d(TAG, "⏸️ 语音识别未运行，无需恢复")
            return
        }
        if (!isPaused) {
            Log.d(TAG, "▶️ 语音识别未暂停，无需恢复")
            return
        }
        try {
            audioRecord?.startRecording()
            isPaused = false
            silenceCount = 0
            lastSpeechTime = System.currentTimeMillis()
            Log.d(TAG, "▶️ 语音识别已恢复")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 恢复失败: ${e.message}")
        }
    }

    fun isPaused(): Boolean = isPaused

    // ============================================================
    // ✅ 核心识别逻辑
    // ============================================================

    fun startListening() {
        if (recognizer == null) {
            Log.e(TAG, "识别器未初始化")
            return
        }
        if (isRunning) return

        val bufferSize = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "缓冲区大小错误")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.CAMCORDER,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord 初始化失败")
            audioRecord?.release()
            audioRecord = null
            return
        }

        audioRecord?.startRecording()
        isRunning = true
        isPaused = false
        silenceCount = 0
        lastSpeechTime = System.currentTimeMillis()
        Log.d(TAG, "🎤 开始监听 (${if (currentModelType == ModelType.CHINESE) "中文" else "英文"})")

        Thread {
            val buffer = ByteArray(bufferSize)
            while (isRunning) {
                // ✅ 如果暂停，跳过处理
                if (isPaused) {
                    Thread.sleep(100)
                    continue
                }

                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0) {
                    try {
                        val rms = calculateRMS(buffer, bytesRead)
                        val isSpeech = rms > 80.0

                        val currentTime = System.currentTimeMillis()
                        if (isSpeech) {
                            silenceCount = 0
                            lastSpeechTime = currentTime
                        } else {
                            silenceCount++
                            if (silenceCount > SILENCE_FRAME_THRESHOLD) {
                                val silenceDuration = currentTime - lastSpeechTime
                                if (silenceDuration > SPEECH_TIMEOUT_MS) {
                                    Log.d(TAG, "🔇 静音超时，重置识别器")
                                    val result = recognizer?.result ?: ""
                                    val text = parseResult(result)
                                    if (text.isNotEmpty()) {
                                        mainHandler.post { processResult(text) }
                                    }
                                    resetRecognizer()
                                    silenceCount = 0
                                    lastSpeechTime = currentTime
                                }
                            }
                        }

                        val processedBuffer = noiseProcessor.processAudio(buffer.copyOf(bytesRead))

                        // ✅ 检查音频数据长度，防止 Vosk 崩溃
                        if (processedBuffer.size < 160) {
                            continue
                        }

                        try {
                            if (recognizer?.acceptWaveForm(processedBuffer, processedBuffer.size) == true) {
                                val result = recognizer?.result ?: ""
                                val text = parseResult(result)
                                if (text.isNotEmpty()) {
                                    Log.d(TAG, "✅ 识别结果: $text")
                                    mainHandler.post { processResult(text) }
                                }
                                silenceCount = 0
                            } else {
                                val partial = recognizer?.partialResult ?: ""
                                val partialText = parsePartial(partial)
                                if (partialText.isNotEmpty()) {
                                    mainHandler.post { onPartial(partialText) }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Vosk 处理异常: ${e.message}")
                            resetRecognizer()
                            silenceCount = 0
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "识别错误: ${e.message}")
                    }
                }
            }
        }.start()
    }

    private fun resetRecognizer() {
        try {
            val currentModel = model
            if (currentModel != null) {
                recognizer?.close()
                recognizer = Recognizer(currentModel, 16000.0f)
                Log.d(TAG, "🔄 Recognizer 已重置")
            }
        } catch (e: Exception) {
            Log.e(TAG, "重置失败: ${e.message}")
        }
    }

    private fun calculateRMS(buffer: ByteArray, bytesRead: Int): Double {
        var sum = 0L
        for (i in 0 until bytesRead step 2) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            sum += (sample * sample).toLong()
        }
        return if (bytesRead / 2 > 0) Math.sqrt((sum / (bytesRead / 2)).toDouble()) else 0.0
    }

    private fun parseResult(json: String): String {
        return try {
            val obj = com.google.gson.JsonParser().parse(json).asJsonObject
            obj.get("text")?.asString ?: ""
        } catch (e: Exception) { "" }
    }

    private fun parsePartial(json: String): String {
        return try {
            val obj = com.google.gson.JsonParser().parse(json).asJsonObject
            obj.get("partial")?.asString ?: ""
        } catch (e: Exception) { "" }
    }

    private fun processResult(text: String) {
        val lowerText = text.lowercase()
        val matched = KEYWORDS.find { lowerText.contains(it) }
        if (matched != null) {
            Log.d(TAG, "✅ 触发关键词: $matched")
        }
        onResult(text)
    }

    fun stopListening() {
        isRunning = false
        isPaused = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "停止监听")
    }

    fun destroy() {
        stopListening()
        recognizer?.close()
        model?.close()
        recognizer = null
        model = null
        noiseProcessor.release()
    }
}