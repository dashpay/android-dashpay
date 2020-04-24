package org.dashevo

import org.bitcoinj.params.EvoNetParams
import org.bitcoinj.params.MobileDevNetParams
import org.dashevo.platform.Platform

class Client(network: String) {
    val platform = Platform(if(network == "testnet") EvoNetParams.get() else MobileDevNetParams.get())

    fun isReady(): Boolean {
        return true
    }
}