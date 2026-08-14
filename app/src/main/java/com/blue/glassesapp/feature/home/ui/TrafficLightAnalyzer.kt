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
import kotlin.math.max
import kotlin.math.min

class TrafficLightAnalyzer(
    private val context: Context,
    private val onResult: (String, Float, List<Detection>) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "TrafficLight"
        private const val MODEL_PATH = "traffic/best_traffic_med_yolo_v8_float32.tflite"
        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.3f
        private const val NMS_IOU_THRESHOLD = 0.5f
        private const val STABILITY_FRAMES = 3
    }

    private var interpreter: Interpreter? = null
    private val stateBuffer = mutableListOf<String>()
    private var lastAnnouncedState = ""
    private var frameCount = 0

    // ✅ 保存 letterbox 参数，用于坐标反变换
    private var lastLetterboxScale = 1f
    private var lastOffsetX = 0f
    private var lastOffsetY = 0f
    private var lastOriginalWidth = 0
    private var lastOriginalHeight = 0

    init {
        Log.e(TAG, "🔴 TrafficLightAnalyzer 创建")
        loadModel()
    }

    private fun loadModel() {
        try {
            val buffer = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(buffer, options)

            val input = interpreter!!.getInputTensor(0)
            val output = interpreter!!.getOutputTensor(0)

            Log.e(TAG, "📥 input shape=${input.shape().contentToString()}")
            Log.e(TAG, "📥 input type=${input.dataType()}")
            Log.e(TAG, "📤 output shape=${output.shape().contentToString()}")
            Log.e(TAG, "📤 output type=${output.dataType()}")

            val shape = output.shape()
            Log.e(TAG, "📤 output 维度数: ${shape.size}")
            for (i in shape.indices) {
                Log.e(TAG, "📤 output 维度 $i: ${shape[i]}")
            }

            Log.e(TAG, "✅ 模型加载完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌模型加载失败 ${e.message}", e)
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

        if (interpreter == null) {
            imageProxy.close()
            return
        }

        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        // ✅ 保存原始尺寸
        lastOriginalWidth = bitmap.width
        lastOriginalHeight = bitmap.height

        // ✅ Letterbox 处理
        val letterboxResult = letterbox(bitmap, INPUT_SIZE, INPUT_SIZE)
        val inputBitmap = letterboxResult.bitmap
        lastLetterboxScale = letterboxResult.scale
        lastOffsetX = letterboxResult.offsetX
        lastOffsetY = letterboxResult.offsetY

        Log.e(TAG, "📐 letterbox: 原图=${bitmap.width}x${bitmap.height}, " +
                "scale=${letterboxResult.scale}, offsetX=${letterboxResult.offsetX}, offsetY=${letterboxResult.offsetY}")

        val inputBuffer = bitmapToByteBuffer(inputBitmap)

        val outputBuffer = ByteBuffer.allocateDirect(
            8 * 8400 * 4
        ).order(
            ByteOrder.nativeOrder()
        )

        try {
            interpreter!!.run(inputBuffer, outputBuffer)
            if (frameCount % 10 == 0) {
                Log.e(TAG, "✅ 推理完成")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌推理失败 ${e.message}")
            imageProxy.close()
            return
        }

        // ✅ 解析检测框（返回的是原图坐标）
        val detections = parseYOLOOutput(outputBuffer)

        if (detections.isNotEmpty()) {
            Log.e(TAG, "🎯检测数量=${detections.size}")
        }

        var bestState = "unknown"
        var bestConf = 0f
        var bestDetection: Detection? = null

        for (d in detections) {
            if (d.confidence > CONFIDENCE_THRESHOLD && d.confidence > bestConf) {
                bestState = d.label
                bestConf = d.confidence
                bestDetection = d
            }
        }

        if (bestState != "unknown" && bestDetection != null) {
            stateBuffer.add(bestState)
            if (stateBuffer.size > STABILITY_FRAMES) {
                stateBuffer.removeAt(0)
            }
            val stable = stateBuffer.all { it == bestState }
            if (stable && bestState != lastAnnouncedState) {
                lastAnnouncedState = bestState
                Log.e(TAG, "🚦结果=$bestState conf=$bestConf")
                onResult(bestState, bestConf, detections)
            }
        } else {
            stateBuffer.clear()
        }

        // 回收 Bitmap
        inputBitmap.recycle()
        imageProxy.close()
    }

    /**
     * Letterbox 结果
     */
    data class LetterboxResult(
        val bitmap: Bitmap,
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float
    )

    /**
     * ✅ Letterbox 处理：保持比例缩放 + 黑边填充
     * 返回缩放后的 Bitmap 和变换参数
     */
    private fun letterbox(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): LetterboxResult {
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height

        // 计算缩放比例（保持宽高比）
        val scale = min(targetWidth.toFloat() / srcWidth, targetHeight.toFloat() / srcHeight)
        val scaledWidth = (srcWidth * scale).toInt()
        val scaledHeight = (srcHeight * scale).toInt()

        // 缩放图片
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        // 创建目标尺寸的 Bitmap（黑色背景）
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)

        // 计算偏移（居中）
        val offsetX = (targetWidth - scaledWidth) / 2f
        val offsetY = (targetHeight - scaledHeight) / 2f

        // 绘制缩放后的图片（居中）
        canvas.drawBitmap(scaledBitmap, offsetX, offsetY, null)

        // 回收临时 Bitmap
        scaledBitmap.recycle()

        return LetterboxResult(result, scale, offsetX, offsetY)
    }

    /**
     * ✅ 将 letterbox 坐标反变换到原图坐标
     */
    private fun toOriginalCoords(x: Float, y: Float): Pair<Float, Float> {
        // letterbox 坐标 → 缩放后坐标（去掉 padding）
        val scaledX = x - lastOffsetX
        val scaledY = y - lastOffsetY
        // 缩放后坐标 → 原图坐标
        val origX = scaledX / lastLetterboxScale
        val origY = scaledY / lastLetterboxScale
        return Pair(origX, origY)
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val planes = imageProxy.planes
            val y = planes[0].buffer
            val u = planes[1].buffer
            val v = planes[2].buffer

            val nv21 = ByteArray(
                y.remaining() + u.remaining() + v.remaining()
            )

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
            yuv.compressToJpeg(
                android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
                100,
                out
            )
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        } catch (e: Exception) {
            Log.e(TAG, "图片转换失败 ${e.message}")
            null
        }
    }

    /**
     * Bitmap 转 TFLite 输入
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(
            1 * INPUT_SIZE * INPUT_SIZE * 3 * 4
        )
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

    /**
     * YOLOv8 输出解析
     */
    private fun parseYOLOOutput(outputBuffer: ByteBuffer): List<Detection> {
        val detections = mutableListOf<Detection>()
        outputBuffer.rewind()

        try {
            val output = FloatArray(8 * 8400)
            outputBuffer.asFloatBuffer().get(output)

            // 打印前 20 个 anchor
            for (i in 0 until min(20, 8400)) {
                Log.e(
                    TAG,
                    """
                anchor=$i
                x=${output[0 * 8400 + i]}
                y=${output[1 * 8400 + i]}
                w=${output[2 * 8400 + i]}
                h=${output[3 * 8400 + i]}
                red=${output[4 * 8400 + i]}
                green=${output[5 * 8400 + i]}
                yellow=${output[6 * 8400 + i]}
                unknown=${output[7 * 8400 + i]}
                """.trimIndent()
                )
            }

            // 输出范围检查
            var minValue = Float.MAX_VALUE
            var maxValue = Float.MIN_VALUE
            for (v in output) {
                if (v < minValue) minValue = v
                if (v > maxValue) maxValue = v
            }
            Log.e(TAG, "📊 OUTPUT min=$minValue max=$maxValue")

            // 找最大值
            var maxIndex = 0
            var maxVal = 0f
            for (i in output.indices) {
                if (output[i] > maxVal) {
                    maxVal = output[i]
                    maxIndex = i
                }
            }
            val maxChannel = maxIndex / 8400
            val maxAnchor = maxIndex % 8400
            Log.e(TAG, "🏆 max channel=$maxChannel anchor=$maxAnchor value=$maxVal")

            val labels = arrayOf("red", "green", "yellow", "unknown")

            for (i in 0 until 8400) {
                // ✅ letterbox 坐标 (0~640)
                val lbX = output[0 * 8400 + i] * 640f
                val lbY = output[1 * 8400 + i] * 640f
                val lbW = output[2 * 8400 + i] * 640f
                val lbH = output[3 * 8400 + i] * 640f

                // ✅ 反变换到原图坐标
                val (origX, origY) = toOriginalCoords(lbX, lbY)
                val (origW, origH) = toOriginalCoords(lbW, lbH)

                // 类别分数
                var bestScore = 0f
                var bestClass = -1
                for (c in 0 until 4) {
                    val score = output[(4 + c) * 8400 + i]
                    if (score > bestScore) {
                        bestScore = score
                        bestClass = c
                    }
                }

                // 打印前 5 个 anchor 和所有分数 > 0.1 的 anchor
                if (i < 5 || bestScore > 0.1f) {
                    Log.e(
                        TAG,
                        """
                    BOX=$i
                    lb: cx=$lbX cy=$lbY w=$lbW h=$lbH
                    orig: cx=$origX cy=$origY w=$origW h=$origH
                    red=${output[4 * 8400 + i]}
                    green=${output[5 * 8400 + i]}
                    yellow=${output[6 * 8400 + i]}
                    unknown=${output[7 * 8400 + i]}
                    bestClass=$bestClass bestScore=$bestScore
                    """.trimIndent()
                    )
                }

                // ✅ 过滤有效检测（使用原图坐标判断大小）
                if (bestClass >= 0 && bestScore > CONFIDENCE_THRESHOLD && origW > 10f && origH > 10f) {
                    val label = labels[bestClass]
                    if (label != "unknown") {
                        detections.add(
                            Detection(
                                label = label,
                                confidence = bestScore,
                                x = origX,
                                y = origY,
                                width = origW,
                                height = origH,
                                left = origX - origW / 2,
                                top = origY - origH / 2,
                                right = origX + origW / 2,
                                bottom = origY + origH / 2
                            )
                        )
                    }
                }
            }

            Log.e(TAG, "📊 原始检测=${detections.size}")

        } catch (e: Exception) {
            Log.e(TAG, "❌解析失败 ${e.message}", e)
            return emptyList()
        }

        val result = nms(detections, NMS_IOU_THRESHOLD)
        Log.e(TAG, "✅ NMS后=${result.size}")
        return result
    }

    /**
     * NMS 非极大值抑制（按类别分别抑制）
     */
    private fun nms(detections: List<Detection>, threshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }
        val result = mutableListOf<Detection>()
        val removed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (removed[i]) continue
            val current = sorted[i]
            result.add(current)

            for (j in i + 1 until sorted.size) {
                if (removed[j]) continue
                if (current.label == sorted[j].label &&
                    calculateIOU(current, sorted[j]) > threshold) {
                    removed[j] = true
                }
            }
        }

        return result
    }

    /**
     * 计算 IOU
     */
    private fun calculateIOU(a: Detection, b: Detection): Float {
        val ax1 = a.left
        val ay1 = a.top
        val ax2 = a.right
        val ay2 = a.bottom

        val bx1 = b.left
        val by1 = b.top
        val bx2 = b.right
        val by2 = b.bottom

        val x1 = max(ax1, bx1)
        val y1 = max(ay1, by1)
        val x2 = min(ax2, bx2)
        val y2 = min(ay2, by2)

        if (x2 <= x1 || y2 <= y1) return 0f

        val inter = (x2 - x1) * (y2 - y1)
        val areaA = (ax2 - ax1) * (ay2 - ay1)
        val areaB = (bx2 - bx1) * (by2 - by1)

        return inter / (areaA + areaB - inter)
    }

    data class Detection(
        val label: String,
        val confidence: Float,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}