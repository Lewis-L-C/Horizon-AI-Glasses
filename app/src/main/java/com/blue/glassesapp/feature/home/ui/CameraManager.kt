package com.blue.glassesapp.feature.home.ui

import androidx.camera.core.ImageProxy
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraManager(
    private val context: Context,
    private val previewView: PreviewView
) {

    companion object {
        private const val TAG = "CameraManager"
    }

    // ✅ UseCase
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var recording: Recording? = null
    private var isRecording = false
    private var isReleased = false
    private var isCameraReady = false
    private var isBound = false

    // ✅ 四个分析器
    private var ocrAnalyzer: Analyzer? = null
    private var qrCodeAnalyzer: Analyzer? = null
    private var trafficLightAnalyzer: Analyzer? = null
    private var blindRoadAnalyzer: Analyzer? = null  // ✅ 新增盲道分析器

    // ✅ 当前激活的分析器
    private var currentAnalyzer: Analyzer? = null

    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val lifecycleOwner = context as LifecycleOwner

    var onPhotoTaken: ((Uri?) -> Unit)? = null
    var onVideoSaved: ((Uri?) -> Unit)? = null

    // ✅ 红绿灯检测结果回调（外部可设置）
    private var onTrafficLightDetected: ((String, Float) -> Unit)? = null

    // ✅ 设置红绿灯检测回调
    fun setOnTrafficLightDetectedListener(listener: (String, Float) -> Unit) {
        this.onTrafficLightDetected = listener
    }

    private fun getExecutor(): ExecutorService {
        if (cameraExecutor == null || cameraExecutor?.isShutdown == true) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
        return cameraExecutor!!
    }

    // ============================================================
    // ✅ 设置分析器
    // ============================================================

    fun setOCRAnalyzer(analyzer: Analyzer) {
        ocrAnalyzer = analyzer
        Log.e(TAG, "✅ OCR 分析器已注册")
    }

    fun setQRCodeAnalyzer(analyzer: Analyzer) {
        qrCodeAnalyzer = analyzer
        Log.e(TAG, "✅ 二维码分析器已注册")
    }

    fun setTrafficLightAnalyzer(analyzer: Analyzer) {
        trafficLightAnalyzer = analyzer
        Log.e(TAG, "✅ 红绿灯分析器已注册")
    }

    // ✅ 新增：设置盲道分析器
    fun setBlindRoadAnalyzer(analyzer: Analyzer) {
        blindRoadAnalyzer = analyzer
        Log.e(TAG, "✅ 盲道分析器已注册")
    }

    // ============================================================
    // ✅ 切换分析器
    // ============================================================

    fun switchToOCR() {
        if (isReleased || cameraProvider == null) {
            Log.e(TAG, "⚠️ CameraManager 已释放或未初始化")
            return
        }

        val analyzer = ocrAnalyzer
        if (analyzer == null) {
            Log.e(TAG, "❌ OCR 分析器未注册")
            return
        }

        try {
            Log.e(TAG, "🔄 切换到 OCR 分析器")

            val newAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            newAnalysis.setAnalyzer(getExecutor(), analyzer)

            imageAnalysis?.let { cameraProvider?.unbind(it) }

            imageAnalysis = newAnalysis
            currentAnalyzer = analyzer

            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                videoCapture,
                newAnalysis
            )

            Log.e(TAG, "✅ OCR 分析器已激活")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换 OCR 失败: ${e.message}")
        }
    }

    fun switchToQRCode() {
        if (isReleased || cameraProvider == null) {
            Log.e(TAG, "⚠️ CameraManager 已释放或未初始化")
            return
        }

        val analyzer = qrCodeAnalyzer
        if (analyzer == null) {
            Log.e(TAG, "❌ 二维码分析器未注册")
            return
        }

        try {
            Log.e(TAG, "🔄 切换到二维码分析器")

            val newAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            newAnalysis.setAnalyzer(getExecutor(), analyzer)

            imageAnalysis?.let { cameraProvider?.unbind(it) }

            imageAnalysis = newAnalysis
            currentAnalyzer = analyzer

            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                videoCapture,
                newAnalysis
            )

            Log.e(TAG, "✅ 二维码分析器已激活")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换二维码失败: ${e.message}")
        }
    }

    fun switchToTrafficLight() {
        if (isReleased || cameraProvider == null) {
            Log.e(TAG, "⚠️ CameraManager 已释放或未初始化")
            return
        }

        val analyzer = trafficLightAnalyzer
        if (analyzer == null) {
            Log.e(TAG, "❌ 红绿灯分析器未注册，请先调用 setTrafficLightAnalyzer()")
            return
        }

        try {
            Log.e(TAG, "🔄 切换到红绿灯分析器")

            val newAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            newAnalysis.setAnalyzer(getExecutor(), analyzer)

            imageAnalysis?.let { cameraProvider?.unbind(it) }

            imageAnalysis = newAnalysis
            currentAnalyzer = analyzer

            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                videoCapture,
                newAnalysis
            )

            Log.e(TAG, "✅ 红绿灯分析器已激活")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换红绿灯失败: ${e.message}")
        }
    }

    // ✅ 新增：切换到盲道分析器
    fun switchToBlindRoad() {
        if (isReleased || cameraProvider == null) {
            Log.e(TAG, "⚠️ CameraManager 已释放或未初始化")
            return
        }

        val analyzer = blindRoadAnalyzer
        if (analyzer == null) {
            Log.e(TAG, "❌ 盲道分析器未注册，请先调用 setBlindRoadAnalyzer()")
            return
        }

        try {
            Log.e(TAG, "🔄 切换到盲道分析器")

            val newAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            newAnalysis.setAnalyzer(getExecutor(), analyzer)

            imageAnalysis?.let { cameraProvider?.unbind(it) }

            imageAnalysis = newAnalysis
            currentAnalyzer = analyzer

            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                videoCapture,
                newAnalysis
            )

            Log.e(TAG, "✅ 盲道分析器已激活")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换盲道失败: ${e.message}")
        }
    }

    fun getCurrentAnalyzer(): Analyzer? = currentAnalyzer

    // ============================================================
    // ✅ 相机初始化
    // ============================================================

    fun setupCamera() {
        if (isReleased) {
            Log.e(TAG, "⚠️ CameraManager 已释放，跳过初始化")
            return
        }

        if (isBound) {
            Log.e(TAG, "⚠️ 相机已绑定，跳过重复初始化")
            return
        }

        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                if (isReleased) {
                    Log.e(TAG, "⚠️ CameraManager 已释放，跳过绑定")
                    return@addListener
                }

                cameraProvider = cameraProviderFuture.get()

                preview = Preview.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .build()
                preview?.setSurfaceProvider(previewView.surfaceProvider)

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetResolution(Size(1280, 720))
                    .build()

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.SD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                val defaultAnalyzer = ocrAnalyzer ?: OCRAnalyzer()
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(getExecutor(), defaultAnalyzer)
                imageAnalysis = analysis
                currentAnalyzer = defaultAnalyzer

                try {
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                        videoCapture,
                        analysis
                    )
                    isBound = true
                    isCameraReady = true
                    Log.e(TAG, "✅ 相机绑定成功，当前分析器: ${currentAnalyzer?.javaClass?.simpleName ?: "null"}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 绑定相机失败: ${e.message}")
                    isCameraReady = false
                    isBound = false
                }

            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e(TAG, "❌ 相机初始化失败: ${e.message}")
            isCameraReady = false
        }
    }

    // ============================================================
    // ✅ 拍照
    // ============================================================

    fun isCameraReady(): Boolean = isCameraReady && imageCapture != null && !isReleased

    fun takePhoto() {
        if (isReleased) {
            Toast.makeText(context, "相机已释放", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isCameraReady || imageCapture == null) {
            Log.e(TAG, "❌ 拍照失败: 相机未就绪")
            Toast.makeText(context, "相机未就绪，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GlassesApp")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (isReleased) return
                    val uri = outputFileResults.savedUri
                    Log.e(TAG, "✅ 照片已保存: $uri")
                    Toast.makeText(context, "📸 照片已保存", Toast.LENGTH_SHORT).show()
                    onPhotoTaken?.invoke(uri)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (isReleased) return
                    Log.e(TAG, "❌ 拍照失败: ${exception.message}")
                    Toast.makeText(context, "拍照失败: ${exception.message}", Toast.LENGTH_SHORT).show()
                    onPhotoTaken?.invoke(null)
                }
            }
        )
    }

    // ============================================================
    // ✅ 录像
    // ============================================================

    fun startRecording() {
        if (isReleased) {
            Toast.makeText(context, "相机已释放", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isCameraReady || videoCapture == null) {
            Toast.makeText(context, "相机未就绪，请重试", Toast.LENGTH_SHORT).show()
            return
        }
        if (isRecording) {
            Toast.makeText(context, "正在录像中...", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileName = "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/GlassesApp")
                }
            }

            val outputOptions = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()

            recording = videoCapture?.output
                ?.prepareRecording(context, outputOptions)
                ?.start(ContextCompat.getMainExecutor(context)) { event ->
                    if (isReleased) return@start
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            Log.e(TAG, "🔴 录像开始")
                            Toast.makeText(context, "🔴 开始录像...", Toast.LENGTH_SHORT).show()
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            val uri = event.outputResults.outputUri
                            if (uri != null) {
                                Log.e(TAG, "✅ 视频已保存: $uri")
                                Toast.makeText(context, "🎬 视频已保存", Toast.LENGTH_SHORT).show()
                            } else {
                                Log.e(TAG, "❌ 视频保存失败: URI 为空")
                                Toast.makeText(context, "视频保存失败", Toast.LENGTH_SHORT).show()
                            }
                            onVideoSaved?.invoke(uri)
                        }
                        else -> {
                            isRecording = false
                            Log.e(TAG, "❌ 录像事件异常: $event")
                            Toast.makeText(context, "录像异常", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 开始录像异常: ${e.message}")
            Toast.makeText(context, "录像异常: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording() {
        if (!isRecording) {
            Toast.makeText(context, "当前没有录像", Toast.LENGTH_SHORT).show()
            return
        }
        recording?.stop()
        recording = null
        isRecording = false
        Toast.makeText(context, "⏹️ 录像已停止", Toast.LENGTH_SHORT).show()
    }

    fun toggleRecording(): Boolean {
        return if (isRecording) {
            stopRecording()
            false
        } else {
            startRecording()
            true
        }
    }

    fun isRecording(): Boolean = isRecording

    // ============================================================
    // ✅ 释放资源
    // ============================================================

    fun release() {
        if (isReleased) return
        Log.e(TAG, "🔴 开始释放 CameraManager")

        try { recording?.stop() } catch (_: Exception) {}
        recording = null
        isRecording = false
        isCameraReady = false
        isBound = false

        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
        } catch (_: Exception) {}

        currentAnalyzer = null
        ocrAnalyzer = null
        qrCodeAnalyzer = null
        trafficLightAnalyzer = null
        blindRoadAnalyzer = null  // ✅ 释放盲道分析器
        imageAnalysis = null
        imageCapture = null
        videoCapture = null
        preview = null

        try {
            cameraExecutor?.let {
                if (!it.isShutdown) {
                    it.shutdown()
                    if (!it.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                        it.shutdownNow()
                    }
                }
            }
        } catch (_: Exception) {}
        cameraExecutor = null

        isReleased = true
        Log.e(TAG, "✅ CameraManager 释放完成")
    }

    fun isReleased(): Boolean = isReleased

    // ✅ 内部 OCR 分析器（用于默认初始化）
    private inner class OCRAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            imageProxy.close()
        }
    }
}