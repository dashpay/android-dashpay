/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashj.platform.dashpay

import java.util.Date
import org.bitcoinj.params.BinTangDevNetParams
import org.bitcoinj.wallet.DerivationPathFactory
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.WalletEx
import org.dashj.platform.sdk.platform.Platform
import org.junit.jupiter.api.AfterEach

open class PlatformNetwork {

    val platform = Platform(BinTangDevNetParams.get())
    val seed = "quantum alarm evoke estate siege play moon spoon later paddle rifle ancient"
    val wallet: WalletEx = WalletEx(
        platform.params,
        KeyChainGroup.builder(platform.params)
            .addChain(
                DeterministicKeyChain.builder()
                    .accountPath(DerivationPathFactory.get(platform.params).bip44DerivationPath(0))
                    .seed(DeterministicSeed(seed, null, "", Date().time))
                    .build()
            )
            .build()
    )
    init {
        wallet.initializeAuthenticationKeyChains(wallet.keyChainSeed, null)
    }

    @AfterEach
    fun afterEachTest() {
        println(platform.client.reportNetworkStatus())
    }

    init {
        println("initializing platform")
        platform.useValidNodes()
    }
}
