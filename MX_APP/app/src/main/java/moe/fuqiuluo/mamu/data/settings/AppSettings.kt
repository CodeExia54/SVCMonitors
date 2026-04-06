package moe.fuqiuluo.mamu.data.settings

import com.tencent.mmkv.MMKV

/**
 * 应用主配置 - 基于 MMKV 的扩展属性
 */

private const val KEY_AUTO_START_FLOATING = "auto_start_floating_window"
private const val KEY_KEEP_FLOATING_ALIVE = "keep_floating_service_alive"
private const val KEY_SVC_WS_ENABLED = "svc_ws_enabled"
private const val KEY_SVC_WS_URL = "svc_ws_url"
private const val KEY_SVC_WS_DEVICE_TAG = "svc_ws_device_tag"

private const val DEFAULT_AUTO_START_FLOATING = false
private const val DEFAULT_KEEP_FLOATING_ALIVE = true
private const val DEFAULT_SVC_WS_ENABLED = false
private const val DEFAULT_SVC_WS_URL = "ws://127.0.0.1:8080/ws"
private const val DEFAULT_SVC_WS_DEVICE_TAG = "mx-device"

/**
 * 应用启动时自动显示悬浮窗
 */
var MMKV.autoStartFloatingWindow: Boolean
    get() = decodeBool(KEY_AUTO_START_FLOATING, DEFAULT_AUTO_START_FLOATING)
    set(value) {
        encode(KEY_AUTO_START_FLOATING, value)
    }

/**
 * 当任务被系统移除时，自动尝试重拉起悬浮服务。
 * 适用于需要更稳定后台驻留的场景。
 */
var MMKV.keepFloatingServiceAlive: Boolean
    get() = decodeBool(KEY_KEEP_FLOATING_ALIVE, DEFAULT_KEEP_FLOATING_ALIVE)
    set(value) {
        encode(KEY_KEEP_FLOATING_ALIVE, value)
    }

var MMKV.svcWsEnabled: Boolean
    get() = decodeBool(KEY_SVC_WS_ENABLED, DEFAULT_SVC_WS_ENABLED)
    set(value) {
        encode(KEY_SVC_WS_ENABLED, value)
    }

var MMKV.svcWsUrl: String
    get() = decodeString(KEY_SVC_WS_URL, DEFAULT_SVC_WS_URL) ?: DEFAULT_SVC_WS_URL
    set(value) {
        encode(KEY_SVC_WS_URL, value.trim())
    }

var MMKV.svcWsDeviceTag: String
    get() = decodeString(KEY_SVC_WS_DEVICE_TAG, DEFAULT_SVC_WS_DEVICE_TAG) ?: DEFAULT_SVC_WS_DEVICE_TAG
    set(value) {
        encode(KEY_SVC_WS_DEVICE_TAG, value.trim())
    }
