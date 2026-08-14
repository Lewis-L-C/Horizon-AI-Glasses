package com.blue.glassesapp.feature.home.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class BlindRoadAnalyzer(
    private val context: Context,
    private val onResult: (String, Float) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "BlindRoad"
        private const val MODEL_PATH = "blindpath/best_int8.tflite"
        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.01f
        private const val STABILITY_FRAMES = 3
        private const val DEVIATION_THRESHOLD = 0.15f
        private const val BLIND_PATH_CLASS_ID = 0

        // ✅ [1, 6, 8400] 格式
        private const val NUM_CHANNELS = 6
        private const val NUM_ANCHORS = 8400
    }

    private var interpreter: Interpreter? = null
    private val stateBuffer = mutableListOf<String>()
    private var lastAnnouncedState = ""
    private var frameCount = 0

    // ✅ 量化参数（如果是 int8 模型）
    private var outputScale = 1f
    private var outputZeroPoint = 0
    private var isInt8Model = false

    init {
        Log.e(TAG, "🟡🟡🟡 BlindRoadAnalyzer 构造函数被调用！")
        loadModel()
    }

    private fun loadModel() {
        try {
            Log.e(TAG, "🔄 开始加载盲道模型...")
            val buffer = loadModelFile()
            Log.e(TAG, "✅ 模型文件加载成功，大小: ${buffer.capacity()} bytes")

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(buffer, options)

            val input = interpreter!!.getInputTensor(0)
            val output = interpreter!!.getOutputTensor(0)

            Log.e(TAG, "📥 input shape=${input.shape().contentToString()}")
            Log.e(TAG, "📤 output shape=${output.shape().contentToString()}")
            Log.e(TAG, "📥 input type=${input.dataType()}")
            Log.e(TAG, "📤 output type=${output.dataType()}")

            // ✅ 检查量化参数
            val quantParams = output.quantizationParams()
            outputScale = quantParams.scale
            outputZeroPoint = quantParams.zeroPoint

            // ✅ 检查数据类型（用字符串比较）
            val dataTypeName = output.dataType().toString().lowercase()
            isInt8Model = dataTypeName.contains("uint8") || dataTypeName.contains("int8")
            Log.e(TAG, "📤 output scale=$outputScale, zeroPoint=$outputZeroPoint, dataType=$dataTypeName")

            Log.e(TAG, "✅✅✅ 盲道模型加载完成！")
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ 加载盲道模型失败: ${e.message}", e)
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val fd = context.assets.openFd(MODEL_PATH)
        val input = FileInputStream(fd.fileDescriptor)
        return input.channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    override fun analyze(imageProxy: ImageProxy) {
        frameCount++
        if (frameCount % 10 == 0) {
            Log.e(TAG, "📷 analyze 被调用，帧数: $frameCount")
        }

        if (interpreter == null) {
            Log.e(TAG, "❌ Interpreter 为 null")
            imageProxy.close()
            return
        }

        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap == null) {
            if (frameCount % 10 == 0) {
                Log.e(TAG, "❌ bitmap 转换失败")
            }
            imageProxy.close()
            return
        }

        if (frameCount % 10 == 0) {
            Log.e(TAG, "✅ bitmap 转换成功，尺寸: ${bitmap.width}x${bitmap.height}")
        }

        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = bitmapToByteBuffer(resized)

        // ✅ 输出缓冲区: [1, 6, 8400]
        val outputBuffer = ByteBuffer.allocateDirect(NUM_CHANNELS * NUM_ANCHORS * 4)
            .order(ByteOrder.nativeOrder())

        try {
            val startTime = System.currentTimeMillis()
            interpreter!!.run(inputBuffer, outputBuffer)
            val elapsed = System.currentTimeMillis() - startTime
            if (frameCount % 10 == 0) {
                Log.e(TAG, "✅ 推理完成，耗时: ${elapsed}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 盲道推理失败: ${e.message}", e)
            imageProxy.close()
            return
        }

        val result = parseOutputAndCalculateDeviation(outputBuffer)

        if (result != null) {
            val (deviation, confidence) = result
            val text = generateGuidanceText(deviation)

            Log.e(TAG, "🟡🟡🟡 盲道检测成功: 偏离=${String.format("%.3f", deviation)}, 置信度=${String.format("%.3f", confidence)}, 文字=$text")

            stateBuffer.add(text)
            if (stateBuffer.size > STABILITY_FRAMES) {
                stateBuffer.removeAt(0)
            }
            val stableText = stateBuffer.groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key

            if (stableText != null && stableText != lastAnnouncedState) {
                lastAnnouncedState = stableText
                onResult(stableText, deviation)
            }
        } else {
            if (frameCount % 30 == 0) {
                Log.e(TAG, "⚠️ 未检测到盲道")
            }
            stateBuffer.clear()
            lastAnnouncedState = ""
        }

        imageProxy.close()
    }

    /**
     * 解析 YOLO 输出 [1, 6, 8400]
     * 格式: [x, y, w, h, confidence, class_id]
     */
    private fun parseOutputAndCalculateDeviation(outputBuffer: ByteBuffer): Pair<Float, Float>? {
        try {
            outputBuffer.rewind()

            val output = FloatArray(NUM_CHANNELS * NUM_ANCHORS)
            outputBuffer.asFloatBuffer().get(output)

            // ✅ 每帧都打印前 20 个值，看看输出是否正常
            val sb = StringBuilder("📊 输出前20个值: ")
            for (i in 0 until minOf(20, output.size)) {
                sb.append("${String.format("%.4f", output[i])}, ")
            }
            Log.e(TAG, sb.toString())

            // ✅ 打印最大值和最小值
            var maxVal = -9999f
            var minVal = 9999f
            for (v in output) {
                if (v > maxVal) maxVal = v
                if (v < minVal) minVal = v
            }
            Log.e(TAG, "📊 输出范围: min=$minVal, max=$maxVal")

            var bestConfidence = 0f
            var bestBoxCenterX = 0.5f
            var bestBoxX1 = 0f
            var bestBoxY1 = 0f
            var bestBoxX2 = 0f
            var bestBoxY2 = 0f
            var bestClassId = -1
            var detectionsCount = 0

            for (i in 0 until NUM_ANCHORS) {
                val x = output[0 * NUM_ANCHORS + i]
                val y = output[1 * NUM_ANCHORS + i]
                val w = output[2 * NUM_ANCHORS + i]
                val h = output[3 * NUM_ANCHORS + i]
                val confidence = output[4 * NUM_ANCHORS + i]
                val classId = output[5 * NUM_ANCHORS + i].toInt()

                if (confidence < 0.01f) continue

                detectionsCount++

                val x1 = (x - w / 2) * INPUT_SIZE
                val y1 = (y - h / 2) * INPUT_SIZE
                val x2 = (x + w / 2) * INPUT_SIZE
                val y2 = (y + h / 2) * INPUT_SIZE

                if (detectionsCount <= 5) {
                    Log.e(TAG, "🔍 检测框 #$detectionsCount: x=$x y=$y w=$w h=$h conf=${String.format("%.3f", confidence)} class=$classId")
                }

                if (confidence < CONFIDENCE_THRESHOLD) continue
                if (x1 < 0 || y1 < 0 || x2 > INPUT_SIZE || y2 > INPUT_SIZE) continue
                if (x1 >= x2 || y1 >= y2) continue
                if (classId != BLIND_PATH_CLASS_ID) continue

                val centerX = (x1 + x2) / 2 / INPUT_SIZE

                if (confidence > bestConfidence) {
                    bestConfidence = confidence
                    bestBoxCenterX = centerX
                    bestBoxX1 = x1
                    bestBoxY1 = y1
                    bestBoxX2 = x2
                    bestBoxY2 = y2
                    bestClassId = classId
                }
            }

            Log.e(TAG, "📊 总共 ${detectionsCount} 个检测框（含低置信度）")

            if (bestConfidence > CONFIDENCE_THRESHOLD) {
                val deviation = (bestBoxCenterX - 0.5f) * 2f
                Log.e(TAG, "🏆 最佳检测: class=$bestClassId, conf=${String.format("%.3f", bestConfidence)}")
                Log.e(TAG, "📐 盲道中心: ${String.format("%.3f", bestBoxCenterX)}, 偏离: ${String.format("%.3f", deviation)}")
                return Pair(deviation, bestConfidence)
            }

            return null
        } catch (e: Exception) {
            Log.e(TAG, "❌ 解析输出失败: ${e.message}", e)
            return null
        }
    }

    private fun generateGuidanceText(deviation: Float): String {
        val absDev = kotlin.math.abs(deviation)

        return when {
            absDev < DEVIATION_THRESHOLD -> "在盲道上"
            deviation < 0 -> "检测到盲道，在左手 ${String.format("%.1f", absDev * 2)} 米处"
            else -> "检测到盲道，在右手 ${String.format("%.1f", absDev * 2)} 米处"
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val planes = imageProxy.planes
            val y = planes[0].buffer
            val u = planes[1].buffer
            val v = planes[2].buffer

            val nv21 = ByteArray(y.remaining() + u.remaining() + v.remaining())
            y.get(nv21, 0, y.remaining())
            u.get(nv21, y.position(), u.remaining())
            v.get(nv21, y.position() + u.position(), v.remaining())

            val yuv = android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )
            val out = java.io.ByteArrayOutputStream()
            yuv.compressToJpeg(android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        } catch (e: Exception) {
            null
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            buffer.putFloat((pixel and 0xFF) / 255.0f)
        }
        buffer.rewind()
        return buffer
    }

    fun destroy() {
        Log.e(TAG, "🗑️ 销毁 BlindRoadAnalyzer")
        interpreter?.close()
        interpreter = null
    }
}