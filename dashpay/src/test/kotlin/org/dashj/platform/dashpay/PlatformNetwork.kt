package org.dashj.platform.dashpay

import java.util.Date
import org.bitcoinj.params.PalinkaDevNetParams
import org.bitcoinj.wallet.DerivationPathFactory
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.Wallet
import org.dashevo.platform.Platform
import org.junit.jupiter.api.AfterEach

open class PlatformNetwork {

    val platform = Platform(PalinkaDevNetParams.get())
    // val seed = "lecture embody employ sad mouse arctic lemon knife provide hockey unaware comfort"
    val seed = "version route there raw fringe derive gain prepare online salon faint scrub"
    val wallet: Wallet = Wallet(
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
