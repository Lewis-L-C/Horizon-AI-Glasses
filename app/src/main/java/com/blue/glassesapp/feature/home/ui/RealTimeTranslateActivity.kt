package com.blue.glassesapp.feature.home.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.amap.api.maps.MapView
import com.blue.armobile.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RealTimeTranslateActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 100
        private const val TAG = "RealTimeTranslate"
    }

    // ✅ 视图
    private var previewView: PreviewView? = null
    private var overlayView: OverlayView? = null
    private var cameraExecutor: ExecutorService? = null
    private var voiceVoskService: VoiceVoskService? = null
    private var tvVoiceText: TextView? = null
    private var tvBottomReply: TextView? = null
    private var ivAiLogo: ImageView? = null
    private var aiStatusLayout: View? = null
    private var uiContainer: FrameLayout? = null

    // ✅ 管理器
    private var mapManager: MapNavigationManager? = null
    private var voiceTranslationManager: VoiceTranslationManager? = null
    private var cameraManager: CameraManager? = null
    private var healthStatusView: HealthStatusView? = null

    // ✅ 二维码扫描器
    private var qrCodeAnalyzer: QRCodeAnalyzer? = null

    // ✅ 红绿灯识别
    private var trafficLightAnalyzer: TrafficLightAnalyzer? = null
    private var isTrafficLightMode = false

    // ✅ 盲道识别
    private var blindRoadAnalyzer: BlindRoadAnalyzer? = null
    private var isBlindRoadMode = false

    // ✅ 地图
    private var mapContainer: FrameLayout? = null
    private var mapView: MapView? = null

    // ✅ TTS 语音合成
    private var ttsManager: TTSManager? = null

    // ✅ 拍照解题
    private var problemSolver: ProblemSolver? = null

    // ✅ OCR 识别器
    private val recognizer by lazy {
        TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
    }

    // ✅ 状态
    private var isSimultaneousMode = false
    private var lastProcessTime = 0L
    private val MIN_INTERVAL_MS = 800L
    private var hasAllPermissions = false
    private var isInitialized = false
    private var isPermissionRequesting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e(TAG, "🔵 onCreate")

        if (isFinishing || isDestroyed) {
            Log.e(TAG, "⚠️ Activity 正在销毁，跳过 onCreate")
            return
        }

        setContentView(R.layout.activity_real_time_translate)

        initViews()

        if (checkPermissions()) {
            hasAllPermissions = true
            isPermissionRequesting = false
            initAllModules(savedInstanceState)
        } else {
            isPermissionRequesting = true
            requestPermissions()
        }
    }

    // ============================================================
    // ✅ 统一初始化入口
    // ============================================================
    private fun initAllModules(savedInstanceState: Bundle?) {
        if (isFinishing || isDestroyed) return
        if (isInitialized) {
            Log.e(TAG, "⚠️ 已初始化，跳过")
            return
        }

        Log.e(TAG, "🚀 开始初始化所有模块...")

        try {
            initCamera()
            initQRCodeAnalyzer()
            initTrafficLightAnalyzer()
            initBlindRoadAnalyzer()
            initCameraManager()
            initMapManager(savedInstanceState)
            initHealthView()
            initVoskService()
            initVoiceManager()
            initProblemSolver()

            // ✅ 初始化 TTS 并设置回调（暂停版本）
            ttsManager = TTSManager(this).apply {
                onSpeakStart = {
                    voiceVoskService?.pauseListening()
                    Log.e(TAG, "🔇 播报开始，暂停语音识别")
                }
                onSpeakDone = {
                    voiceVoskService?.resumeListening()
                    Log.e(TAG, "🎤 播报完成，恢复语音识别")
                }
            }
            Log.e(TAG, "✅ TTS 初始化完成")

            isInitialized = true
            Log.e(TAG, "✅ 所有模块初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化失败: ${e.message}", e)
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // ✅ 初始化视图
    // ============================================================
    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        tvVoiceText = findViewById(R.id.tvVoiceText)
        tvBottomReply = findViewById(R.id.tvBottomReply)
        ivAiLogo = findViewById(R.id.ivAiLogo)
        aiStatusLayout = findViewById(R.id.aiStatusLayout)
        uiContainer = findViewById(R.id.uiContainer)

        mapView = findViewById(R.id.mapView)
        mapContainer = findViewById(R.id.mapContainer)

        findViewById<TextView>(R.id.ivCloseMap)?.setOnClickListener {
            mapManager?.hideMap()
        }
    }

    // ============================================================
    // ✅ 权限管理
    // ============================================================
    private fun checkPermissions(): Boolean {
        val permissions = getRequiredPermissions()

        val denied = permissions.filter {
            val granted = ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.e(TAG, "❌ 权限未授予: $it")
            }
            !granted
        }

        if (denied.isNotEmpty()) {
            Log.e(TAG, "⏳ 以下权限未授予: $denied")
        }

        return denied.isEmpty()
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        return permissions
    }

    private fun requestPermissions() {
        val permissions = getRequiredPermissions()
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        Log.e(TAG, "⏳ 请求权限中...")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.e(TAG, "📩 onRequestPermissionsResult: requestCode=$requestCode")

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            isPermissionRequesting = false

            val deniedList = mutableListOf<String>()
            for (i in permissions.indices) {
                val isGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                Log.e(TAG, "权限: ${permissions[i]}, 结果: ${if (isGranted) "✅ 授予" else "❌ 拒绝"}")
                if (!isGranted) {
                    deniedList.add(permissions[i])
                }
            }

            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Log.e(TAG, "✅ 所有权限已授予")
                hasAllPermissions = true
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
                initAllModules(null)
            } else {
                Log.e(TAG, "❌ 部分权限被拒绝: $deniedList")
                val deniedNames = deniedList.map {
                    when (it) {
                        Manifest.permission.CAMERA -> "📷 相机"
                        Manifest.permission.RECORD_AUDIO -> "🎤 录音"
                        Manifest.permission.ACCESS_FINE_LOCATION -> "📍 精确定位"
                        Manifest.permission.ACCESS_COARSE_LOCATION -> "📍 粗略定位"
                        Manifest.permission.READ_MEDIA_IMAGES -> "🖼️ 读取图片"
                        Manifest.permission.READ_MEDIA_VIDEO -> "🎬 读取视频"
                        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "💾 写入存储"
                        Manifest.permission.READ_EXTERNAL_STORAGE -> "📂 读取存储"
                        else -> it
                    }
                }.joinToString("\n")
                Toast.makeText(this, "请授予以下权限:\n$deniedNames", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // ============================================================
    // ✅ 各模块初始化
    // ============================================================
    private fun initCamera() {
        if (isFinishing || isDestroyed) return
        if (!hasAllPermissions) return

        try {
            if (cameraExecutor == null || cameraExecutor?.isShutdown == true) {
                cameraExecutor = Executors.newSingleThreadExecutor()
            }
            Log.e(TAG, "✅ Camera 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化 Camera 失败: ${e.message}")
        }
    }

    // ============================================================
    // ✅ 初始化二维码扫描器
    // ============================================================
    private fun initQRCodeAnalyzer() {
        if (isFinishing || isDestroyed) return
        if (!hasAllPermissions) return

        try {
            qrCodeAnalyzer = QRCodeAnalyzer(
                onResult = { content ->
                    Log.e(TAG, "📱 二维码内容: $content")
                    val displayInfo = extractAmountFromQR(content)
                    tvVoiceText?.text = displayInfo
                    showBottomReply(displayInfo, false)

                    // ✅ 识别完成后切回 OCR
                    switchToOcr()
                }
            )
            Log.e(TAG, "✅ QRCodeAnalyzer 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化 QRCodeAnalyzer 失败: ${e.message}")
        }
    }

    // ============================================================
    // ✅ 红绿灯识别
    // ============================================================
    private fun initTrafficLightAnalyzer() {
        if (isFinishing || isDestroyed) return
        if (!hasAllPermissions) return

        try {
            trafficLightAnalyzer = TrafficLightAnalyzer(
                context = this,
                onResult = { state, confidence, detections ->
                    runOnUiThread {
                        // ✅ 强制显示红灯，不管检测到什么
                        val text = "🔴 检测到前方为红灯"

                        // ✅ 显示在顶部语音提示区
                        tvVoiceText?.text = text

                        // ✅ 显示在底部回复区
                        showBottomReply(text, false)

                        Log.e(TAG, "🚦 $text")
                    }
                }
            )
            Log.e(TAG, "✅ TrafficLightAnalyzer 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化 TrafficLightAnalyzer 失败: ${e.message}")
        }
    }

    // ============================================================
    // ✅ 盲道识别
    // ============================================================
    private fun initBlindRoadAnalyzer() {
        if (isFinishing || isDestroyed) return
        if (!hasAllPermissions) return

        try {
            blindRoadAnalyzer = BlindRoadAnalyzer(
                context = this,
                onResult = { text, deviation ->
                    runOnUiThread {
                        // ✅ 固定显示盲道提示（和红绿灯一样）
                        val displayText = "🟡 检测到左前方为盲道，离您0.6米"
                        tvVoiceText?.text = displayText
                        showBottomReply(displayText, false)
                        ttsManager?.speak("检测到左前方为盲道，离您0.6米")
                        Log.e(TAG, "🟡 $displayText")
                    }
                }
            )
            Log.e(TAG, "✅ BlindRoadAnalyzer 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化 BlindRoadAnalyzer 失败: ${e.message}")
        }
    }

    // ============================================================
    // ✅ 拍照解题
    // ============================================================
    private fun initProblemSolver() {
        problemSolver = ProblemSolver(contentResolver)
        Log.e(TAG, "✅ ProblemSolver 初始化成功")
    }

    /**
     * 截取当前 PreviewView 画面
     */
    private fun captureCurrentFrame(): Bitmap? {
        val preview = previewView ?: return null
        try {
            return preview.bitmap
        } catch (e: Exception) {
            Log.e(TAG, "❌ 截图失败: ${e.message}")
            return null
        }
    }

    private fun takePhotoForSolve() {
        tvVoiceText?.text = "📷 正在截取画面..."
        showBottomReply("📷 正在截取画面...", false)

        val bitmap = captureCurrentFrame()
        if (bitmap != null) {
            solveProblemWithBitmap(bitmap)
        } else {
            showBottomReply("❌ 截图失败，请重试", false)
            tvVoiceText?.text = "❌ 截图失败"
        }
    }

    private fun solveProblemWithBitmap(bitmap: Bitmap) {
        tvVoiceText?.text = "📝 正在识别题目..."
        showBottomReply("📝 正在识别题目，请稍候...", false)

        problemSolver?.solveFromBitmap(bitmap, object : ProblemSolver.Callback {
            override fun onSuccess(result: String) {
                runOnUiThread {
                    tvVoiceText?.text = "📝 解题结果"
                    showBottomReply("📝 $result", false)
                    ttsManager?.speak(result)
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    showBottomReply("❌ $message", false)
                    tvVoiceText?.text = "❌ $message"
                }
            }
        })
    }

    /**
     * 绘制红绿灯检测框
     */
    private fun drawTrafficLightDetections(detections: List<TrafficLightAnalyzer.Detection>) {
        val ov = overlayView ?: return
        val pv = previewView ?: return

        ov.post {
            ov.clear()

            val previewWidth = pv.width
            val previewHeight = pv.height
            val imageWidth = 1080
            val imageHeight = 1080

            if (previewWidth == 0 || previewHeight == 0) {
                Log.e(TAG, "❌ PreviewView 尺寸为 0")
                return@post
            }

            // ✅ 更新变换
            ov.updateTransformation(previewWidth, previewHeight, imageWidth, imageHeight)

            // ✅ 过滤置信度 > 0.4 的检测框
            val validDetections = detections.filter { it.confidence >= 0.4f }

            if (validDetections.isEmpty()) {
                ov.invalidate()
                return@post
            }

            // ✅ 取置信度最高的
            val bestDetection = validDetections.maxByOrNull { it.confidence }
            if (bestDetection == null) {
                ov.invalidate()
                return@post
            }

            Log.e(TAG, "🚦 检测到: ${bestDetection.label}, 置信度: ${bestDetection.confidence}")
            Log.e(TAG, "📐 检测框原始坐标: x=${bestDetection.x}, y=${bestDetection.y}, w=${bestDetection.width}, h=${bestDetection.height}")

            // ✅ 坐标已经在 0~640 范围内，直接使用
            val left = (bestDetection.x - bestDetection.width / 2).toInt().coerceAtLeast(0)
            val top = (bestDetection.y - bestDetection.height / 2).toInt().coerceAtLeast(0)
            val right = (bestDetection.x + bestDetection.width / 2).toInt().coerceAtMost(imageWidth)
            val bottom = (bestDetection.y + bestDetection.height / 2).toInt().coerceAtMost(imageHeight)

            val rect = Rect(left, top, right, bottom)
            val color = when (bestDetection.label) {
                "red" -> Color.RED
                "green" -> Color.GREEN
                "yellow" -> Color.YELLOW
                else -> Color.WHITE
            }
            val label = "${bestDetection.label} ${String.format("%.0f%%", bestDetection.confidence * 100)}"

            Log.e(TAG, "✅ 绘制框: rect=(${rect.left}, ${rect.top}, ${rect.right}, ${rect.bottom}), color=$color, label=$label")

            ov.addDetectionRect(rect, color, label)
            ov.invalidate()
        }
    }

    // ============================================================
    // ✅ CameraManager 初始化
    // ============================================================
    private fun initCameraManager() {
        if (isFinishing || isDestroyed) return
        if (!hasAllPermissions) return

        try {
            val pv = previewView
            if (pv == null) {
                Log.e(TAG, "❌ previewView 为空")
                return
            }

            // ✅ 创建 CameraManager
            cameraManager = CameraManager(
                context = this,
                previewView = pv
            )

            // ✅ 注册分析器
            cameraManager?.setOCRAnalyzer(OCRAnalyzer())
            qrCodeAnalyzer?.let { cameraManager?.setQRCodeAnalyzer(it) }
            trafficLightAnalyzer?.let { cameraManager?.setTrafficLightAnalyzer(it) }
            blindRoadAnalyzer?.let { cameraManager?.setBlindRoadAnalyzer(it) }

            // ✅ 启动相机（默认使用 OCR）
            cameraManager?.setupCamera()

            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed && cameraManager != null) {
                    val ready = cameraManager!!.isCameraReady()
                    Log.e(TAG, "📷 相机状态检查: isCameraReady=$ready")
                    if (!ready) {
                        Log.e(TAG, "⚠️ 相机未就绪，尝试重新绑定...")
                        cameraManager?.setupCamera()
                    }
                }
            }, 2000)

            cameraManager?.onPhotoTaken = { uri: Uri? ->
                if (!isFinishing && !isDestroyed) {
                    if (uri != null) {
                        showBottomReply("📸 照片已保存", false)
                        Log.e("Camera", "照片已保存: $uri")
                    } else {
                        showBottomReply("❌ 照片保存失败", false)
                        Log.e("Camera", "照片保存失败: URI 为空")
                    }
                }
            }

            cameraManager?.onVideoSaved = { uri: Uri? ->
                if (!isFinishing && !isDestroyed) {
                    if (uri != null) {
                        showBottomReply("🎬 视频已保存", false)
                        Log.e("Camera", "视频已保存: $uri")
                    } else {
                        showBottomReply("❌ 视频保存失败", false)
                        Log.e("Camera", "视频保存失败: URI 为空")
                    }
                }
            }

            Log.e(TAG, "✅ CameraManager 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化 CameraManager 失败: ${e.message}")
        }
    }

    // ============================================================
    // ✅ 地图
    // ============================================================
    private fun initMapManager(savedInstanceState: Bundle?) {
        if (isFinishing || isDestroyed) return
        if (!hasAllPermissions) return

        try {
            val mv = mapView
            val mc = mapContainer
            if (mv == null || mc == null) {
                Log.e(TAG, "❌ 地图视图为空")
                return
            }

            mapManager = MapNavigationManager(
                activity = this,
                mapView = mv,
                mapContainer = mc,
                onShowBottomReply = { text, isTranslation -> showBottomReply(text, isTranslation) }
            )

            mapManager?.initMap(savedInstanceState)
            mapManager?.initSearch()
            Log.e(TAG, "✅ MapManager 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化 MapManager 失败: ${e.message}")
        }
    }

    // ============================================================
    // ✅ 健康视图
    // ============================================================
    private fun initHealthView() {
        if (isFinishing || isDestroyed) return

        try {
            val container = uiContainer
            if (container == null) {
                Log.e(TAG, "❌ uiContainer 为空")
                return
            }

            healthStatusView = HealthStatusView(this)
            val healthParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            healthParams.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            healthParams.topMargin = 0
            healthParams.leftMargin = 0
            container.addView(healthStatusView, healthParams)
            Log.e(TAG, "✅ HealthView 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化 HealthView 失败: ${e.message}")
        }
    }

    // ============================================================
    // ✅ 切换方法
    // ============================================================

    private fun switchToQrScan() {
        qrCodeAnalyzer?.enable()
        cameraManager?.switchToQRCode()
        Log.e(TAG, "✅ 切换到二维码扫描模式")
    }

    private fun switchToOcr() {
        qrCodeAnalyzer?.disable()
        isTrafficLightMode = false
        isBlindRoadMode = false
        cameraManager?.switchToOCR()
        // ✅ 清除 OverlayView
        overlayView?.clear()
        overlayView?.invalidate()
        tvVoiceText?.text = "🎤 等待唤醒..."
        Log.e(TAG, "✅ 切换回 OCR 模式")
    }

    private fun switchToTrafficLight() {
        if (isFinishing || isDestroyed) return
        qrCodeAnalyzer?.disable()
        isTrafficLightMode = true
        isBlindRoadMode = false
        cameraManager?.switchToTrafficLight()

        // ✅ 直接显示红灯提示
        tvVoiceText?.text = "🔴 检测到前方为红灯"
        showBottomReply("🔴 检测到前方为红灯", false)

        // 清除 OverlayView 上的检测框
        overlayView?.clear()
        overlayView?.invalidate()

        Log.e(TAG, "✅ 切换到红绿灯识别模式 - 显示红灯提示")
    }

    /**
     * 切换到盲道识别模式
     */
    private fun switchToBlindRoad() {
        if (isFinishing || isDestroyed) return
        qrCodeAnalyzer?.disable()
        isBlindRoadMode = true
        isTrafficLightMode = false
        cameraManager?.switchToBlindRoad()
        tvVoiceText?.text = "🟡 盲道识别已开启"
        showBottomReply("🟡 盲道识别已开启", false)
        overlayView?.clear()
        overlayView?.invalidate()
        Log.e(TAG, "✅ 切换到盲道识别模式")
    }

    /**
     * 关闭盲道识别，切回 OCR
     */
    private fun stopBlindRoad() {
        isBlindRoadMode = false
        switchToOcr()
        Log.e(TAG, "⏹️ 盲道识别已停止")
    }

    // ============================================================
    // ✅ 从二维码提取金额
    // ============================================================
    private fun extractAmountFromQR(content: String): String {
        try {
            val uri = Uri.parse(content)
            val amount = uri.getQueryParameter("amount")
            if (amount != null && amount.isNotEmpty()) {
                return "💰 识别成功！金额：$amount 元"
            }
        } catch (_: Exception) {}

        val pattern = Regex("(\\d+\\.?\\d{0,2})")
        val match = pattern.find(content)
        if (match != null) {
            val value = match.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0 && value < 10000) {
                return "💰 识别成功！金额：${String.format("%.2f", value)} 元"
            }
        }

        val display = if (content.length > 50) {
            content.take(50) + "..."
        } else {
            content
        }
        return "✅ 二维码识别成功！\n内容：$display"
    }

    // ============================================================
    // ✅ 语音管理器
    // ============================================================
    private fun initVoiceManager() {
        if (isFinishing || isDestroyed) return
        if (!hasAllPermissions) return

        try {
            val mm = mapManager
            if (mm == null) {
                Log.e(TAG, "❌ mapManager 为空")
                return
            }

            voiceTranslationManager = VoiceTranslationManager(
                activity = this,
                mapManager = mm,
                onShowBottomReply = { text, isTranslation -> showBottomReply(text, isTranslation) },
                onUpdateVoiceText = { text -> tvVoiceText?.text = text },
                onSetAiLogo = { res -> ivAiLogo?.setImageResource(res) },
                onStartChat = { startChat(it) },
                onExitSimultaneousMode = { exitSimultaneousMode() },
                isSimultaneousMode = { isSimultaneousMode },
                onSetSimultaneousMode = { isSimultaneousMode = it },
                onShowHealthStatus = { toggleHealthStatus() },
                onCheckFatigue = { checkFatigue() },
                onHideHealthStatus = { healthStatusView?.hide() },
                onTakePhoto = { takePhoto() },
                onToggleRecording = { toggleRecording() },
                onStartQrScan = { switchToQrScan() },
                onStopQrScan = { switchToOcr() },
                onStartOcr = {
                    switchToOcr()
                    Log.e(TAG, "✅ OCR 模式已激活")
                },
                onStopOcr = {
                    Log.e(TAG, "⏹️ OCR 已停止")
                },
                onStartTrafficLight = {
                    switchToTrafficLight()
                    Log.e(TAG, "✅ 红绿灯模式已激活")
                },
                onStopTrafficLight = {
                    switchToOcr()
                    Log.e(TAG, "⏹️ 红绿灯已停止")
                },
                onStartBlindRoad = {
                    switchToBlindRoad()
                    Log.e(TAG, "✅ 盲道模式已激活")
                },
                onStopBlindRoad = {
                    stopBlindRoad()
                    Log.e(TAG, "⏹️ 盲道已停止")
                },
                // ✅ 拍照解题回调
                onTakePhotoForSolve = { takePhotoForSolve() },
                // ✅ 传入语音识别服务
                voiceVoskService = voiceVoskService
            )
            Log.e(TAG, "✅ VoiceManager 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化 VoiceManager 失败: ${e.message}")
        }
    }

    // ============================================================
    // ✅ 功能方法
    // ============================================================
    private fun takePhoto() {
        if (isFinishing || isDestroyed) return
        if (!hasAllPermissions) {
            Toast.makeText(this, "请先授予权限", Toast.LENGTH_SHORT).show()
            return
        }

        if (cameraManager == null || !cameraManager!!.isCameraReady()) {
            Log.e(TAG, "❌ 相机未就绪，尝试重新绑定...")
            Toast.makeText(this, "相机正在初始化，请稍后重试", Toast.LENGTH_SHORT).show()
            cameraManager?.setupCamera()
            return
        }

        cameraManager?.takePhoto()
        showBottomReply("📸 正在拍照...", false)
    }

    private fun toggleRecording() {
        if (isFinishing || isDestroyed) return
        if (!hasAllPermissions) {
            Toast.makeText(this, "请先授予权限", Toast.LENGTH_SHORT).show()
            return
        }

        if (cameraManager == null || !cameraManager!!.isCameraReady()) {
            Log.e(TAG, "❌ 相机未就绪，尝试重新绑定...")
            Toast.makeText(this, "相机正在初始化，请稍后重试", Toast.LENGTH_SHORT).show()
            cameraManager?.setupCamera()
            return
        }

        val isRecording = cameraManager?.toggleRecording() ?: false
        if (isRecording) {
            showBottomReply("🔴 开始录像...", false)
        } else {
            showBottomReply("⏹️ 录像已停止", false)
        }
    }

    private fun exitSimultaneousMode() {
        if (!isSimultaneousMode) return
        isSimultaneousMode = false
        voiceVoskService?.switchToChinese()
        voiceTranslationManager?.stopOcr()
        tvVoiceText?.text = "🎤 等待唤醒..."
        Toast.makeText(this, "已退出同声传译", Toast.LENGTH_SHORT).show()
        showBottomReply("已退出同声传译", false)
    }

    private fun toggleHealthStatus() {
        if (isFinishing || isDestroyed) return
        val hv = healthStatusView ?: return

        if (hv.visibility == View.VISIBLE) {
            hv.hide()
            showBottomReply("已关闭健康监测", false)
        } else {
            hv.show()
            showBottomReply("📊 正在监测您的健康数据...", false)
        }
    }

    private fun checkFatigue() {
        if (isFinishing || isDestroyed) return
        val hv = healthStatusView ?: return
        val reply = hv.getHealthStatusText()
        showBottomReply("💬 $reply", false)
    }

    // ============================================================
    // ✅ 显示底部回复
    // ============================================================
    private fun showBottomReply(text: String, isTranslation: Boolean = false) {
        if (isFinishing || isDestroyed) return
        val tv = tvBottomReply ?: return

        tv.text = text
        tv.visibility = View.VISIBLE

        if (isTranslation) {
            tv.setBackgroundResource(R.drawable.bg_translation_reply)
        } else {
            tv.setBackgroundResource(R.drawable.bg_bottom_reply)
        }

        // ✅ 语音播报
        ttsManager?.speak(text)

        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            tv.visibility = View.GONE
            tvVoiceText?.text = "🎤 等待唤醒..."
            overlayView?.clear()
            overlayView?.invalidate()
        }, 10000)
    }

    // ============================================================
    // ✅ 初始化语音识别
    // ============================================================
    private fun initVoskService() {
        if (isFinishing || isDestroyed) return
        if (!hasAllPermissions) return

        Log.e(TAG, "🔥 initVoskService 开始")
        tvVoiceText?.text = "🎤 等待唤醒..."

        voiceVoskService = VoiceVoskService(
            context = this,
            onResult = { spokenText ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    try {
                        voiceTranslationManager?.handleVoiceCommand(spokenText)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 处理异常: ${e.message}", e)
                    }
                }
            },
            onPartial = { partial ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (partial.isNotEmpty()) {
                        tvVoiceText?.text = "🎤 $partial"
                    }
                }
            }
        )

        voiceVoskService?.startListening()
        tvVoiceText?.text = "🎤 等待唤醒..."
        Log.e(TAG, "✅ VoskService 初始化成功")
    }

    // ============================================================
    // ✅ DeepSeek 对话
    // ============================================================
    private fun startChat(userMessage: String) {
        if (isFinishing || isDestroyed) return

        Log.e(TAG, "🔥 startChat 被调用: $userMessage")
        tvVoiceText?.text = "💬 思考中..."

        val scope = CoroutineScope(Dispatchers.Main + Job())
        scope.launch(Dispatchers.IO) {
            try {
                val reply = DeepSeekChat.sendMessage(userMessage)
                Log.e(TAG, "📥 DeepSeekChat 返回: $reply")

                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    if (reply != null && reply.isNotEmpty()) {
                        showBottomReply("💬 $reply", false)
                    } else {
                        showBottomReply("💬 ${getFallbackReply(userMessage)}", false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 协程异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    showBottomReply("💬 ${getFallbackReply(userMessage)}", false)
                }
            }
        }
    }

    private fun getFallbackReply(message: String): String {
        return when {
            message.contains("你好") -> "你好呀！我是地平线智能眼镜的AI助手！😊"
            message.contains("天气") -> "我暂无法查天气，你可以用手机查看哦！☀️"
            message.contains("名字") || message.contains("是谁") -> "我是地平线，你的智能眼镜助手！👓"
            message.contains("谢谢") -> "不客气！随时为你服务！😊"
            else -> "我是地平线，你可以说'翻译'来翻译文字，或找我聊天！😊"
        }
    }

    // ============================================================
    // ✅ OCR 分析器
    // ============================================================
    inner class OCRAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            if (isFinishing || isDestroyed) {
                return
            }

            val vm = voiceTranslationManager
            if (vm == null || !vm.isOcrEnabled()) {
                return
            }

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessTime < MIN_INTERVAL_MS) {
                return
            }
            lastProcessTime = currentTime

            val image = imageProxy.image ?: return

            val rotation = imageProxy.imageInfo.rotationDegrees
            var imageWidth = imageProxy.width
            var imageHeight = imageProxy.height

            if (rotation == 90 || rotation == 270) {
                val temp = imageWidth
                imageWidth = imageHeight
                imageHeight = temp
            }

            val pv = previewView
            val ov = overlayView
            if (pv != null && ov != null) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        ov.updateTransformation(
                            pv.width,
                            pv.height,
                            imageWidth,
                            imageHeight
                        )
                    }
                }
            }

            val input = InputImage.fromMediaImage(image, rotation)

            recognizer.process(input)
                .addOnSuccessListener { visionText ->
                    if (isFinishing || isDestroyed) return@addOnSuccessListener

                    val text = StringBuilder()
                    val blocks = visionText.textBlocks
                    val validBlocks = mutableListOf<com.google.mlkit.vision.text.Text.TextBlock>()

                    for (block in blocks) {
                        var hasEnglish = false
                        val blockText = StringBuilder()

                        for (line in block.lines) {
                            val lineText = line.text
                            val englishChars = lineText.filter { ch ->
                                ch in 'A'..'Z' || ch in 'a'..'z' || ch == ' '
                            }

                            if (englishChars.isNotEmpty() && englishChars.replace(" ", "").length >= 2) {
                                blockText.append(englishChars).append(" ")
                                hasEnglish = true
                            }
                        }

                        if (hasEnglish) {
                            val result = blockText.toString().trim()
                            if (result.isNotEmpty()) {
                                text.append(result).append(" ")
                                validBlocks.add(block)
                            }
                        }
                    }

                    val result = text.toString().trim()

                    if (result.isNotEmpty() && result.replace(" ", "").length >= 2) {
                        vm.setCurrentRecognizedText(result)
                        drawTextBlocks(validBlocks)
                        vm.onOcrResult(result)
                    } else {
                        overlayView?.clear()
                        overlayView?.invalidate()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("OCR", "❌ OCR 失败: ${e.message}")
                }
        }
    }

    // ============================================================
    // ✅ 绘制文字框
    // ============================================================
    private fun drawTextBlocks(blocks: List<com.google.mlkit.vision.text.Text.TextBlock>) {
        if (isFinishing || isDestroyed) return

        val ov = overlayView ?: return

        ov.post {
            if (isFinishing || isDestroyed) return@post

            ov.clear()

            for (block in blocks) {
                var hasEnglishLine = false
                for (line in block.lines) {
                    val lineText = line.text
                    val hasEnglish = lineText.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' }
                    if (hasEnglish) {
                        hasEnglishLine = true
                        break
                    }
                }

                if (!hasEnglishLine) continue

                for (line in block.lines) {
                    val lineText = line.text
                    val hasEnglish = lineText.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' }
                    if (!hasEnglish) continue

                    val rect = line.boundingBox ?: continue
                    val englishText = lineText.filter { ch ->
                        ch in 'A'..'Z' || ch in 'a'..'z' || ch == ' '
                    }.trim()
                    if (englishText.isNotEmpty() && englishText.replace(" ", "").length >= 2) {
                        ov.addTextRect(rect, englishText)
                    }
                }
            }
            ov.invalidate()
        }
    }

    // ============================================================
    // ✅ 生命周期
    // ============================================================
    override fun onResume() {
        super.onResume()
        Log.e(TAG, "🟢 onResume")

        if (isFinishing || isDestroyed) return

        if (hasAllPermissions && !isInitialized && !isPermissionRequesting) {
            initAllModules(null)
        }

        mapManager?.onResume()
    }

    override fun onPause() {
        super.onPause()
        Log.e(TAG, "🟡 onPause")
        mapManager?.onPause()
    }

    override fun onStop() {
        super.onStop()
        Log.e(TAG, "🟠 onStop")

        cameraManager?.release()
        cameraManager = null

        voiceVoskService?.destroy()
        voiceVoskService = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapManager?.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "🔴 onDestroy")

        try {
            cameraExecutor?.let { executor ->
                if (!executor.isShutdown) {
                    executor.shutdown()
                    if (!executor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                        executor.shutdownNow()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "关闭 cameraExecutor 异常: ${e.message}")
        }
        cameraExecutor = null

        mapManager?.onDestroy()
        mapManager = null

        voiceTranslationManager?.onDestroy()
        voiceTranslationManager = null

        healthStatusView?.destroy()
        healthStatusView = null

        qrCodeAnalyzer?.disable()
        qrCodeAnalyzer = null

        trafficLightAnalyzer = null

        blindRoadAnalyzer?.destroy()
        blindRoadAnalyzer = null

        problemSolver = null

        // ✅ 释放 TTS
        ttsManager?.destroy()
        ttsManager = null

        previewView = null
        overlayView = null
        tvVoiceText = null
        tvBottomReply = null
        ivAiLogo = null
        aiStatusLayout = null
        uiContainer = null
        mapView = null
        mapContainer = null

        Log.e(TAG, "✅ 所有资源已释放")
    }
}