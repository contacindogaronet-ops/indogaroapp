package com.indogaro.net.core

object CoreNativeManager {
    init {
        System.loadLibrary("v2ray")
    }

    external fun setup(configPath: String): Boolean
    external fun startCore(): Boolean
    external fun stopCore(): Boolean
}
