package com.blue.glassesapp.feature.home.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.blue.armobile.R

/**
 * 健康状态视图
 * 在右上角显示心率、血氧、疲劳度
 * 内部包含数据模拟器
 */
class HealthStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ============================================================
    // ✅ 内部数据模拟器
    // ============================================================
    private inner class HealthDataSimulator {
        private var baseHeartRate = 72
        private var usageSeconds = 0

        fun generateHeartRate(fatigueLevel: Int): Int {
            // ✅ 使用 Random.Default 或直接调用 Random.nextInt()
            val fluctuation = kotlin.random.Random.nextInt(-8, 9) // 注意：9 是开区间，取不到 9，实际范围 -8 到 8
            return (baseHeartRate + fluctuation + fatigueLevel / 2).coerceIn(60, 120)
        }

        fun generateSpO2(): Int {
            // ✅ 使用 Random.Default
            return kotlin.random.Random.nextInt(95, 101) // 95 到 100
        }

        fun generateFatigueLevel(): Int {
            usageSeconds += 3
            return (usageSeconds / 60).coerceIn(0, 100)
        }

        fun reset() {
            usageSeconds = 0
            baseHeartRate = 72
        }
    }

    // ============================================================
    // ✅ 视图控件
    // ============================================================
    private lateinit var tvHeartRate: TextView
    private lateinit var tvSpO2: TextView
    private lateinit var tvFatigue: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var isVisible = false
    private var updateRunnable: Runnable? = null
    private val simulator = HealthDataSimulator()

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.health_status_view, this, true)

        tvHeartRate = view.findViewById(R.id.tvHeartRate)
        tvSpO2 = view.findViewById(R.id.tvSpO2)
        tvFatigue = view.findViewById(R.id.tvFatigue)

        visibility = View.GONE
    }

    // ============================================================
    // ✅ 外部接口
    // ============================================================

    /**
     * 显示健康状态（开始实时更新）
     */
    fun show() {
        isVisible = true
        visibility = View.VISIBLE
        simulator.reset()

        updateRunnable = object : Runnable {
            override fun run() {
                if (!isVisible) return

                val fatigue = simulator.generateFatigueLevel()
                val heartRate = simulator.generateHeartRate(fatigue)
                val spO2 = simulator.generateSpO2()

                updateHealthData(heartRate, spO2, fatigue)

                if (fatigue > 70) {
                    showFatigueWarning()
                }

                handler.postDelayed(this, 3000)
            }
        }
        handler.post(updateRunnable!!)
    }

    /**
     * 隐藏健康状态
     */
    fun hide() {
        isVisible = false
        visibility = View.GONE
        updateRunnable?.let { handler.removeCallbacks(it) }
    }

    /**
     * 切换显示/隐藏
     */
    fun toggle() {
        if (isVisible) {
            hide()
        } else {
            show()
        }
    }

    /**
     * 获取当前疲劳度（用于语音查询）
     */
    fun getFatigueLevel(): Int {
        return simulator.generateFatigueLevel()
    }

    /**
     * 获取健康状态文字描述
     */
    fun getHealthStatusText(): String {
        val fatigue = simulator.generateFatigueLevel()
        return when {
            fatigue > 70 -> "您有点疲劳了，建议休息一下眼睛 👀"
            fatigue > 40 -> "您状态还可以，但别太累了哦 😊"
            else -> "检测到您的眼睛有过多红血丝，注意休息。"
        }
    }

    /**
     * 清理资源（防止内存泄漏）
     */
    fun destroy() {
        isVisible = false
        updateRunnable?.let { handler.removeCallbacks(it) }
    }

    // ============================================================
    // ✅ 私有方法
    // ============================================================

    private fun updateHealthData(heartRate: Int, spO2: Int, fatigue: Int) {
        tvHeartRate.text = "$heartRate bpm"
        tvSpO2.text = "$spO2%"
        tvFatigue.text = "$fatigue%"

        val color = when {
            fatigue > 70 -> 0xFFFF4444.toInt()
            fatigue > 40 -> 0xFFFFBB33.toInt()
            else -> 0xFF4CAF50.toInt()
        }
        tvFatigue.setTextColor(color)
    }

    private fun showFatigueWarning() {
        Toast.makeText(context, "😊 您已使用较长时间，建议休息一下眼睛", Toast.LENGTH_LONG).show()
    }
}