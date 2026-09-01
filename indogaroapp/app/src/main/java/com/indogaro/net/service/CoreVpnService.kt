package com.indogaro.net.service
import androidx.core.app.NotificationCompat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.indogaro.net.contracts.Tun2SocksControl
import com.indogaro.net.core.CoreNativeManager
import com.indogaro.net.util.LogUtil
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class CoreVpnService : VpnService() {

    companion object {
        const val ACTION_VPN_STATE = "com.indogaro.net.VPN_STATE"
        const val EXTRA_STATE = "state"
        
        const val STATE_CONNECTING = "CONNECTING"
        const val STATE_CONNECTED = "CONNECTED"
        const val STATE_STOPPED = "STOPPED"
        
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "jargo_vpn_channel"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        LogUtil.d("CoreVpnService: onCreate initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.d("CoreVpnService: onStartCommand triggered")
        
        if (intent?.action == "STOP") {
            stopVpnService()
            return START_NOT_STICKY
        }

        if (isRunning.compareAndSet(false, true)) {
            startForeground(NOTIFICATION_ID, createNotification("Connecting..."))
            broadcastState(STATE_CONNECTING)
            
            thread(start = true, name = "VpnSetupThread") {
                setupVpnInterface()
            }
        }
        
        return START_STICKY
    }

    private fun setupVpnInterface() {
        try {
            val builder = Builder()
                .setSession("JARGO-Core")
                .setMtu(1500)
                .addAddress("26.26.26.1", 24)
                .addAddress("fdfe:dcba:9876::1", 126)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .setBlocking(false)

            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                LogUtil.e("CoreVpnService: Failed to exclude own package - ${e.message}")
            }

            vpnInterface = builder.establish()
            val fd = vpnInterface?.fd ?: throw IllegalStateException("Failed to establish VPN interface (fd is null)")

            LogUtil.d("CoreVpnService: VPN Interface established. FD: $fd")

            val coreStarted = CoreNativeManager.startCore()
            if (!coreStarted) {
                throw IllegalStateException("Golang Core Engine failed to start")
            }

            val tunStarted = Tun2SocksControl.start(fd, 1500, 10808)
            if (!tunStarted) {
                throw IllegalStateException("Tun2Socks Engine failed to start")
            }

            LogUtil.d("CoreVpnService: All engines started successfully")
            updateNotification("Connected")
            broadcastState(STATE_CONNECTED)

        } catch (e: Exception) {
            LogUtil.e("CoreVpnService: Critical Setup Failure -> ${e.message}")
            stopVpnService()
        }
    }

    private fun stopVpnService() {
        if (!isRunning.get()) return
        LogUtil.d("CoreVpnService: Stopping VPN Service...")
        
        try {
            Tun2SocksControl.stop()
            CoreNativeManager.stopCore()
            
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            LogUtil.e("CoreVpnService: Error during teardown -> ${e.message}")
        } finally {
            isRunning.set(false)
            broadcastState(STATE_STOPPED)
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onRevoke() {
        LogUtil.d("CoreVpnService: onRevoke called by OS")
        stopVpnService()
        super.onRevoke()
    }

    override fun onDestroy() {
        LogUtil.d("CoreVpnService: onDestroy")
        stopVpnService()
        super.onDestroy()
    }

    private fun broadcastState(state: String) {
        val intent = Intent(ACTION_VPN_STATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATE, state)
        }
        sendBroadcast(intent)
    }

    private fun createNotification(contentText: String): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARGO VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for VPN connection"
            }
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Indogaro VPN")
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
