/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Utils
import org.bitcoinj.evolution.CreditFundingTransaction
import org.dashevo.Client
import org.json.JSONObject

/**
 * This is an example that will register all the names from the command line
 * using an example identity on mobile devnet
 */

class RegisterNames {
    companion object {
        val sdk = Client("mobile")

        const val publicKeyHex = "02af0cfc16fa8778ce6fd64694bfb9f9853cb2e51217fe77af487d1c1a44d50da7"
        const val privateKeyHex = "7c8922c88449e483fe95901216d8116e3c2098e7cff8fa2cc75eaa8031a7a405"
        val publicKey = Utils.HEX.decode(publicKeyHex)
        val privateKey = Utils.HEX.decode(privateKeyHex)
        val creditBurnTx =
            Utils.HEX.decode("010000000145c496f93af16c09d3ec97f669527318b336261f24842693868d28029cd9650b000000006b483045022100e63f680c9341b89ab0bf2314dc1961b21e38a9f2b40a177b5e0e4dbb3b27f09402203c10c3ee1e894182f504effe835abcaea5ea61e7d091d65af3d88b711d23c7dc012103bd7faa0249fd3b5ed9a1060afd61cfd6a23f230a2fc97d2f8bdd7064c87f716dffffffff02409c000000000000166a14e1d6d0811d500e6ad194121ac4d927a04aef7227209ff703000000001976a914ecbf4da7dfc4142e845a8e9cb3ddb625cb2bd4f088ac00000000")
        val creditBurnTxId = Sha256Hash.wrap("b12b1064de1a8310c394528cbd26a4ef78876ef9d76669a40d77d78dda28e810")
        val creditBurnTxOutput = 0

        @JvmStatic
        fun main(args: Array<String>) {
            for(i in 1 until args.size)
                registerName(args[i])
        }

        fun registerName(name: String) {
            val platform = sdk.platform
            sdk.isReady();

            val cftx = CreditFundingTransaction(platform.params, creditBurnTx)
            cftx.setCreditBurnPublicKeyAndIndex(ECKey.fromPrivateAndPrecalculatedPublic(privateKey, publicKey), 0)

            try {
                val identityPrivateKey = ECKey.fromPrivateAndPrecalculatedPublic(privateKey, publicKey)

                var identity = platform.identities.get(cftx.creditBurnIdentityIdentifier.toStringBase58())

                var nameDocument = platform.names.register(name, identity!!, identityPrivateKey)

                // display information
                println("Name Created: ${name}")
                println(JSONObject(nameDocument!!.toJSON()).toString(2))

            } catch (e: Exception) {
                println(e.localizedMessage)
            }
        }
    }
}