package com.indogaro.net.core

import android.content.Context
import android.content.Intent
import android.os.Build
import com.indogaro.net.contracts.Tun2SocksControl
import com.indogaro.net.dto.V2rayConfig
import com.indogaro.net.enums.RoutingType
import com.indogaro.net.root.RootManager
import com.indogaro.net.service.CoreVpnService
import com.indogaro.net.service.TProxyService
import com.indogaro.net.util.JsonUtil
import com.indogaro.net.util.LogUtil

// Compatibility extensions to ensure seamless integration with existing architecture
fun V2rayConfig.toJson(): String = JsonUtil.toJson(this)
fun RootManager.hasRoot(): Boolean = this.requestRoot()
fun CoreNativeManager.initCore(config: String): Boolean = this.setup(config)

object CoreServiceManager {
    const val ACTION_SERVICE_ERROR = "com.indogaro.net.SERVICE_ERROR"
    const val EXTRA_ERROR_MESSAGE = "error_message"

    fun startService(context: Context, config: V2rayConfig, routingType: RoutingType) {
        try {
            LogUtil.d("CoreServiceManager: Initializing core engine...")
            
            // Panggil CoreNativeManager.initCore SEBELUM service OS dijalankan
            val isCoreInitialized = CoreNativeManager.initCore(config.toJson())
            if (!isCoreInitialized) {
                LogUtil.e("CoreServiceManager: Core engine initialization failed.")
                broadcastError(context, "Core engine initialization failed.")
                return
            }

            when (routingType.name) {
                "TPROXY" -> {
                    if (RootManager.hasRoot()) {
                        LogUtil.d("CoreServiceManager: Root access granted. Starting TProxyService.")
                        val intent = Intent(context, TProxyService::class.java)
                        startServiceSafe(context, intent)
                    } else {
                        LogUtil.d("CoreServiceManager: Root access denied. Fallback to VPN routing.")
                        val intent = Intent(context, CoreVpnService::class.java)
                        startServiceSafe(context, intent)
                    }
                }
                "VPN" -> {
                    LogUtil.d("CoreServiceManager: Starting CoreVpnService.")
                    val intent = Intent(context, CoreVpnService::class.java)
                    startServiceSafe(context, intent)
                }
                else -> {
                    LogUtil.d("CoreServiceManager: Defaulting to CoreVpnService.")
                    val intent = Intent(context, CoreVpnService::class.java)
                    startServiceSafe(context, intent)
                }
            }
        } catch (e: Exception) {
            LogUtil.e("CoreServiceManager: Critical error during startService -> ${e.message}")
            broadcastError(context, e.message ?: "Unknown error occurred during startup.")
        }
    }

    fun stopAllServices(context: Context) {
        try {
            LogUtil.d("CoreServiceManager: Stopping all services and engines...")
            
            // Menghentikan CoreVpnService
            val vpnIntent = Intent(context, CoreVpnService::class.java).apply { action = "STOP" }
            try {
                context.startService(vpnIntent)
            } catch (e: Exception) {
                context.stopService(Intent(context, CoreVpnService::class.java))
            }
            
            // Menghentikan TProxyService
            val tproxyIntent = Intent(context, TProxyService::class.java).apply { action = "STOP" }
            try {
                context.startService(tproxyIntent)
            } catch (e: Exception) {
                context.stopService(Intent(context, TProxyService::class.java))
            }

            // Memanggil CoreNativeManager.stopCore() dan Tun2SocksControl.stop()
            CoreNativeManager.stopCore()
            Tun2SocksControl.stop()
            
            LogUtil.d("CoreServiceManager: All services stopped successfully.")
        } catch (e: Exception) {
            LogUtil.e("CoreServiceManager: Error during stopAllServices -> ${e.message}")
        }
    }

    private fun startServiceSafe(context: Context, intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: IllegalStateException) {
            LogUtil.e("CoreServiceManager: Foreground service start failed (IllegalStateException) -> ${e.message}")
            broadcastError(context, "Background execution limit reached. Please open the app to connect.")
        } catch (e: Exception) {
            LogUtil.e("CoreServiceManager: Failed to start service -> ${e.message}")
            broadcastError(context, e.message ?: "Failed to start service.")
        }
    }

    private fun broadcastError(context: Context, message: String) {
        val intent = Intent(ACTION_SERVICE_ERROR).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_ERROR_MESSAGE, message)
        }
        context.sendBroadcast(intent)
    }
}
