package com.indogaro.net.core

import com.indogaro.net.util.LogUtil

object CoreNativeManager {
    init { System.loadLibrary("v2ray") }
    external fun setup(configPath: String): Boolean
    external fun startCore(): Boolean
    external fun stopCore(): Boolean

    fun initializeEngine(configPath: String) {
        LogUtil.d("CoreNativeManager: Initializing Golang Engine...")
        if(setup(configPath)) {
            startCore()
            LogUtil.d("CoreNativeManager: Engine Started Successfully")
        } else {
            LogUtil.e("CoreNativeManager: Setup Failed")
        }
    }
}
