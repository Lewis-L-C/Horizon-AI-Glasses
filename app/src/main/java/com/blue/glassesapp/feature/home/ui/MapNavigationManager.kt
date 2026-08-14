package com.blue.glassesapp.feature.home.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.amap.api.services.route.BusRouteResult
import com.amap.api.services.route.DriveRouteResult
import com.amap.api.services.route.RideRouteResult
import com.amap.api.services.route.RouteSearch
import com.amap.api.services.route.WalkRouteResult
import kotlinx.coroutines.*

/**
 * 地图导航管理器 - 管理地图显示、定位、POI搜索和路径规划
 */
class MapNavigationManager(
    private val activity: AppCompatActivity,
    private val mapView: MapView,
    private val mapContainer: FrameLayout,
    private val onShowBottomReply: (String, Boolean) -> Unit
) {

    companion object {
        private const val TAG = "MapNavigationManager"
    }

    private lateinit var aMap: AMap
    private var isMapVisible = false
    private var isMapInitialized = false
    private var isNavigating = false
    private var destinationLatLng: LatLng? = null
    private lateinit var routeSearch: RouteSearch

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // ✅ 3D导航状态
    private var is3DNavigation = false

    // ============================================================
    // ✅ 地图初始化
    // ============================================================
    fun initMap(savedInstanceState: Bundle?) {
        Log.e(TAG, "🔥🔥🔥 initMap 被调用")
        try {
            // 隐私合规
            try {
                AMapLocationClient.updatePrivacyShow(activity, true, true)
                AMapLocationClient.updatePrivacyAgree(activity, true)
                MapsInitializer.updatePrivacyShow(activity, true, true)
                MapsInitializer.updatePrivacyAgree(activity, true)
                Log.e(TAG, "✅ 隐私合规设置成功")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 隐私合规设置失败: ${e.message}")
            }

            // ✅ 直接使用 mapView，不需要 :: 判断
            mapView.onCreate(savedInstanceState)
            aMap = mapView.map
            Log.e(TAG, "✅ aMap 对象获取成功")

            aMap.mapType = AMap.MAP_TYPE_NORMAL
            aMap.uiSettings.isZoomControlsEnabled = false
            aMap.uiSettings.isScaleControlsEnabled = false
            aMap.setOnMyLocationChangeListener { location ->
                if (location != null) {
                    Log.e(TAG, "📍 定位成功: lat=${location.latitude}, lng=${location.longitude}")
                }
            }

            val myLocationStyle = MyLocationStyle()
            myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE)
            myLocationStyle.showMyLocation(true)
            aMap.myLocationStyle = myLocationStyle
            aMap.isMyLocationEnabled = true
            Log.e(TAG, "✅ 定位已启用")

            aMap.moveCamera(CameraUpdateFactory.zoomTo(18f))

            isMapInitialized = true
            Log.e(TAG, "✅ 地图初始化成功")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 地图初始化失败: ${e.message}")
            e.printStackTrace()
        }
    }

    // ✅ 初始化搜索
    fun initSearch() {
        routeSearch = RouteSearch(activity)
        routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
            override fun onBusRouteSearched(p0: BusRouteResult?, p1: Int) {}
            override fun onDriveRouteSearched(result: DriveRouteResult?, code: Int) {
                if (code == 1000 && result != null && result.paths != null && result.paths.isNotEmpty()) {
                    val path = result.paths[0]
                    val distance = path.distance
                    val duration = path.duration

                    val allPoints = mutableListOf<LatLng>()
                    val steps = path.steps
                    if (steps != null) {
                        for (step in steps) {
                            try {
                                val polyline = step.polyline
                                if (polyline != null) {
                                    for (point in polyline) {
                                        allPoints.add(LatLng(point.latitude, point.longitude))
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "获取 step 点失败: ${e.message}")
                            }
                        }
                    }

                    activity.runOnUiThread {
                        val myLocationStyle = MyLocationStyle()
                        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE)
                        myLocationStyle.showMyLocation(true)
                        aMap.myLocationStyle = myLocationStyle
                        aMap.isMyLocationEnabled = true

                        destinationLatLng?.let {
                            aMap.addMarker(
                                MarkerOptions()
                                    .position(it)
                                    .title("目的地")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                            )
                        }

                        if (allPoints.isNotEmpty()) {
                            aMap.addPolyline(
                                PolylineOptions()
                                    .addAll(allPoints)
                                    .width(12f)
                                    .color(0xFF2196F3.toInt())
                            )

                            try {
                                val boundsBuilder = LatLngBounds.Builder()
                                for (point in allPoints) {
                                    boundsBuilder.include(point)
                                }
                                val startLocation = aMap.myLocation
                                if (startLocation != null) {
                                    boundsBuilder.include(LatLng(startLocation.latitude, startLocation.longitude))
                                }
                                val bounds = boundsBuilder.build()
                                aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50))
                            } catch (e: Exception) {
                                Log.e(TAG, "缩放失败: ${e.message}")
                            }
                        }

                        val timeStr = if (duration > 60) "${duration / 60}分钟" else "${duration}秒"
                        onShowBottomReply("🗺️ 路线已规划，距离 ${distance}米，预计 $timeStr", false)
                        isNavigating = true
                        Log.e(TAG, "✅ 路线规划成功，距离: ${distance}米，路径点数量: ${allPoints.size}")
                    }
                } else {
                    activity.runOnUiThread {
                        onShowBottomReply("❌ 路线规划失败", false)
                        Log.e(TAG, "❌ 路线规划失败 code: $code")
                    }
                }
            }

            override fun onWalkRouteSearched(p0: WalkRouteResult?, p1: Int) {}
            override fun onRideRouteSearched(p0: RideRouteResult?, p1: Int) {}
        })
    }

    // ✅ POI 搜索 + 导航
    fun searchAndNavigate(keyword: String) {
        onShowBottomReply("🤔 正在理解你的意思...", false)
        Log.e(TAG, "🔍 原始指令: $keyword")

        val currentLocation = aMap.myLocation
        if (currentLocation == null) {
            onShowBottomReply("❌ 无法获取当前位置，请检查定位", false)
            Log.e(TAG, "❌ 当前位置为空")
            return
        }

        val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
        Log.e(TAG, "📍 当前位置: ${currentLocation.latitude}, ${currentLocation.longitude}")

        // 用 DeepSeek 解析目的地
        scope.launch(Dispatchers.IO) {
            try {
                val prompt = """
                    用户说：$keyword
                    请提取出用户想要搜索的地点名称，只输出地点名称，不要输出任何其他内容。
                    如果用户说"最近的肯德基"或"肯德基"，输出"肯德基"
                    如果用户说"最近的加油站"，输出"加油站"
                    如果用户说"西湖"，输出"西湖"
                    如果用户说"附近的便利店"，输出"便利店"
                    只输出地点名称，不要输出其他任何内容。
                """.trimIndent()

                val reply = DeepSeekChat.sendSimpleMessage(prompt)
                Log.e(TAG, "🤖 AI 解析结果: $reply")

                val searchKeyword = if (reply != null && reply.isNotEmpty()) {
                    reply.trim()
                } else {
                    keyword
                }

                Log.e(TAG, "🔍 最终搜索关键词: $searchKeyword")

                withContext(Dispatchers.Main) {
                    performPoiSearch(searchKeyword, currentLatLng)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ AI 解析失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    onShowBottomReply("❌ 请说想去哪里，如'导航到肯德基'", false)
                }
            }
        }
    }

    private fun performPoiSearch(searchKeyword: String, currentLatLng: LatLng) {
        onShowBottomReply("🔍 搜索 $searchKeyword...", false)
        Log.e(TAG, "🔍 搜索关键词: $searchKeyword")

        val query = PoiSearch.Query(searchKeyword, "", "")
        query.pageSize = 10

        val poiSearch = PoiSearch(activity, query)
        poiSearch.setBound(
            PoiSearch.SearchBound(
                LatLonPoint(currentLatLng.latitude, currentLatLng.longitude),
                5000,
                true
            )
        )

        poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
            override fun onPoiSearched(result: PoiResult?, code: Int) {
                Log.e(TAG, "🔍 POI 搜索: code=$code")
                if (code == 1000 && result != null) {
                    val pois = result.getPois()
                    if (pois != null && pois.isNotEmpty()) {
                        val poi = pois[0]
                        val destLatLng = LatLng(poi.getLatLonPoint().latitude, poi.getLatLonPoint().longitude)
                        destinationLatLng = destLatLng
                        Log.e(TAG, "📍 找到目的地: ${poi.title}")

                        activity.runOnUiThread {
                            aMap.addMarker(
                                MarkerOptions()
                                    .position(destLatLng)
                                    .title(poi.title)
                                    .snippet(poi.getSnippet())
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                            )

                            // ✅ 如果启用了3D导航，切换到3D视角
                            if (is3DNavigation) {
                                apply3DNavigationView()
                            } else {
                                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(destLatLng, 15f))
                            }

                            onShowBottomReply("📍 找到: ${poi.title}，正在规划路线...", false)
                        }

                        planRoute(currentLatLng, destLatLng)
                    } else {
                        activity.runOnUiThread {
                            onShowBottomReply("❌ 附近未找到 $searchKeyword，请尝试其他关键词", false)
                            Log.e(TAG, "❌ POI 搜索结果为空")
                        }
                    }
                } else {
                    activity.runOnUiThread {
                        onShowBottomReply("❌ POI 搜索失败，请稍后重试", false)
                        Log.e(TAG, "❌ POI 搜索失败 code: $code")
                    }
                }
            }

            override fun onPoiItemSearched(p0: PoiItem?, p1: Int) {}
        })
        poiSearch.searchPOIAsyn()
    }

    private fun planRoute(start: LatLng, end: LatLng) {
        val from = LatLonPoint(start.latitude, start.longitude)
        val to = LatLonPoint(end.latitude, end.longitude)
        val query = RouteSearch.DriveRouteQuery(
            RouteSearch.FromAndTo(from, to),
            RouteSearch.DRIVING_SINGLE_DEFAULT,
            null,
            null,
            ""
        )
        routeSearch.calculateDriveRouteAsyn(query)
        Log.e(TAG, "🚗 开始规划路线")
    }

    // ============================================================
    // ✅ 3D 导航控制
    // ============================================================

    /**
     * 启用/禁用 3D 导航
     */
    fun enable3DNavigation(enable: Boolean) {
        is3DNavigation = enable
        Log.e(TAG, "🗺️ 3D导航模式: ${if (enable) "开启" else "关闭"}")
    }

    /**
     * 切换到 3D 导航视角
     */
    fun apply3DNavigationView() {
        if (!::aMap.isInitialized) {
            Log.e(TAG, "❌ aMap 未初始化")
            return
        }

        val location = aMap.myLocation
        if (location == null) {
            Log.e(TAG, "⚠️ 当前位置为空，无法切换3D视角")
            return
        }

        val target = LatLng(location.latitude, location.longitude)
        val cameraPosition = CameraPosition.Builder()
            .target(target)
            .zoom(18f)       // 缩放级别
            .tilt(45f)       // 倾斜角度（0=俯视，45=3D效果）
            .bearing(0f)     // 朝向角度
            .build()

        aMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 800, null)
        Log.e(TAG, "🗺️ 已切换到 3D 导航视角")
    }

    /**
     * 切换到 2D 俯视图
     */
    fun apply2DView() {
        if (!::aMap.isInitialized) {
            Log.e(TAG, "❌ aMap 未初始化")
            return
        }

        val location = aMap.myLocation
        if (location == null) {
            Log.e(TAG, "⚠️ 当前位置为空")
            return
        }

        val target = LatLng(location.latitude, location.longitude)
        val cameraPosition = CameraPosition.Builder()
            .target(target)
            .zoom(16f)
            .tilt(0f)        // 0度 = 俯视
            .bearing(0f)
            .build()

        aMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 500, null)
        is3DNavigation = false
        Log.e(TAG, "🗺️ 已切换到 2D 俯视图")
    }

    // ============================================================
    // ✅ 地图显示/隐藏
    // ============================================================
    fun showMap() {
        activity.runOnUiThread {
            try {
                if (!::aMap.isInitialized || !isMapInitialized) {
                    Log.e(TAG, "❌ aMap 未初始化，尝试重新初始化")
                    initMap(null)
                    if (!::aMap.isInitialized) {
                        onShowBottomReply("❌ 地图加载失败，请检查网络和权限", false)
                        return@runOnUiThread
                    }
                }
                mapContainer.visibility = View.VISIBLE
                isMapVisible = true

                val location = aMap.myLocation
                if (location == null) {
                    Log.e(TAG, "⚠️ 当前位置为空，使用默认位置（杭州西湖）")
                    val defaultLatLng = LatLng(30.2741, 120.1551)
                    aMap.addMarker(
                        MarkerOptions()
                            .position(defaultLatLng)
                            .title("当前位置（模拟）")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    )
                    aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 18f))
                    onShowBottomReply("📍 显示默认位置（定位中...）", false)
                } else {
                    Log.e(TAG, "✅ 定位成功: ${location.latitude}, ${location.longitude}")
                    // ✅ 根据模式选择视角
                    if (is3DNavigation) {
                        apply3DNavigationView()
                        onShowBottomReply("🗺️ 3D导航模式已开启", false)
                    } else {
                        apply2DView()
                        onShowBottomReply("📍 显示当前位置", false)
                    }
                }
                Log.e(TAG, "✅ 地图已显示")
            } catch (e: Exception) {
                Log.e(TAG, "❌ showMap 异常: ${e.message}")
                onShowBottomReply("❌ 地图加载失败", false)
            }
        }
    }

    fun hideMap() {
        activity.runOnUiThread {
            mapContainer.visibility = View.GONE
            isMapVisible = false
            isNavigating = false
            destinationLatLng = null
            is3DNavigation = false
            onShowBottomReply("🗺️ 地图已关闭", false)
        }
    }

    fun isMapVisible(): Boolean = isMapVisible
    fun isMapInitialized(): Boolean = isMapInitialized
    fun isAMapReady(): Boolean = ::aMap.isInitialized

    fun zoomIn() {
        if (isMapVisible && ::aMap.isInitialized) {
            aMap.animateCamera(CameraUpdateFactory.zoomIn())
            onShowBottomReply("🔍 已放大", false)
        } else {
            onShowBottomReply("❌ 请先打开地图", false)
        }
    }

    fun zoomOut() {
        if (isMapVisible && ::aMap.isInitialized) {
            aMap.animateCamera(CameraUpdateFactory.zoomOut())
            onShowBottomReply("🔍 已缩小", false)
        } else {
            onShowBottomReply("❌ 请先打开地图", false)
        }
    }

    fun cancelNavigation() {
        isNavigating = false
        destinationLatLng = null
        is3DNavigation = false
        aMap.clear()
        val myLocationStyle = MyLocationStyle()
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE)
        myLocationStyle.showMyLocation(true)
        aMap.myLocationStyle = myLocationStyle
        aMap.isMyLocationEnabled = true
        onShowBottomReply("🗺️ 已取消导航", false)
    }

    // ============================================================
    // ✅ 生命周期
    // ============================================================
    fun onResume() {
        mapView.onResume()
    }

    fun onPause() {
        mapView.onPause()
    }

    fun onSaveInstanceState(outState: Bundle) {
        mapView.onSaveInstanceState(outState)
    }

    fun onDestroy() {
        mapView.onDestroy()
        scope.cancel()
    }
}