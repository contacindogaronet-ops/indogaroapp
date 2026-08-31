package com.indogaro.net

import android.app.Application

class NetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO: Initialize Dual-Pool Memory (32KB Regular / 4MB VVIP) & Logger
    }
}
