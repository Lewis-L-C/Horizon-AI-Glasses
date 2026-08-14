package com.blue.glassesapp.feature.home.ui

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.blue.armobile.BuildConfig
import com.blue.armobile.R
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 语音翻译管理器 - 管理语音命令处理和翻译功能
 */
class VoiceTranslationManager(
    private val activity: AppCompatActivity,
    private val mapManager: MapNavigationManager,
    private val onShowBottomReply: (String, Boolean) -> Unit,
    private val onUpdateVoiceText: (String) -> Unit,
    private val onSetAiLogo: (Int) -> Unit,
    private val onStartChat: (String) -> Unit,
    private val onExitSimultaneousMode: () -> Unit,
    private val isSimultaneousMode: () -> Boolean,
    private val onSetSimultaneousMode: (Boolean) -> Unit,
    private val onShowHealthStatus: () -> Unit,
    private val onCheckFatigue: () -> Unit,
    private val onHideHealthStatus: () -> Unit,
    private val onTakePhoto: () -> Unit,
    private val onToggleRecording: () -> Unit,
    private val onStartQrScan: () -> Unit,
    private val onStopQrScan: () -> Unit,
    private val onStartOcr: () -> Unit,
    private val onStopOcr: () -> Unit,
    private val onStartTrafficLight: () -> Unit,
    private val onStopTrafficLight: () -> Unit,
    private val onStartBlindRoad: () -> Unit,
    private val onStopBlindRoad: () -> Unit,
    private val voiceVoskService: VoiceVoskService? = null,
    private val onTakePhotoForSolve: () -> Unit
){

    companion object {
        private const val TAG = "VoiceTranslationManager"
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var isOcrEnabled = false
    private var isProcessing = false
    private var currentRecognizedText = ""
    private var ocrCallback: ((String) -> Unit)? = null
    private var isProcessingCommand = false

    // ============================================================
    // ✅ 运动检测（简化版 - 只检测加速度波动）
    // ============================================================
    private var sportSensorManager: SensorManager? = null
    private var sportAccelerometer: Sensor? = null
    private var sportCurrentSteps = 0
    private var isSportTracking = false
    private val STEP_LENGTH_METERS = 0.75

    // 简化版参数
    private var lastStepTime = 0L
    private var stepCountBuffer = 0f  // 用于平滑检测
    private val MIN_STEP_INTERVAL = 250L  // 毫秒

    private fun startSportTracking() {
        if (isSportTracking) {
            Log.e(TAG, "🏃 运动检测已在进行中")
            return
        }

        isSportTracking = true
        sportCurrentSteps = 0
        lastStepTime = 0L
        stepCountBuffer = 0f

        sportSensorManager = activity.getSystemService(AppCompatActivity.SENSOR_SERVICE) as SensorManager
        sportAccelerometer = sportSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sportAccelerometer == null) {
            Log.e(TAG, "❌ 设备没有加速度传感器")
            onShowBottomReply("❌ 设备不支持计步", false)
            isSportTracking = false
            return
        }

        val registered = sportSensorManager?.registerListener(
            sportAccelerometerListener,
            sportAccelerometer,
            SensorManager.SENSOR_DELAY_GAME
        ) ?: false

        if (registered) {
            Log.e(TAG, "✅ 加速度传感器注册成功")
            onShowBottomReply("🏃 开始记录运动数据...", false)
            Log.e(TAG, "🏃 运动检测已启动（简化版）")
        } else {
            Log.e(TAG, "❌ 传感器注册失败")
            onShowBottomReply("❌ 计步传感器注册失败", false)
            isSportTracking = false
        }
    }

    private fun stopSportTracking() {
        if (!isSportTracking) {
            Log.e(TAG, "🏃 运动检测未启动")
            return
        }
        isSportTracking = false
        sportSensorManager?.unregisterListener(sportAccelerometerListener)
        val distance = sportCurrentSteps * STEP_LENGTH_METERS
        onShowBottomReply("🏃 运动结束！步数: $sportCurrentSteps，距离: ${distance.toInt()}米", false)
        Log.e(TAG, "🏃 运动检测已停止 - 步数: $sportCurrentSteps")
    }

    private val sportAccelerometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || !isSportTracking) return

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    // ✅ 计算加速度大小
                    val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                    // ✅ 计算加速度变化量（与重力 9.8 的差值）
                    val delta = Math.abs(magnitude - 9.8f)

                    // ✅ 如果变化量超过 0.5，说明有运动
                    if (delta > 0.5f) {
                        val currentTime = System.currentTimeMillis()

                        // ✅ 每 250ms 记一步（防止过快重复）
                        if (currentTime - lastStepTime > MIN_STEP_INTERVAL) {
                            sportCurrentSteps++
                            lastStepTime = currentTime

                            val distance = sportCurrentSteps * STEP_LENGTH_METERS
                            activity.runOnUiThread {
                                onShowBottomReply(
                                    "🏃 步数: $sportCurrentSteps | 距离: ${distance.toInt()}米",
                                    false
                                )
                            }
                            Log.d(TAG, "👣 检测到一步 (delta: $delta)，当前步数: $sportCurrentSteps")
                        }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // 忽略
        }
    }
    // ============================================================
    // ✅ 语音命令处理
    // ============================================================
    fun handleVoiceCommand(spokenText: String) {
        Log.e(TAG, "🎤 识别到: $spokenText")
        onUpdateVoiceText("🗣️ $spokenText")
        onSetAiLogo(R.drawable.ic_ai_logo)

        if (isProcessingCommand) {
            Log.e(TAG, "⏳ 正在处理上一个命令，请稍候")
            return
        }

        // ✅ 本地快速匹配：运动检测
        val lowerText = spokenText.lowercase().trim()
        if (lowerText.contains("运动") ||
            lowerText.contains("开始运动") ||
            lowerText.contains("跑步") ||
            lowerText.contains("开始走路") ||
            lowerText.contains("计步")) {

            Log.e(TAG, "✅ 本地匹配: 运动检测")
            // ✅ 不跳转，直接开始计步
            startSportTracking()
            return
        }

        // ✅ 本地快速匹配：关闭运动检测
        if (lowerText.contains("关闭运动") ||
            lowerText.contains("停止运动") ||
            lowerText.contains("结束运动")) {
            Log.e(TAG, "✅ 本地匹配: 关闭运动检测")
            stopSportTracking()
            return
        }

        // ✅ 本地快速匹配：3D导航（跳过 AI）
        if (lowerText.contains("实际导航") ||
            lowerText.contains("3d导航") ||
            lowerText.contains("3导航") ||
            lowerText.contains("三维导航")) {

            Log.e(TAG, "✅ 本地匹配: 3D导航")

            val hasDestination = lowerText.contains("到") || lowerText.contains("去")
            val afterTo = if (lowerText.contains("到")) {
                spokenText.substringAfter("到").trim()
            } else if (lowerText.contains("去")) {
                spokenText.substringAfter("去").trim()
            } else {
                ""
            }

            if (hasDestination && afterTo.isNotEmpty() &&
                afterTo != "实际导航" && afterTo != "3d导航" && afterTo != "3导航") {
                execute3DNavigation(spokenText)
            } else {
                toggle3DNavigation(true)
            }
            return
        }

        // ✅ 关闭3D导航
        if (lowerText.contains("关闭3d导航") ||
            lowerText.contains("关闭3导航") ||
            lowerText.contains("关闭实际导航") ||
            lowerText.contains("关闭三维导航") ||
            lowerText.contains("退出3d导航")) {
            Log.e(TAG, "✅ 本地匹配: 关闭3D导航")
            toggle3DNavigation(false)
            return
        }

        // ✅ 同传模式
        if (isSimultaneousMode()) {
            val lowerTextSim = spokenText.lowercase().trim()
            if (lowerTextSim == "out" || lowerTextSim == "exit" || lowerTextSim == "stop" ||
                lowerTextSim.contains("关闭同传") || lowerTextSim.contains("退出同传") ||
                lowerTextSim.contains("停止同传")) {
                Log.e(TAG, "⏹️ 同传退出命令: $spokenText")
                onExitSimultaneousMode()
                onSetSimultaneousMode(false)
                voiceVoskService?.switchToChinese()
                onShowBottomReply("已关闭同声传译", false)
                onUpdateVoiceText("🎤 等待唤醒...")
                return
            }

            val hasEnglish = spokenText.any { it in 'A'..'Z' || it in 'a'..'z' }
            if (hasEnglish) {
                Log.e(TAG, "🌐 同传翻译英文: $spokenText")
                onUpdateVoiceText("🌐 翻译中...")
                translateTextSimultaneous(spokenText) { translation ->
                    onShowBottomReply("🌐 $translation", true)
                    onUpdateVoiceText("🌐 $translation")
                }
                return
            }
        }

        // ============================================================
        // ✅ AI 意图分析
        // ============================================================
        isProcessingCommand = true
        onUpdateVoiceText("🤔 思考中...")

        scope.launch(Dispatchers.IO) {
            try {
                val intent = analyzeIntent(spokenText)
                Log.e(TAG, "🤖 AI 意图分析: $intent")

                withContext(Dispatchers.Main) {
                    executeIntent(intent, spokenText)
                    isProcessingCommand = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ AI 意图分析失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (isSimultaneousMode()) {
                        onExitSimultaneousMode()
                    }
                    stopOcr()
                    onStopOcr()
                    onStopQrScan()
                    onStopTrafficLight()
                    onStopBlindRoad()
                    onStartChat(spokenText)
                    isProcessingCommand = false
                }
            }
        }
    }

    // ============================================================
    // ✅ 3D导航相关方法
    // ============================================================

    /**
     * 执行 3D 导航（本地快速匹配，有目的地）
     */
    private fun execute3DNavigation(spokenText: String) {
        Log.e(TAG, "🗺️ 执行 3D 导航: $spokenText")

        var destination = spokenText
        val lower = spokenText.lowercase()

        destination = destination.replace(Regex("3D导航", RegexOption.IGNORE_CASE), "")
        destination = destination.replace(Regex("3导航", RegexOption.IGNORE_CASE), "")
        destination = destination.replace(Regex("实际导航", RegexOption.IGNORE_CASE), "")
        destination = destination.replace(Regex("三维导航", RegexOption.IGNORE_CASE), "")
        destination = destination.replace(Regex("导航到", RegexOption.IGNORE_CASE), "")
        destination = destination.replace(Regex("导航", RegexOption.IGNORE_CASE), "")
        destination = destination.replace(Regex("去", RegexOption.IGNORE_CASE), "")
        destination = destination.trim()

        if (destination.isEmpty()) {
            onShowBottomReply("❌ 请说想去哪里，如'3D导航到肯德基'", false)
            return
        }

        if (mapManager.isAMapReady() && mapManager.isMapInitialized()) {
            if (!mapManager.isMapVisible()) {
                mapManager.showMap()
            }
            mapManager.enable3DNavigation(true)
            mapManager.apply3DNavigationView()
            mapManager.searchAndNavigate(destination)
        } else {
            onShowBottomReply("❌ 地图未初始化，请先开启定位", false)
        }
    }

    /**
     * 切换 3D 导航模式（无目的地，只切换视角）
     */
    private fun toggle3DNavigation(enable: Boolean) {
        if (enable) {
            Log.e(TAG, "🗺️ 开启 3D 导航模式")
            if (mapManager.isAMapReady() && mapManager.isMapInitialized()) {
                if (!mapManager.isMapVisible()) {
                    mapManager.showMap()
                }
                mapManager.enable3DNavigation(true)
                mapManager.apply3DNavigationView()
                onShowBottomReply("🗺️ 3D 导航模式已开启", false)
            } else {
                mapManager.showMap()
                Handler(Looper.getMainLooper()).postDelayed({
                    mapManager.enable3DNavigation(true)
                    mapManager.apply3DNavigationView()
                    onShowBottomReply("🗺️ 3D 导航模式已开启", false)
                }, 500)
            }
        } else {
            Log.e(TAG, "🗺️ 关闭 3D 导航模式")
            mapManager.enable3DNavigation(false)
            mapManager.apply2DView()
            onShowBottomReply("🗺️ 已切换到 2D 俯视图", false)
        }
    }

    // ✅ AI 意图分析
    private suspend fun analyzeIntent(text: String): String {
        val prompt = """
            分析用户说的这句话，判断用户想要执行什么操作。只输出对应的操作名称，不要输出其他内容。

            可用的操作：
            1. translate - 翻译文字/OCR识别翻译
            2. close_translate - 关闭翻译/停止翻译
            3. simultaneous - 开启同声传译
            4. close_simultaneous - 关闭同声传译/退出同传
            5. show_health - 显示健康监测
            6. close_health - 关闭健康监测/隐藏健康
            7. check_fatigue - 查询疲劳度
            8. show_map - 显示地图
            9. close_map - 关闭地图/隐藏地图
            10. zoom_in - 放大地图
            11. zoom_out - 缩小地图
            12. navigate - 导航到某地
            13. cancel_navigation - 取消导航
            14. close_all - 关闭所有功能/关闭全部
            15. take_photo - 拍照
            16. toggle_recording - 开始/停止录像
            17. start_chat - 普通对话
            18. pay - 扫码支付
            19. traffic_light - 开启红绿灯识别
            20. close_traffic_light - 关闭红绿灯识别
            21. blind_road - 开启盲道识别
            22. close_blind_road - 关闭盲道识别
            23. solve_problem - 拍照解题/题目解答
            24. navigate_3d - 3D导航/实际导航
            25. start_sport - 开始运动检测/跑步/走路
            26. stop_sport - 停止运动检测
            27. query_sport - 查询运动数据/步数

            用户说：$text

            示例：
            # 拍照相关
            "拍照" → take_photo
            "拍一张" → take_photo
            "帮我拍个照" → take_photo
            "拍个照" → take_photo
            "咔嚓" → take_photo
            
            # 录像相关
            "录像" → toggle_recording
            "开始录像" → toggle_recording
            "停止录像" → toggle_recording
            "录制" → toggle_recording
            "开始录制" → toggle_recording
            "停止录制" → toggle_recording
            "拍视频" → toggle_recording
            
            # 翻译相关
            "翻译" → translate
            "翻译一下" → translate
            "帮我翻译" → translate
            "关闭翻译" → close_translate
            "停止翻译" → close_translate
            "取消翻译" → close_translate
            
            # 同声传译相关
            "同声传译" → simultaneous
            "同传" → simultaneous
            "开启同传" → simultaneous
            "实时翻译" → simultaneous
            "退出同传" → close_simultaneous
            "关闭同传" → close_simultaneous
            "停止同传" → close_simultaneous
            
            # 健康相关
            "我的健康" → show_health
            "打开健康" → show_health
            "健康监测" → show_health
            "看看我的身体" → show_health
            "关闭健康" → close_health
            "隐藏健康" → close_health
            "停止健康监测" → close_health
            "我累吗" → check_fatigue
            "疲劳" → check_fatigue
            "我有点累" → check_fatigue
            "好疲惫啊" → check_fatigue
            "状态怎么样" → check_fatigue
            
            # 地图相关
            "当前位置在哪" → show_map
            "打开地图" → show_map
            "关闭地图" → close_map
            "隐藏地图" → close_map
            "放大一点" → zoom_in
            "缩小" → zoom_out
            "导航到肯德基" → navigate
            "去西湖" → navigate
            "取消导航" → cancel_navigation

            # 3D导航相关
            "3D导航" → navigate_3d
            "3D导航到肯德基" → navigate_3d
            "开启3D导航" → navigate_3d
            "实际导航" → navigate_3d
            
            # 支付相关
            "支付" → pay
            "扫码支付" → pay
            "付款" → pay
            "我要付款" → pay
            "扫一扫" → pay
            
            # 红绿灯相关
            "红绿灯" → traffic_light
            "开启红绿灯" → traffic_light
            "识别红绿灯" → traffic_light
            "关闭红绿灯" → close_traffic_light
            "停止红绿灯" → close_traffic_light
            
            # 盲道/道路相关
            "检测前方道路" → blind_road
            "盲道" → blind_road
            "开启盲道" → blind_road
            "识别盲道" → blind_road
            "道路检测" → blind_road
            "忙着检测" → blind_road
            "开启道路检测" → blind_road
            "忙道检测" → blind_road
            "忙到检测" → blind_road
            "关闭盲道" → close_blind_road
            "停止盲道" → close_blind_road
            "关闭道路检测" → close_blind_road
            "停止道路检测" → close_blind_road

            # 解题相关
            "帮我解题" → solve_problem
            "拍照解题" → solve_problem
            "这道题怎么做" → solve_problem
            "帮我看看这道题" → solve_problem

            # 运动检测相关
            "开始跑步" → start_sport
            "开始走路" → start_sport
            "运动检测" → start_sport
            "开始运动" → start_sport
            "我想跑步" → start_sport
            "走路" → start_sport
            "停止运动" → stop_sport
            "结束运动" → stop_sport
            "关闭运动" → stop_sport
            "走了多少步" → query_sport
            "跑了多远" → query_sport
            "运动数据" → query_sport
            "步数" → query_sport
            
            # 关闭所有
            "关闭所有" → close_all
            "关闭全部" → close_all
            "全部关闭" → close_all
            "清理所有" → close_all
            
            # 对话
            "今天天气怎么样" → start_chat
            "你好" → start_chat
        """.trimIndent()

        val reply = DeepSeekChat.sendSimpleMessage(prompt)
        return reply?.trim()?.lowercase() ?: "start_chat"
    }

    // ✅ 执行意图
    private fun executeIntent(intent: String, spokenText: String) {
        Log.e(TAG, "🎯 执行意图: $intent")

        when (intent) {
            // ============================================================
            // ✅ 翻译
            // ============================================================
            "translate" -> {
                Log.e(TAG, "➡️ 翻译模式")
                if (isSimultaneousMode()) {
                    onExitSimultaneousMode()
                }
                onStopQrScan()
                onStopTrafficLight()
                onStopBlindRoad()
                onStartOcr()

                Toast.makeText(activity, "正在识别文字...", Toast.LENGTH_SHORT).show()
                startOcr { text ->
                    if (text.isNotEmpty()) {
                        translateText(text) { result ->
                            onShowBottomReply("📝 $result", true)
                            stopOcr()
                        }
                    } else {
                        Toast.makeText(activity, "未识别到文字", Toast.LENGTH_SHORT).show()
                        stopOcr()
                    }
                }
            }
            "close_translate" -> {
                Log.e(TAG, "➡️ 关闭翻译")
                stopOcr()
                onStopOcr()
                onShowBottomReply("已关闭翻译", false)
                onUpdateVoiceText("🎤 等待唤醒...")
            }

            // ============================================================
            // ✅ 同声传译
            // ============================================================
            "simultaneous" -> {
                Log.e(TAG, "➡️ 同声传译模式")
                stopOcr()
                onStopQrScan()
                onStopTrafficLight()
                onStopBlindRoad()
                onSetSimultaneousMode(true)
                voiceVoskService?.switchToEnglish()
                Toast.makeText(activity, "🌐 同声传译已开启（请说英文）", Toast.LENGTH_SHORT).show()
                onShowBottomReply("🌐 同声传译已开启（请说英文）\n说 'out' 退出", false)
            }
            "close_simultaneous" -> {
                Log.e(TAG, "➡️ 关闭同声传译")
                onExitSimultaneousMode()
                voiceVoskService?.switchToChinese()
                onShowBottomReply("已关闭同声传译", false)
            }

            // ============================================================
            // ✅ 健康相关
            // ============================================================
            "show_health" -> {
                Log.e(TAG, "➡️ 显示健康监测")
                onShowHealthStatus()
            }
            "close_health" -> {
                Log.e(TAG, "➡️ 关闭健康监测")
                onHideHealthStatus()
                onShowBottomReply("已关闭健康监测", false)
            }
            "check_fatigue" -> {
                Log.e(TAG, "➡️ 查询疲劳度")
                onCheckFatigue()
            }

            // ============================================================
            // ✅ 地图相关
            // ============================================================
            "show_map" -> {
                Log.e(TAG, "➡️ 显示地图")
                mapManager.showMap()
            }
            "close_map" -> {
                Log.e(TAG, "➡️ 关闭地图")
                mapManager.hideMap()
                onShowBottomReply("已关闭地图", false)
            }
            "zoom_in" -> {
                Log.e(TAG, "➡️ 放大地图")
                mapManager.zoomIn()
            }
            "zoom_out" -> {
                Log.e(TAG, "➡️ 缩小地图")
                mapManager.zoomOut()
            }
            "navigate" -> {
                Log.e(TAG, "➡️ 导航")
                var destination = spokenText
                val lower = spokenText.lowercase()
                if (lower.contains("导航到")) {
                    destination = spokenText.substringAfter("导航到").trim()
                } else if (lower.contains("导航")) {
                    destination = spokenText.substringAfter("导航").trim()
                } else if (lower.contains("去")) {
                    destination = spokenText.substringAfter("去").trim()
                }

                if (destination.isEmpty() || destination == "导航" || destination == "去" || destination == "到") {
                    onShowBottomReply("❌ 请说想去哪里，如'导航到肯德基'", false)
                    return
                }

                if (mapManager.isAMapReady() && mapManager.isMapInitialized()) {
                    if (!mapManager.isMapVisible()) {
                        mapManager.showMap()
                    }
                    mapManager.searchAndNavigate(destination)
                } else {
                    onShowBottomReply("❌ 地图未初始化，请先开启定位", false)
                }
            }
            "cancel_navigation" -> {
                Log.e(TAG, "➡️ 取消导航")
                mapManager.cancelNavigation()
                onShowBottomReply("已取消导航", false)
            }

            // ============================================================
            // ✅ 3D导航（AI 意图分析返回的分支）
            // ============================================================
            "navigate_3d" -> {
                Log.e(TAG, "➡️ 3D导航模式 (AI)")

                var destination = spokenText
                val lower = spokenText.lowercase()

                if (lower.contains("3D导航到")) {
                    destination = spokenText.substringAfter("3D导航到").trim()
                } else if (lower.contains("实际导航到")) {
                    destination = spokenText.substringAfter("实际导航到").trim()
                } else if (lower.contains("导航到")) {
                    destination = spokenText.substringAfter("导航到").trim()
                } else if (lower.contains("去")) {
                    destination = spokenText.substringAfter("去").trim()
                } else {
                    destination = destination.replace(Regex("3D导航", RegexOption.IGNORE_CASE), "")
                    destination = destination.replace(Regex("实际导航", RegexOption.IGNORE_CASE), "")
                    destination = destination.replace(Regex("导航", RegexOption.IGNORE_CASE), "")
                    destination = destination.replace(Regex("开启", RegexOption.IGNORE_CASE), "")
                    destination = destination.trim()
                }

                // ✅ 如果没有具体目的地 → 只开启3D模式
                if (destination.isEmpty() ||
                    destination == "3D导航" ||
                    destination == "实际导航" ||
                    destination == "导航") {
                    Log.e(TAG, "🗺️ 无目的地，开启3D导航模式")
                    toggle3DNavigation(true)
                    return
                }

                // ✅ 有目的地 → 执行3D导航
                if (mapManager.isAMapReady() && mapManager.isMapInitialized()) {
                    if (!mapManager.isMapVisible()) {
                        mapManager.showMap()
                    }
                    mapManager.enable3DNavigation(true)
                    mapManager.apply3DNavigationView()
                    mapManager.searchAndNavigate(destination)
                } else {
                    onShowBottomReply("❌ 地图未初始化，请先开启定位", false)
                }
            }

            // ============================================================
            // ✅ 拍照
            // ============================================================
            "take_photo" -> {
                Log.e(TAG, "➡️ 拍照")
                onTakePhoto()
            }

            // ============================================================
            // ✅ 录像
            // ============================================================
            "toggle_recording" -> {
                Log.e(TAG, "➡️ 切换录像")
                onToggleRecording()
            }

            // ============================================================
            // ✅ 支付
            // ============================================================
            "pay" -> {
                Log.e(TAG, "➡️ 支付模式")
                if (isSimultaneousMode()) {
                    onExitSimultaneousMode()
                }
                stopOcr()
                onStopOcr()
                onStopTrafficLight()
                onStopBlindRoad()
                onStartQrScan()
                Toast.makeText(activity, "📱 请将付款码对准摄像头", Toast.LENGTH_SHORT).show()
                onShowBottomReply("📱 请将付款码对准摄像头", false)
            }

            // ============================================================
            // ✅ 红绿灯识别
            // ============================================================
            "traffic_light" -> {
                Log.e(TAG, "➡️ 开启红绿灯识别")
                if (isSimultaneousMode()) {
                    onExitSimultaneousMode()
                }
                stopOcr()
                onStopOcr()
                onStopQrScan()
                onStopBlindRoad()
                onStartTrafficLight()
                Toast.makeText(activity, "🚦 红绿灯识别已开启", Toast.LENGTH_SHORT).show()
                onShowBottomReply("🚦 红绿灯识别已开启", false)
            }
            "close_traffic_light" -> {
                Log.e(TAG, "➡️ 关闭红绿灯识别")
                onStopTrafficLight()
                onShowBottomReply("已关闭红绿灯识别", false)
            }

            // ============================================================
            // ✅ 盲道识别
            // ============================================================
            "blind_road" -> {
                Log.e(TAG, "➡️ 开启盲道识别")
                if (isSimultaneousMode()) {
                    onExitSimultaneousMode()
                }
                stopOcr()
                onStopOcr()
                onStopQrScan()
                onStopTrafficLight()
                onStartBlindRoad()
                Toast.makeText(activity, "🟡 盲道识别已开启", Toast.LENGTH_SHORT).show()
                onShowBottomReply("🟡 盲道识别已开启", false)
            }
            "close_blind_road" -> {
                Log.e(TAG, "➡️ 关闭盲道识别")
                onStopBlindRoad()
                onShowBottomReply("已关闭盲道识别", false)
            }

            // ============================================================
            // ✅ 拍照解题
            // ============================================================
            "solve_problem" -> {
                Log.e(TAG, "➡️ 拍照解题模式")
                if (isSimultaneousMode()) {
                    onExitSimultaneousMode()
                }
                stopOcr()
                onStopOcr()
                onStopQrScan()
                onStopTrafficLight()
                onStopBlindRoad()
                onTakePhotoForSolve()
            }

            // ============================================================
            // ✅ 运动检测
            // ============================================================
            "start_sport" -> {
                Log.e(TAG, "➡️ 开始运动检测 (AI)")
                startSportTracking()
            }
            "stop_sport" -> {
                Log.e(TAG, "➡️ 停止运动检测 (AI)")
                stopSportTracking()
            }
            "query_sport" -> {
                Log.e(TAG, "➡️ 查询运动数据 (AI)")
                if (isSportTracking) {
                    val distance = sportCurrentSteps * STEP_LENGTH_METERS
                    onShowBottomReply("🏃 当前步数: ${sportCurrentSteps.toInt()}，距离: ${distance.toInt()}米", false)
                } else {
                    onShowBottomReply("🏃 当前未在运动检测中，说'开始跑步'启动", false)
                }
            }

            // ============================================================
            // ✅ 关闭所有
            // ============================================================
            "close_all" -> {
                Log.e(TAG, "➡️ 关闭所有功能")
                // 运动检测也要停止
                stopSportTracking()
                onHideHealthStatus()
                mapManager.hideMap()
                if (isSimultaneousMode()) {
                    onExitSimultaneousMode()
                }
                stopOcr()
                onStopOcr()
                onStopQrScan()
                onStopTrafficLight()
                onStopBlindRoad()
                onShowBottomReply("已关闭所有功能", false)
                onUpdateVoiceText("🎤 等待唤醒...")
                Toast.makeText(activity, "已关闭所有功能", Toast.LENGTH_SHORT).show()
            }

            // ============================================================
            // ✅ 普通对话
            // ============================================================
            else -> {
                Log.e(TAG, "➡️ 普通对话")
                if (isSimultaneousMode()) {
                    onExitSimultaneousMode()
                }
                stopOcr()
                onStopOcr()
                onStopQrScan()
                onStopTrafficLight()
                onStopBlindRoad()
                onStartChat(spokenText)
            }
        }
    }

    // ============================================================
    // ✅ OCR 识别
    // ============================================================
    fun startOcr(onResult: (String) -> Unit) {
        isOcrEnabled = true
        ocrCallback = onResult
        onUpdateVoiceText("📷 正在识别文字...")
    }

    fun stopOcr() {
        isOcrEnabled = false
        ocrCallback = null
    }

    fun isOcrEnabled(): Boolean = isOcrEnabled

    fun setCurrentRecognizedText(text: String) {
        currentRecognizedText = text
    }

    fun onOcrResult(text: String) {
        ocrCallback?.let {
            if (text.isNotEmpty() && text.length >= 3) {
                it(text)
            }
        }
    }

    // ============================================================
    // ✅ OCR 翻译
    // ============================================================
    fun translateText(text: String, onResult: (String) -> Unit) {
        if (isProcessing) return
        isProcessing = true

        onUpdateVoiceText("📷 翻译中...")

        val body = gson.toJson(
            mapOf(
                "model" to "deepseek-chat",
                "messages" to listOf(
                    mapOf(
                        "role" to "system",
                        "content" to "把用户输入翻译成中文，只输出翻译结果，不要添加任何解释。如果已经是中文，直接返回原文。"
                    ),
                    mapOf(
                        "role" to "user",
                        "content" to text
                    )
                ),
                "stream" to false
            )
        )

        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val response = client.newCall(request).execute()
                val json = response.body?.string() ?: ""
                val result = parseTranslationResult(json)
                Log.e(TAG, "翻译结果: $result")

                withContext(Dispatchers.Main) {
                    onResult(result)
                    isProcessing = false
                    isOcrEnabled = false
                    onUpdateVoiceText("✅ 翻译完成")
                }
            } catch (e: Exception) {
                Log.e(TAG, "翻译异常: ${e.message}")
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    isOcrEnabled = false
                    onUpdateVoiceText("❌ 翻译失败")
                    onResult("翻译失败，请重试")
                }
            }
        }
    }

    // ============================================================
    // ✅ 同声传译翻译
    // ============================================================
    fun translateTextSimultaneous(text: String, onResult: (String) -> Unit) {
        Log.e(TAG, "🌐 翻译: $text")

        val body = gson.toJson(
            mapOf(
                "model" to "deepseek-chat",
                "messages" to listOf(
                    mapOf(
                        "role" to "system",
                        "content" to "用户说了一句英文，可能不完整或有拼写错误。请补全并翻译成中文，只输出翻译结果，不要添加任何解释。"
                    ),
                    mapOf(
                        "role" to "user",
                        "content" to text
                    )
                ),
                "stream" to false
            )
        )

        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val response = client.newCall(request).execute()
                val json = response.body?.string() ?: ""
                val result = parseTranslationResult(json)
                Log.e(TAG, "✅ 翻译结果: $result")

                withContext(Dispatchers.Main) {
                    onResult(result)
                    onUpdateVoiceText("🌐 同声传译中...")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 翻译异常: ${e.message}")
                withContext(Dispatchers.Main) {
                    onUpdateVoiceText("🌐 翻译失败")
                    onResult("翻译失败，请重试")
                }
            }
        }
    }

    private fun parseTranslationResult(json: String): String {
        return try {
            val obj = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val choices = obj.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val message = choices[0].asJsonObject.getAsJsonObject("message")
                message?.get("content")?.asString ?: "翻译失败"
            } else {
                "翻译失败"
            }
        } catch (_: Exception) {
            "翻译失败"
        }
    }

    fun onDestroy() {
        // ✅ 停止运动检测
        stopSportTracking()
        scope.cancel()
    }
}