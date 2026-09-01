package com.indogaro.net.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.indogaro.net.core.CoreNativeManager
import com.indogaro.net.root.RootManager
import com.indogaro.net.root.RootShell
import com.indogaro.net.util.LogUtil
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class TProxyService : Service() {

    companion object {
        const val ACTION_TPROXY_STATE = "com.indogaro.net.VPN_STATE" // Reusing VPN state action for UI compatibility
        const val EXTRA_STATE = "state"
        
        const val STATE_CONNECTING = "CONNECTING"
        const val STATE_CONNECTED = "CONNECTED"
        const val STATE_STOPPED = "STOPPED"
        
        const val NOTIFICATION_ID = 102
        const val CHANNEL_ID = "jargo_tproxy_channel"
        const val TPROXY_PORT = 10808
    }

    private val isRunning = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LogUtil.d("TProxyService: onCreate initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.d("TProxyService: onStartCommand triggered")
        
        if (intent?.action == "STOP") {
            stopTProxyService()
            return START_NOT_STICKY
        }

        if (isRunning.compareAndSet(false, true)) {
            startForeground(NOTIFICATION_ID, createNotification("Starting TProxy Engine..."))
            broadcastState(STATE_CONNECTING)
            
            thread(start = true, name = "TProxySetupThread") {
                setupTProxy()
            }
        }
        
        return START_STICKY
    }

    private fun setupTProxy() {
        try {
            if (!RootManager.requestRoot()) {
                throw SecurityException("Root access denied or su binary not found.")
            }

            LogUtil.d("TProxyService: Root access granted. Starting Core Engine...")
            val coreStarted = CoreNativeManager.startCore()
            if (!coreStarted) {
                throw IllegalStateException("Golang Core Engine failed to start")
            }

            LogUtil.d("TProxyService: Core started. Applying iptables rules...")
            enableTProxy()

            LogUtil.d("TProxyService: TProxy routing established successfully.")
            updateNotification("TProxy Connected")
            broadcastState(STATE_CONNECTED)

        } catch (e: Exception) {
            LogUtil.e("TProxyService: Critical Setup Failure -> ${e.message}")
            broadcastError(e.message ?: "Unknown error during TProxy setup")
            stopTProxyService()
        }
    }

    private fun enableTProxy() {
        val enableCmds = """
            iptables -t mangle -N INDOGARO_TPROXY
            iptables -t mangle -F INDOGARO_TPROXY
            iptables -t mangle -A INDOGARO_TPROXY -d 0.0.0.0/8 -j RETURN
            iptables -t mangle -A INDOGARO_TPROXY -d 127.0.0.0/8 -j RETURN
            iptables -t mangle -A INDOGARO_TPROXY -d 169.254.0.0/16 -j RETURN
            iptables -t mangle -A INDOGARO_TPROXY -d 224.0.0.0/4 -j RETURN
            iptables -t mangle -A INDOGARO_TPROXY -d 240.0.0.0/4 -j RETURN
            iptables -t mangle -A INDOGARO_TPROXY -p tcp -j TPROXY --on-port $TPROXY_PORT --tproxy-mark 1
            iptables -t mangle -A INDOGARO_TPROXY -p udp -j TPROXY --on-port $TPROXY_PORT --tproxy-mark 1
            iptables -t mangle -A PREROUTING -j INDOGARO_TPROXY
            ip rule add fwmark 1 lookup 100
            ip route add local 0.0.0.0/0 dev lo table 100
        """.trimIndent()

        val script = enableCmds.split("\n").joinToString(" ; ")
        val result = RootShell.exec(script)
        LogUtil.d("TProxyService: enableTProxy result -> $result")
    }

    private fun disableTProxy() {
        val disableCmds = """
            iptables -t mangle -D PREROUTING -j INDOGARO_TPROXY
            iptables -t mangle -F INDOGARO_TPROXY
            iptables -t mangle -X INDOGARO_TPROXY
            ip rule del fwmark 1 lookup 100
            ip route del local 0.0.0.0/0 dev lo table 100
        """.trimIndent()

        val script = disableCmds.split("\n").joinToString(" ; ")
        val result = RootShell.exec(script)
        LogUtil.d("TProxyService: disableTProxy result -> $result")
    }

    private fun stopTProxyService() {
        if (!isRunning.get()) return
        LogUtil.d("TProxyService: Stopping TProxy Service...")
        
        try {
            disableTProxy()
            CoreNativeManager.stopCore()
        } catch (e: Exception) {
            LogUtil.e("TProxyService: Error during teardown -> ${e.message}")
        } finally {
            isRunning.set(false)
            broadcastState(STATE_STOPPED)
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        LogUtil.d("TProxyService: onDestroy")
        stopTProxyService()
        super.onDestroy()
    }

    private fun broadcastState(state: String) {
        val intent = Intent(ACTION_TPROXY_STATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATE, state)
        }
        sendBroadcast(intent)
    }

    private fun broadcastError(message: String) {
        val intent = Intent("com.indogaro.net.SERVICE_ERROR").apply {
            setPackage(packageName)
            putExtra("error_message", message)
        }
        sendBroadcast(intent)
    }

    private fun createNotification(contentText: String): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARGO TProxy Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for Root TProxy connection"
            }
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Indogaro TProxy (Root)")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
    }
}
