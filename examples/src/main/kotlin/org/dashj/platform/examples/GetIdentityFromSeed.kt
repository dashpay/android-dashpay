/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import java.util.EnumSet
import org.bitcoinj.script.Script
import org.bitcoinj.wallet.AuthenticationKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.authentication.AuthenticationGroupExtension
import org.dashj.platform.dpp.toHex
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions

class GetIdentityFromSeed {
    companion object {
        lateinit var sdk: Client
        var wordlist = arrayListOf<String>()

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: GetIdentityFromSeed network word-list")
                return
            }
            sdk = Client(ClientOptions(network = args[0]))
            if (args.size > 1) {
                for (i in 0 until args.size - 1) {
                    wordlist.add(args[i + 1])
                }
            }
            getIdentityFromSeed()
        }

        private fun getIdentityFromSeed() {
            val platform = sdk.platform

            val seed = DeterministicSeed(wordlist, null, "", 0)
            val kcg = KeyChainGroup.builder(platform.params).fromSeed(seed, Script.ScriptType.P2PKH).build()
            val wallet = Wallet(platform.params, kcg)
            val authenticationExtension = AuthenticationGroupExtension(wallet.params)
            authenticationExtension.addKeyChains(
                wallet.params, wallet.keyChainSeed,
                EnumSet.of(
                    AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY,
                    AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING,
                    AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_TOPUP,
                    AuthenticationKeyChain.KeyChainType.INVITATION_FUNDING
                )
            )
            wallet.addExtension(authenticationExtension)

            println("Locate the identity with this first public key hash ${authenticationExtension.identityKeyChain.getKey(0, true).pubKeyHash.toHex()}")
            val identity = platform.identities.getByPublicKeyHash(authenticationExtension.identityKeyChain.getKey(0, true).pubKeyHash)
            if (identity != null) {
                println("Identity: ${identity.id}")
                val nameDocuments = platform.names.getByOwnerId(identity.id)
                if (nameDocuments.isNotEmpty()) {
                    println("Name: ${nameDocuments[0].data["label"] as String}")
                } else {
                    println("Domain document not found for ${identity.id}")
                }
            } else {
                println("Identity not found for this seed.")
            }
        }
    }
}
