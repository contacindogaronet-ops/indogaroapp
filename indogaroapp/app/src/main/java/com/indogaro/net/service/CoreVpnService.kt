package com.indogaro.net.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.indogaro.net.util.LogUtil
import com.indogaro.net.contracts.Tun2SocksControl
import com.indogaro.net.core.CoreNativeManager

class CoreVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.d("CoreVpnService: Booting up networking stack...")
        setupVpnInterface()
        return START_STICKY
    }

    private fun setupVpnInterface() {
        try {
            val builder = Builder()
            builder.setSession("JARGO-Core").setMtu(1500).addAddress("26.26.26.1", 24).addRoute("0.0.0.0", 0)
            
            vpnInterface = builder.establish()
            val fd = vpnInterface?.fd ?: throw Exception("Failed to establish tun0 interface")
            
            LogUtil.d("CoreVpnService: VPN Established (FD: $fd). Starting Tun2Socks...")
            
            // Injeksi FD ke mesin C (SOCKS5 di port 10808)
            val tunResult = Tun2SocksControl.start(fd, 1500, 10808)
            LogUtil.d("CoreVpnService: Tun2Socks status -> $tunResult")
            
        } catch (e: Exception) {
            LogUtil.e("CoreVpnService: Critical Setup Failure -> ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Tun2SocksControl.stop()
        CoreNativeManager.stopCore()
        vpnInterface?.close()
        vpnInterface = null
        LogUtil.d("CoreVpnService: Stack destroyed gracefully.")
    }
}
