/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import org.dashevo.Client
import org.dashevo.client.ClientOptions
import org.dashevo.client.WalletOptions
import org.json.JSONObject
import java.lang.Thread.sleep

class CreateContract {
    companion object {
        lateinit var client: Client

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: CreateContract network")
                return
            }
            client = Client(ClientOptions(network = args[0], walletOptions = WalletOptions(DefaultIdentity(args[0]).seed)))
            createContract()
        }

        private fun createContract() {
            val identity = client.platform.identities.getByPublicKeyHash(client.wallet!!.blockchainIdentityKeyChain.watchingKey.pubKeyHash)!!

            val contractText = javaClass.getResource("dashpay-contract.json").readText()
            val jsonObject = JSONObject(contractText)
            val rawContract = jsonObject.toMap()

            val dataContract = client.platform.contracts.create(rawContract, identity)

            val transition = client.platform.contracts.broadcast(dataContract, identity, client.wallet!!.blockchainIdentityKeyChain.watchingKey, 0)

            println("DataContractCreateTransition: ----------------------")
            println(JSONObject(transition.toJSON()).toString(2))
            sleep(10000)

            val publishedContract = client.platform.contracts.get(dataContract.id)

            println("DataContract: -----------------------------------")
            println(JSONObject(publishedContract!!.toJSON()).toString(2))
        }
    }
}