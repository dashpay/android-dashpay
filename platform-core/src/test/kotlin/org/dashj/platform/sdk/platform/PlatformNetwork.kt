package org.dashj.platform.sdk.platform
import java.util.Date
import org.bitcoinj.params.JackDanielsDevNetParams
import org.bitcoinj.wallet.DerivationPathFactory
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.Wallet
import org.junit.jupiter.api.AfterEach

open class PlatformNetwork {

    val platform = Platform(JackDanielsDevNetParams.get())

    val assetLockTxId: String
    val seed: String

    val wallet: Wallet

    init {
        when {
            platform.params.id.contains("test") -> {
                assetLockTxId = "1175bf329cf6d35839f67aa57da87636a76b4837ce76b46ababa2a415be8d866"
                seed = "mango air virus pigeon crowd attract review lemon lion assume lab rain"
            }
            platform.params.id.contains("krupnik") -> {
                assetLockTxId = "33d94c52dc54b9f94ab73308eee8bf708da06eb9b587e5c2a2d54c031337564d"
                seed = "draw hole box island loan mom rookie park page sword curve illegal"
            }
            else -> {
                assetLockTxId = ""
                seed = ""
            }
        }

        wallet = Wallet(
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
