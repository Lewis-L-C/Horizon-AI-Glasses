package com.blue.glassesapp.feature.home.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 权限管理助手
 */
object PermissionHelper {

    const val REQUEST_CODE = 100

    fun getRequiredPermissions(): List<String> {
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

    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getDeniedPermissions(context: Context): List<String> {
        return getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions(activity: AppCompatActivity) {
        ActivityCompat.requestPermissions(
            activity,
            getRequiredPermissions().toTypedArray(),
            REQUEST_CODE
        )
    }

    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "📷 相机"
            Manifest.permission.RECORD_AUDIO -> "🎤 录音"
            Manifest.permission.ACCESS_FINE_LOCATION -> "📍 精确定位"
            Manifest.permission.ACCESS_COARSE_LOCATION -> "📍 粗略定位"
            Manifest.permission.READ_MEDIA_IMAGES -> "🖼️ 读取图片"
            Manifest.permission.READ_MEDIA_VIDEO -> "🎬 读取视频"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "💾 写入存储"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "📂 读取存储"
            else -> permission
        }
    }
}