package org.dashj.platform.dashpay

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class BlockchainIdentityTest : PlatformNetwork() {

    val blockchainIdentity = BlockchainIdentity(platform, 0, wallet)

    @Test
    fun checkWalletSeedTest() {
        assertEquals(seed.split(' '), blockchainIdentity.wallet!!.keyChainSeed.mnemonicCode)
    }

    @Test
    fun updateProfileTest() {
        val displayName = "Hello " + Random.nextInt()

        blockchainIdentity.recoverIdentity(wallet.blockchainIdentityKeyChain.watchingKey.pubKeyHash)

        val currentProfile = blockchainIdentity.getProfileFromPlatform()!!

        val updatedProfile = blockchainIdentity.updateProfile(displayName, null, null, null, null, null)

        val retrievedProfile = blockchainIdentity.getProfileFromPlatform()!!

        assertNotEquals(currentProfile, retrievedProfile)
        assertEquals(updatedProfile, Profile(retrievedProfile))
    }
}
