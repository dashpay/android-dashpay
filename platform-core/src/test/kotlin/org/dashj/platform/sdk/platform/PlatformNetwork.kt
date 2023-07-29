package org.dashj.platform.sdk.platform
import org.bitcoinj.params.OuzoDevNetParams
import org.bitcoinj.wallet.*
import org.bitcoinj.wallet.authentication.AuthenticationGroupExtension
import org.junit.jupiter.api.AfterEach
import java.util.*

open class PlatformNetwork {

    val platform = Platform(OuzoDevNetParams.get())

    val assetLockTxId: String
    val seed: String

    val wallet: Wallet
    val authenticationGroupExtension: AuthenticationGroupExtension

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
            platform.params.id.contains("ouzo") -> {
                seed = "city recycle story comfort weapon mammal improve fancy sunset bounce badge reunion"
                assetLockTxId = ""
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
        authenticationGroupExtension = AuthenticationGroupExtension(wallet)
        authenticationGroupExtension.addKeyChains(wallet.params, wallet.keyChainSeed, EnumSet.of(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING))
        wallet.addExtension(authenticationGroupExtension)
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
