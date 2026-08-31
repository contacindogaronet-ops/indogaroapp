package com.indogaro.net.contracts

object Tun2SocksControl {
    init { System.loadLibrary("hev-socks5-tunnel") }
    external fun start(fd: Int, mtu: Int, socksPort: Int): Boolean
    external fun stop(): Boolean
}
