/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import org.bitcoinj.evolution.AssetLockTransaction
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions
import org.json.JSONObject

/**
 * This is an example that will register all the names from the command line
 * using an example identity on schnapps devnet
 */

class RegisterNames {
    companion object {
        val sdk = Client(ClientOptions(network = "schnapps"))

        @JvmStatic
        fun main(args: Array<String>) {
            for (i in 1 until args.size)
                registerName(args[i])
        }

        fun registerName(name: String) {
            val platform = sdk.platform

            val cftx = AssetLockTransaction(platform.params, DefaultIdentity.creditBurnTx)
            cftx.setAssetLockPublicKeyAndIndex(DefaultIdentity.identityPrivateKey, 0)

            try {
                val identityPrivateKey = DefaultIdentity.identityPrivateKey

                var identity = platform.identities.get(cftx.identityId.toStringBase58())

                if (platform.names.get(name) == null) {
                    var nameDocument = platform.names.register(name, identity!!, identityPrivateKey)

                    // display information
                    println("Name Created: $name")
                    println(JSONObject(nameDocument!!.toJSON()).toString(2))
                } else {
                    println("ERROR: username $name already exists")
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            }
        }
    }
}
