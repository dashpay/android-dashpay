/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.bitcoinj.wallet.DerivationPathFactory
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.Wallet
import org.dashevo.Client
import org.dashevo.dashpay.BlockchainIdentity
import org.dashevo.client.ClientOptions
import org.json.JSONObject
import java.util.*
import kotlin.random.Random

class UpdateProfile {
    companion object {
        lateinit var sdk: Client
        lateinit var displayName: String

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: Update Profile network New-Display-Name")
                return
            }
            sdk = Client(ClientOptions(network = args[0]))
            displayName = args[1] + Random.nextInt()
            updateProfile()
        }

        fun updateProfile() {
            val platform = sdk.platform

            val wallet = Wallet(platform.params,
                KeyChainGroup.builder(platform.params)
                    .addChain(DeterministicKeyChain.builder()
                        .accountPath(DerivationPathFactory.get(platform.params).bip44DerivationPath(0))
                        .seed(DeterministicSeed(DefaultIdentity(platform.params).seed, null, "", Date().time))
                        .build())
                    .build())

            wallet.initializeAuthenticationKeyChains(wallet.keyChainSeed, null)


            val blockchainIdentity = BlockchainIdentity(platform, 0, wallet)
            blockchainIdentity.recoverIdentity(wallet.blockchainIdentityKeyChain.watchingKey.pubKeyHash)

            val currentProfile = blockchainIdentity.getProfileFromPlatform()!!

            blockchainIdentity.updateProfile(displayName, null, null, null, null, null)

            val updatedProfile = blockchainIdentity.getProfileFromPlatform()!!

            println("Current Profile: ----------------------")
            println(JSONObject(currentProfile.toJSON()).toString(2))


            println("Updated Profile: ----------------------")
            println(JSONObject(updatedProfile.toJSON()).toString(2))
        }
    }
}