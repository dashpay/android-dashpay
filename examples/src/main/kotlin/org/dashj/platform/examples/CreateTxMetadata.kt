/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import org.dashj.platform.contracts.wallet.TxMetadata
import org.dashj.platform.contracts.wallet.TxMetadataDocument
import org.dashj.platform.contracts.wallet.TxMetadataItem
import org.dashj.platform.dashpay.BlockchainIdentity
import org.dashj.platform.dpp.util.Entropy
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions
import org.dashj.platform.sdk.client.WalletOptions
import org.json.JSONObject

class CreateTxMetadata {
    companion object {
        lateinit var client: Client

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: CreateTxMetadata network")
                return
            }
            client = Client(ClientOptions(network = args[0], walletOptions = WalletOptions(DefaultIdentity(args[0]).seed)))
            createDocument()
        }

        private fun createDocument() {
            val blockchainIdentity = BlockchainIdentity(client.platform, 0, client.wallet!!)
            blockchainIdentity.recoverIdentity()
            val identity = blockchainIdentity.identity!! // client.platform.identities.getByPublicKeyHash(client.wallet!!.blockchainIdentityKeyChain.getKey(0, true).pubKeyHash)!!

            val txMetadata = TxMetadata(client.platform)

            // val signingKey = blockchainIdentity.getPrivateKeyByPurpose(BlockchainIdentity.KeyIndexPurpose.AUTHENTICATION, null)

            // val metadataDoc = txMetadata.create(0, 0, ByteArray(1024), identity, 1, signingKey)
            // println(JSONObject(metadataDoc.toJSON()).toString(2))

            // println("public key hash: ${signingKey.pubKeyHash.toHex()}")

            val txMetadataItems = listOf(
                TxMetadataItem(Entropy.generateRandomBytes(32), "Pizza Party"),
                TxMetadataItem(Entropy.generateRandomBytes(32), "Book Store")
            )

            blockchainIdentity.publishTxMetaData(txMetadataItems, null)

            val documents = txMetadata.get(identity.id)

            println("Tx Metadata: -----------------------------------")
            for (doc in documents) {
                val txDoc = TxMetadataDocument(doc)
                if (txDoc.encryptedMetadata[0] != 0.toByte()) {
                    println(JSONObject(doc.toJSON()).toString(2))
                    val txList = blockchainIdentity.decryptTxMetadata(txDoc, null)
                    println("  $txList")
                }
            }
        }
    }
}
