/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo

import org.bitcoinj.params.EvoNetParams
import org.bitcoinj.params.MobileDevNetParams
import org.bitcoinj.params.PalinkaDevNetParams
import org.bitcoinj.params.TestNet3Params
import org.dashevo.platform.Platform

class Client(network: String) {
    val platform = Platform( when (network) {
        "evonet" -> EvoNetParams.get()
        "palinka" -> PalinkaDevNetParams.get()
        "mobile" -> MobileDevNetParams.get()
        "testnet" -> TestNet3Params.get()
        else -> throw IllegalArgumentException("network $network is not valid")
    })

    fun isReady(): Boolean {
        // TODO: determine that there are several valid nodes to connect to
        // A method should be added to the Platform class to query several nodes
        // with GetStatus to determine that each is online and is updated to
        // the correct version of DPP that this library supports.  Platform
        // should then use those nodes for future calls.  If later as the app
        // runs a particular node is found to be offline, then Platform should
        // find another node to replace it.  This methodology is similar to the way
        // that dashj handles connections to peers.
        return true
    }
}