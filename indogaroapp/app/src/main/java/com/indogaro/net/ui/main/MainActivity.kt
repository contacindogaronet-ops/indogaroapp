package com.indogaro.net.ui.main

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import com.indogaro.net.service.CoreVpnService
import com.indogaro.net.util.LogUtil

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogUtil.d("Checking VPN permission")

        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 24)
        } else {
            startVpnService()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 24 && resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    private fun startVpnService() {
        LogUtil.d("Starting CoreVpnService")
        startService(Intent(this, CoreVpnService::class.java))
    }
}
