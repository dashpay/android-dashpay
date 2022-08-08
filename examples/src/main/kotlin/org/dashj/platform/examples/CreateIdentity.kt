/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import java.lang.Thread.sleep
import org.bitcoinj.evolution.CreditFundingTransaction
import org.bitcoinj.quorums.InstantSendLock
import org.dashj.platform.dpp.identity.IdentityPublicKey
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions
import org.json.JSONObject

class CreateIdentity {
    companion object {
        val sdk = Client(ClientOptions(network = "schnapps"))

        @JvmStatic
        fun main(args: Array<String>) {
            createIdentity()
        }

        fun createIdentity() {
            val platform = sdk.platform

            val cftx = CreditFundingTransaction(platform.params, DefaultIdentity.creditBurnTx)
            cftx.setCreditBurnPublicKeyAndIndex(DefaultIdentity.identityPrivateKey, 0)
            val islock = InstantSendLock(platform.params, DefaultIdentity.islock, InstantSendLock.ISLOCK_VERSION)

            try {
                var identity = platform.identities.get(cftx.creditBurnIdentityIdentifier.toStringBase58())

                if (identity == null) {
                    // only create the identity if it does not exist
                    platform.identities.register(
                        cftx, islock,
                        listOf(
                            DefaultIdentity.privateKey
                        ),
                        listOf(
                            IdentityPublicKey(
                                0, IdentityPublicKey.Type.ECDSA_SECP256K1,
                                DefaultIdentity.publicKey
                            )
                        )
                    )
                    sleep(10000)
                    identity = platform.identities.get(cftx.creditBurnIdentityIdentifier.toString())
                }

                // check that the identity public key matches our public key information
                if (identity!!.publicKeys[0].data.contentEquals(DefaultIdentity.publicKey)) {
                    println("Identity public key verified")
                }

                // display information
                println("Identity Created: ${cftx.creditBurnIdentityIdentifier.toStringBase58()}")
                println(JSONObject(identity!!.toJSON()).toString(2))
                println("Private Key: ${DefaultIdentity.privateKeyHex} (hex)")
            } catch (e: Exception) {
                println(e.localizedMessage)
            }
        }
    }
}
