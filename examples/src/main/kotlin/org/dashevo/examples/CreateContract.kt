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
import org.json.JSONObject
import java.lang.Thread.sleep
import java.util.*

class CreateContract {
    companion object {
        val sdk = Client("palinka")

        @JvmStatic
        fun main(args: Array<String>) {
            createIdentity()
        }

        fun createIdentity() {
            val platform = sdk.platform
            sdk.isReady()

            val wallet = Wallet(platform.params,
                KeyChainGroup.builder(platform.params)
                    .addChain(DeterministicKeyChain.builder()
                        .accountPath(DerivationPathFactory.get(platform.params).bip44DerivationPath(0))
                        .seed(DeterministicSeed(DefaultIdentity.seed, null, "", Date().time))
                        .build())
                    .build())

            wallet.initializeAuthenticationKeyChains(wallet.keyChainSeed, null)

            val identity = platform.identities.getByPublicKeyHash(wallet.blockchainIdentityKeyChain.watchingKey.pubKeyHash)!!

            val contractText = javaClass.getResource("dashpay-contract.json").readText()
            val jsonObject = JSONObject(contractText)
            val rawContract = jsonObject.toMap()

            val dataContract = platform.contracts.create(rawContract, identity)

            val transition = platform.contracts.broadcast(dataContract, identity, wallet.blockchainIdentityKeyChain.watchingKey, 0)

            println("DataContractCreateTransition: ----------------------")
            println(JSONObject(transition.toJSON()).toString(2))
            sleep(3000)

            val publishedContract = platform.contracts.get(dataContract.id)

            println("DataContract: -----------------------------------")
            println(JSONObject(publishedContract!!.toJSON()).toString())
        }
    }
}