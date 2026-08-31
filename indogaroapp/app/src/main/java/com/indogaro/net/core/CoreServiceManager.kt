package com.indogaro.net.core

import android.content.Context
import android.content.Intent
import com.indogaro.net.service.CoreVpnService

object CoreServiceManager {
    fun startCoreService(context: Context) {
        // Pemicu inisialisasi binary Golang
        CoreNativeManager.startCore()

        val intent = Intent(context, CoreVpnService::class.java)
        context.startService(intent)
    }
}
