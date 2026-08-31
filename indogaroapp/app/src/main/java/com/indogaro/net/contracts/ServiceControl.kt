package com.indogaro.net.contracts

interface ServiceControl {
    fun getServiceState(): Int
    fun stopService()
}
