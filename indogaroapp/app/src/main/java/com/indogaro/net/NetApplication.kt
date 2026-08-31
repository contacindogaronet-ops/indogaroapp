package com.indogaro.net

import android.app.Application
import com.indogaro.net.util.LogUtil

class NetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO: Initialize Dual-Pool Memory (32KB Regular / 4MB VVIP) in Golang
        LogUtil.d("NetApplication: Process started. Zero-Alloc protocols engaged.")
    }
}
