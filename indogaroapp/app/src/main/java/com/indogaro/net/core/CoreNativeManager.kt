package com.indogaro.net.core

import com.indogaro.net.util.LogUtil

object CoreNativeManager {
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("v2ray")
            isNativeLoaded = true
        } catch (e: Throwable) {
            LogUtil.e("CoreNativeManager: Failed to load native library 'v2ray'. Error: ${e.message}")
        }
    }

    private external fun nativeSetup(configPath: String): Boolean
    private external fun nativeStartCore(): Boolean
    private external fun nativeStopCore(): Boolean

    fun setup(configPath: String): Boolean {
        if (!isNativeLoaded) {
            LogUtil.e("Native lib not loaded, bypassing setup...")
            return false
        }
        return nativeSetup(configPath)
    }

    fun startCore(): Boolean {
        if (!isNativeLoaded) {
            LogUtil.e("Native lib not loaded, bypassing startCore...")
            return false
        }
        return nativeStartCore()
    }

    fun stopCore(): Boolean {
        if (!isNativeLoaded) {
            LogUtil.e("Native lib not loaded, bypassing stopCore...")
            return false
        }
        return nativeStopCore()
    }

    fun initializeEngine(configPath: String) {
        LogUtil.d("CoreNativeManager: Initializing Golang Engine...")
        if(setup(configPath)) {
            startCore()
            LogUtil.d("CoreNativeManager: Engine Started Successfully")
        } else {
            LogUtil.e("CoreNativeManager: Setup Failed or library missing")
        }
    }
}
