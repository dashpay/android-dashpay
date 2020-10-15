/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.bitcoinj.script.Script
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.Wallet
import org.dashevo.Client
import org.dashevo.dpp.toHexString
import org.json.JSONObject

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
            sdk = Client(args[0])
            if (args.size > 1) {
                for (i in 0 until args.size - 1) {
                    wordlist.add(args[i+1])
                }
            }
            getIdentityFromSeed()
        }

        private fun getIdentityFromSeed() {
            val platform = sdk.platform
            sdk.isReady()

            val seed = DeterministicSeed(wordlist, null, "", 0)
            val kcg = KeyChainGroup.builder(platform.params).fromSeed(seed, Script.ScriptType.P2PKH).build()
            val wallet = Wallet(platform.params, kcg)
            wallet.initializeAuthenticationKeyChains(seed, null)

            println("Locate the identity with this first public key hash ${wallet.blockchainIdentityKeyChain.watchingKey.pubKeyHash.toHexString()}")
            val identity = platform.identities.get(wallet.blockchainIdentityKeyChain.watchingKey.pubKeyHash)
            if (identity != null) {
                println("Identity: ${identity.id}")
                val nameDocuments = platform.names.getByUserId(identity.id)
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