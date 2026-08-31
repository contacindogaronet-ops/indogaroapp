package com.indogaro.net.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class CoreRootService : Service() {
    override fun onBind(i: Intent?): IBinder? = null
}
