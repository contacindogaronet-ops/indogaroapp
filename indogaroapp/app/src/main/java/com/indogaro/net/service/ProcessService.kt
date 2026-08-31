package com.indogaro.net.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ProcessService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO: Start Foreground Notification to prevent Doze Mode kill.
        return START_STICKY
    }
}
