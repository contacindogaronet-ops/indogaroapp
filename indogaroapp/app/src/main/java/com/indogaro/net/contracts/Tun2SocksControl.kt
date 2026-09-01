package com.indogaro.net.contracts

import com.indogaro.net.util.LogUtil

object Tun2SocksControl {
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
            isNativeLoaded = true
        } catch (e: Throwable) {
            LogUtil.e("Tun2SocksControl: Failed to load native library 'hev-socks5-tunnel'. Error: ${e.message}")
        }
    }

    private external fun nativeStart(fd: Int, mtu: Int, socksPort: Int): Boolean
    private external fun nativeStop(): Boolean

    fun start(fd: Int, mtu: Int, socksPort: Int): Boolean {
        if (!isNativeLoaded) {
            LogUtil.e("Native lib not loaded, bypassing start...")
            return false
        }
        return nativeStart(fd, mtu, socksPort)
    }

    fun stop(): Boolean {
        if (!isNativeLoaded) {
            LogUtil.e("Native lib not loaded, bypassing stop...")
            return false
        }
        return nativeStop()
    }
}
