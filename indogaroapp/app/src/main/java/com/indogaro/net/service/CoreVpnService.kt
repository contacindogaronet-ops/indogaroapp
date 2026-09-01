package com.indogaro.net.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.indogaro.net.contracts.Tun2SocksControl
import com.indogaro.net.core.CoreNativeManager

class CoreVpnService : VpnService() {
    private val TAG = "CoreVpnService"
    private val NOTIFICATION_ID = 1185
    private val CHANNEL_ID = "indogaro_vpn_channel"
    
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Starting CoreVpnService...")
        startForegroundNotification()
        setupVpn()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Indogaro VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        // MENGGUNAKAN NATIVE BUILDER (Bebas AndroidX / Anti Unresolved Reference)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("Indogaro Network")
            .setContentText("VPN Service is routing traffic...")
            .setSmallIcon(android.R.drawable.ic_secure) // Default icon for safety
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupVpn() {
        broadcastState("CONNECTING")
        try {
            val builder = Builder()
            builder.setMtu(1500)
            builder.addAddress("172.19.0.2", 24) // Dummy local IP
            
            // Route All IPv4 & IPv6
            builder.addRoute("0.0.0.0", 0)
            builder.addRoute("::", 0)
            
            // Bypass app itself to prevent routing loop
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to exclude package", e)
            }
            
            builder.setBlocking(false)
            builder.setSession("IndogaroVPN")

            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                val fd = vpnInterface!!.fd
                Log.i(TAG, "VPN Interface established with FD: $fd")
                
                // Execute Native Engine
                CoreNativeManager.startCore()
                Tun2SocksControl.start(fd, 1500, 10808)
                
                broadcastState("CONNECTED")
            } else {
                throw IllegalStateException("Failed to establish VPN interface (null)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal Error configuring VPN", e)
            broadcastState("STOPPED")
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Destroying CoreVpnService...")
        Tun2SocksControl.stop()
        CoreNativeManager.stopCore()
        
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        
        broadcastState("STOPPED")
        super.onDestroy()
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN Permission revoked by OS")
        stopSelf()
        super.onRevoke()
    }

    private fun broadcastState(state: String) {
        val intent = Intent("com.indogaro.net.VPN_STATE")
        intent.putExtra("state", state)
        sendBroadcast(intent)
    }
}
