package com.indogaro.net.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class CoreVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupVpnInterface()
        return START_STICKY
    }

    fun setupVpnInterface() {
        vpnInterface = Builder()
            .setSession("JARGO_TUN")
            .setMtu(1500)
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .establish()
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
