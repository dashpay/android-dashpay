/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.bitcoinj.evolution.CreditFundingTransaction
import org.dashevo.Client
import org.dashevo.dpp.identity.IdentityPublicKey
import org.dashevo.dpp.toBase64
import org.json.JSONObject
import java.lang.Thread.sleep

class CreateIdentity {
    companion object {
        val sdk = Client("palinka")

        @JvmStatic
        fun main(args: Array<String>) {
            createIdentity()
        }

        fun createIdentity() {
            val platform = sdk.platform
            sdk.isReady()

            val cftx = CreditFundingTransaction(platform.params, DefaultIdentity.creditBurnTx)
            cftx.setCreditBurnPublicKeyAndIndex(DefaultIdentity.identityPrivateKey, 0)

            try {
                var identity = platform.identities.get(cftx.creditBurnIdentityIdentifier.toStringBase58())

                if (identity == null) {
                    // only create the identity if it does not exist
                    platform.identities.register(cftx, listOf(IdentityPublicKey(0, IdentityPublicKey.TYPES.ECDSA_SECP256K1, DefaultIdentity.publicKey.toBase64(), true)))
                    sleep(10000)
                    identity = platform.identities.get(cftx.creditBurnIdentityIdentifier.toString())
                }

                // check that the identity public key matches our public key information
                if (identity!!.publicKeys[0].data == DefaultIdentity.publicKey.toBase64())
                    println("Identity public key verified")

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