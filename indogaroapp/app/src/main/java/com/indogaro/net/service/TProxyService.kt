package com.indogaro.net.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.indogaro.net.core.CoreNativeManager

class TProxyService : Service() {
    private val TAG = "TProxyService"
    private val NOTIFICATION_ID = 1186
    private val CHANNEL_ID = "indogaro_tproxy_channel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Starting TProxyService (Root Mode)...")
        startForegroundNotification()
        
        if (enableTProxy()) {
            CoreNativeManager.startCore()
            broadcastState("CONNECTED")
        } else {
            Log.e(TAG, "Failed to inject TProxy iptables rules")
            broadcastState("STOPPED")
            stopSelf()
        }
        
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Indogaro TProxy Service",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        // MENGGUNAKAN NATIVE BUILDER 
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("Indogaro Root Network")
            .setContentText("TProxy Iptables active...")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun enableTProxy(): Boolean {
        return executeSu("iptables -t mangle -N INDOGARO_PROXY || true")
    }

    private fun disableTProxy() {
        executeSu("iptables -t mangle -F INDOGARO_PROXY || true")
    }

    private fun executeSu(cmd: String): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", cmd).start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root execution failed for cmd: $cmd", e)
            false
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Destroying TProxyService...")
        disableTProxy()
        CoreNativeManager.stopCore()
        broadcastState("STOPPED")
        super.onDestroy()
    }
    
    private fun broadcastState(state: String) {
        val intent = Intent("com.indogaro.net.VPN_STATE")
        intent.putExtra("state", state)
        sendBroadcast(intent)
    }
}
