/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import org.bitcoinj.core.ECKey
import org.dashj.platform.dpp.toHexString
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions

class GetIdentity {
    companion object {
        lateinit var sdk: Client
        lateinit var id: String

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: GetIdentity network base58-id")
                return
            }
            sdk = Client(ClientOptions(network = args[0]))
            if (args.size > 1) {
                id = args[1]
            }
            getIdentity()
        }

        private fun getIdentity() {
            val platform = sdk.platform

            val identity = platform.identities.get(id)
            if (identity != null) {
                println("Identity: ${identity.id}")
                val publicKey = ECKey.fromPublicOnly(identity.publicKeys[0].data)
                println("Identity Public Key: ${publicKey.pubKeyHash.toHexString()}")
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
