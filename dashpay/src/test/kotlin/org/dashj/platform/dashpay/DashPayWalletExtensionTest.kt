package org.dashj.platform.dashpay

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import org.dashj.platform.dpp.ProtocolVersion
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.dpp.identity.Identity
import org.dashj.platform.dpp.identity.IdentityPublicKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DashPayWalletExtensionTest : PlatformNetwork() {
    @Test
    fun roundTripTest() {
        val extension = DashPayWalletExtension(platform) // (platform, wallet)
        extension.blockchainIdentity = BlockchainIdentity(platform, 0, wallet)

        val expectedIdentity = Identity(
            Identifier.Companion.from(Sha256Hash.ZERO_HASH),
            500000L,
            mutableListOf(
                IdentityPublicKey(
                    0,
                    IdentityPublicKey.Type.ECDSA_SECP256K1,
                    IdentityPublicKey.Purpose.AUTHENTICATION,
                    IdentityPublicKey.SecurityLevel.MASTER,
                    ECKey().pubKey,
                    true
                )
            ),
            1,
            ProtocolVersion.latestVersion
        )

        extension.blockchainIdentity!!.identity = expectedIdentity

        val bytes = extension.serializeWalletExtension()

        assertEquals(94, bytes.size)

        val extensionTwo = DashPayWalletExtension(platform)
        extensionTwo.blockchainIdentity = BlockchainIdentity(platform, 0, wallet)
        extensionTwo.deserializeWalletExtension(wallet, bytes)

        assertEquals(expectedIdentity, extensionTwo.blockchainIdentity!!.identity)
    }

    @Test
    fun roundTripWithoutIdentityTest() {
        val extension = DashPayWalletExtension(platform)
        extension.blockchainIdentity = BlockchainIdentity(platform, 0, wallet)
        val bytes = extension.serializeWalletExtension()

        assertEquals(0, bytes.size)

        val extensionTwo = DashPayWalletExtension(platform)
        extensionTwo.blockchainIdentity = BlockchainIdentity(platform, 0, wallet)
        extensionTwo.deserializeWalletExtension(wallet, bytes)
    }
}
