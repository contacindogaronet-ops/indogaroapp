package com.indogaro.net.contracts

interface IDialerService {
    fun protect(socket: Int): Boolean
}
