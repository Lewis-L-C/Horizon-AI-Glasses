package com.blue.glassesapp.feature.home.ui

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class QRCodeAnalyzer(
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "QRCodeAnalyzer"
    }

    private val scanner = BarcodeScanning.getClient()
    private var isEnabled = false
    private var isProcessing = false
    private var frameCount = 0

    fun enable() {
        isEnabled = true
        isProcessing = false
        frameCount = 0
        Log.e(TAG, "✅ 二维码扫描已开启")
    }

    fun disable() {
        isEnabled = false
        isProcessing = false
        Log.e(TAG, "⏹️ 二维码扫描已关闭")
    }

    fun isEnabled(): Boolean = isEnabled

    override fun analyze(imageProxy: ImageProxy) {
        frameCount++

        // ✅ 每帧都打印，确认是否在运行
        if (frameCount % 10 == 0) {
            Log.e(TAG, "📷 扫描中... 帧数: $frameCount, isEnabled=$isEnabled, isProcessing=$isProcessing")
        }

        if (!isEnabled || isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    Log.e(TAG, "📱 扫描到 ${barcodes.size} 个条码")
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        val format = barcode.format
                        Log.e(TAG, "📱 格式: $format, 内容: $rawValue")

                        if (rawValue != null && rawValue.isNotEmpty()) {
                            isProcessing = true
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onResult(rawValue)
                            }
                            disable()
                            break
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ 扫码失败: ${e.message}")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}