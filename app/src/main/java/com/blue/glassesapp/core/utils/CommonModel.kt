package com.blue.glassesapp.core.utils

import androidx.databinding.ObservableField
import com.blue.armobile.BuildConfig
import com.blue.glassesapp.common.model.bindmodel.GlassesInfoModel

/**
 * @Description TODO
 *
 *
 * @Author liux
 * @Date 2022/11/24 16:13
 * @Version 1.0
 */
object CommonModel {
    /**
     * 密钥（由 local.properties 通过 BuildConfig 注入，占位符表示未配置）
     */
    val CLIENT_SECRET: String = BuildConfig.ROKID_CLIENT_SECRET

    /**
     * 眼镜信息
     */
    var glassesInfo: GlassesInfoModel = GlassesInfoModel()


    /**
     * 是否播放语音
     */
    var useTTS = false

    var glassesLinkErrorMsg: String = ""


    var deviceSocketUuid: String = ""
    var deviceMacAddress: String = ""
    var deviceDeviceName: String = ""
        set(value) {
            field = value
            glassesInfo.name = value
        }


    var useCustomView = false

    /**
     * 是否是在蓝牙配对页面
     */
    var isInBluetoothPairingPage = false


}