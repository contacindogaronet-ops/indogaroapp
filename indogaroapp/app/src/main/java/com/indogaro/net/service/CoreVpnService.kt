package com.indogaro.net.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.indogaro.net.util.LogUtil

class CoreVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.d("Initializing CoreVpnService")
        setupVpnInterface()
        return START_STICKY
    }

    private fun setupVpnInterface() {
        try {
            val builder = Builder()
                .setSession("JARGO-Core")
                .setMtu(1500)
                .addAddress("26.26.26.1", 24)
                .addRoute("0.0.0.0", 0)

            vpnInterface = builder.establish()
            val fd = vpnInterface?.fd
            LogUtil.d("VPN interface established with fd: $fd")
        } catch (e: Exception) {
            LogUtil.e("Failed to setup VPN interface: ${e.message}")
        }
    }

    override fun onDestroy() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            LogUtil.e("Error closing VPN interface: ${e.message}")
        } finally {
            vpnInterface = null
            LogUtil.d("CoreVpnService destroyed")
            super.onDestroy()
        }
    }
}
