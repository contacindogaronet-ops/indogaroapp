package com.indogaro.net.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.indogaro.net.R
import com.indogaro.net.service.CoreVpnService

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnConnect: Button
    private var isVpnRunning = false

    // Peluncur modern pengganti onActivityResult untuk minta izin VPN
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "Izin VPN Ditolak!", Toast.LENGTH_SHORT).show()
        }
    }

    // Pendengar status langsung dari CoreVpnService
    private val vpnStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra("state") ?: "STOPPED"
            updateUiState(state)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnConnect = findViewById(R.id.btnConnect)

        btnConnect.setOnClickListener {
            if (isVpnRunning) {
                stopVpnService()
            } else {
                prepareVpn()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.indogaro.net.VPN_STATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vpnStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(vpnStateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(vpnStateReceiver)
    }

    private fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Butuh izin OS
            vpnPermissionLauncher.launch(intent)
        } else {
            // Udah dapet izin, langsung gas
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, CoreVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopVpnService() {
        val intent = Intent(this, CoreVpnService::class.java)
        stopService(intent)
    }

    private fun updateUiState(state: String) {
        when (state) {
            "CONNECTED" -> {
                isVpnRunning = true
                tvStatus.text = "STATUS: CONNECTED"
                tvStatus.setTextColor(Color.parseColor("#00C853")) // Hijau
                btnConnect.text = "STOP VPN"
                btnConnect.setBackgroundColor(Color.parseColor("#D50000")) // Merah
                btnConnect.isEnabled = true
            }
            "CONNECTING" -> {
                tvStatus.text = "STATUS: CONNECTING..."
                tvStatus.setTextColor(Color.parseColor("#FFAB00")) // Kuning
                btnConnect.text = "PLEASE WAIT"
                btnConnect.isEnabled = false
            }
            else -> { // STOPPED
                isVpnRunning = false
                tvStatus.text = "STATUS: DISCONNECTED"
                tvStatus.setTextColor(Color.parseColor("#FF5252")) // Merah
                btnConnect.text = "START VPN"
                btnConnect.setBackgroundColor(Color.parseColor("#00C853")) // Hijau
                btnConnect.isEnabled = true
            }
        }
    }
}
