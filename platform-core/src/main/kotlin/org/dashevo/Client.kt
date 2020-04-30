/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo

import org.bitcoinj.params.EvoNetParams
import org.bitcoinj.params.MobileDevNetParams
import org.dashevo.platform.Platform

class Client(network: String) {
    val platform = Platform(if (network == "testnet") EvoNetParams.get() else MobileDevNetParams.get())

    fun isReady(): Boolean {
        return true
    }
}