package com.blue.glassesapp.feature.home.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.blue.armobile.R

class SportTrackingActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val TAG = "SportTracking"
    }

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null

    // 步数相关
    private var initialSteps = -1f  // 初始总步数（设备开机累计）
    private var currentSteps = 0f   // 本次运动步数
    private var isFirstReading = true

    // 距离相关（步幅 0.75 米，可以根据用户身高调整）
    private val STEP_LENGTH_METERS = 0.75

    // UI 控件
    private lateinit var tvSteps: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sport_tracking)

        // 绑定 UI
        tvSteps = findViewById(R.id.tv_steps)
        tvDistance = findViewById(R.id.tv_distance)
        tvStatus = findViewById(R.id.tv_status)

        // 初始化传感器
        setupSensors()
    }

    private fun setupSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // ✅ 获取计步传感器（TYPE_STEP_COUNTER）
        // 这个传感器返回设备开机到现在的总步数
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // ✅ 获取步数检测传感器（TYPE_STEP_DETECTOR）
        // 这个传感器每走一步触发一次
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        // 检查设备是否支持
        if (stepCounterSensor == null && stepDetectorSensor == null) {
            tvStatus.text = "❌ 设备不支持计步功能"
            Toast.makeText(this, "设备不支持计步传感器", Toast.LENGTH_LONG).show()
            return
        }

        // 判断使用哪个传感器
        if (stepCounterSensor != null) {
            tvStatus.text = "✅ 使用 StepCounter 传感器"
            Log.e(TAG, "✅ StepCounter 传感器可用")
        } else {
            tvStatus.text = "⚠️ 使用 StepDetector 传感器（精度较低）"
            Log.e(TAG, "⚠️ StepDetector 传感器可用，StepCounter 不可用")
        }
    }

    // ============================================================
    // ✅ 生命周期：注册/注销传感器
    // ============================================================

    override fun onResume() {
        super.onResume()
        registerSensors()
    }

    override fun onPause() {
        super.onPause()
        unregisterSensors()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterSensors()
    }

    private fun registerSensors() {
        // 优先使用 StepCounter
        stepCounterSensor?.let {
            val success = sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.e(TAG, "📊 StepCounter 注册: ${if (success) "成功" else "失败"}")
        } ?: run {
            // 降级使用 StepDetector
            stepDetectorSensor?.let {
                sensorManager.registerListener(
                    this,
                    it,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
                Log.e(TAG, "📊 StepDetector 注册成功（降级方案）")
            }
        }
    }

    private fun unregisterSensors() {
        try {
            sensorManager.unregisterListener(this)
            Log.e(TAG, "📊 传感器已注销")
        } catch (e: Exception) {
            Log.e(TAG, "📊 注销传感器异常: ${e.message}")
        }
    }

    // ============================================================
    // ✅ 传感器回调
    // ============================================================

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            // ✅ StepCounter：获取总步数
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values[0]
                Log.d(TAG, "📊 总步数: $totalSteps")

                if (isFirstReading) {
                    // 第一次读取，记录初始值
                    initialSteps = totalSteps
                    isFirstReading = false
                    Log.e(TAG, "📊 初始步数: $initialSteps")
                }

                // 本次运动步数 = 当前总步数 - 初始步数
                currentSteps = totalSteps - initialSteps

                // 更新 UI
                updateUI()
            }

            // ✅ StepDetector：每走一步触发一次
            Sensor.TYPE_STEP_DETECTOR -> {
                // 步数 +1
                currentSteps += 1
                Log.d(TAG, "👣 检测到一步，当前步数: $currentSteps")

                // 更新 UI
                updateUI()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 传感器精度变化，忽略
    }

    // ============================================================
    // ✅ UI 更新
    // ============================================================

    private fun updateUI() {
        runOnUiThread {
            // 计算距离
            val distanceMeters = currentSteps * STEP_LENGTH_METERS
            val distanceKm = distanceMeters / 1000

            // 更新步数显示
            tvSteps.text = "🚶 ${currentSteps.toInt()} 步"

            // 更新距离显示
            tvDistance.text = when {
                distanceKm >= 1 -> "📍 ${"%.2f".format(distanceKm)} 公里"
                else -> "📍 ${distanceMeters.toInt()} 米"
            }
        }
    }

    // ============================================================
    // ✅ 获取当前运动数据（供外部调用）
    // ============================================================

    fun getCurrentSteps(): Int = currentSteps.toInt()

    fun getCurrentDistance(): Double = currentSteps * STEP_LENGTH_METERS
}