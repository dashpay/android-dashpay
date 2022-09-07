/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import java.lang.Thread.sleep
import org.dashj.platform.dashpay.BlockchainIdentity
import org.dashj.platform.dpp.toHex
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions
import org.dashj.platform.sdk.client.WalletOptions
import org.json.JSONObject

class CreateContract {
    companion object {
        lateinit var client: Client

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size < 2) {
                println("Usage: CreateContract network contract")
                println("  contract is \"dashpay\" or \"dashwallet\"")
                return
            }
            client = Client(ClientOptions(network = args[0], walletOptions = WalletOptions(DefaultIdentity(args[0]).seed)))
            createContract(args[1])
        }

        private fun createContract(contractToCreate: String) {
            val blockchainIdentity = BlockchainIdentity(client.platform, 0, client.wallet!!)
            blockchainIdentity.recoverIdentity()
            val identity = blockchainIdentity.identity!! // client.platform.identities.getByPublicKeyHash(client.wallet!!.blockchainIdentityKeyChain.getKey(0, true).pubKeyHash)!!

            val contractText = this::class.java.getResource("$contractToCreate-contract.json")?.readText()
            val jsonObject = JSONObject(contractText)
            val rawContract = jsonObject.toMap()

            val dataContract = client.platform.contracts.create(rawContract, identity)

            val signingKey = blockchainIdentity.getPrivateKeyByPurpose(BlockchainIdentity.KeyIndexPurpose.AUTHENTICATION, null)
            println("public key hash: ${signingKey.pubKeyHash.toHex()}")
            val transition = client.platform.contracts.broadcast(dataContract, identity, signingKey, BlockchainIdentity.KeyIndexPurpose.AUTHENTICATION.ordinal)

            println("DataContractCreateTransition: ----------------------")
            println(JSONObject(transition.toJSON()).toString(2))
            sleep(10000)

            val publishedContract = client.platform.contracts.get(dataContract.id)

            println("DataContract: -----------------------------------")
            println(JSONObject(publishedContract!!.toJSON()).toString(2))
        }
    }
}
