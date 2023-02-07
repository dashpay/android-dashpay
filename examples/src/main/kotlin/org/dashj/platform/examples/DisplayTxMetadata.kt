/**
 * Copyright (c) 2022-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import java.util.Date
import java.util.Scanner
import org.dashj.platform.contracts.wallet.TxMetadata
import org.dashj.platform.contracts.wallet.TxMetadataDocument
import org.dashj.platform.dashpay.BlockchainIdentity
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions
import org.dashj.platform.sdk.client.WalletOptions

class DisplayTxMetadata {
    companion object {
        lateinit var client: Client

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: DisplayTxMetadata network")
                return
            }
            println("Enter a recovery phrase: ")
            val scanner = Scanner(System.`in`)
            val phrase = scanner.nextLine()

            val recoveryPhrase = if (phrase == "default") { DefaultIdentity(args[0]).seed } else phrase
            client = Client(ClientOptions(network = args[0], walletOptions = WalletOptions(recoveryPhrase)))
            displayDocuments()
        }

        private fun displayDocuments() {
            val blockchainIdentity = BlockchainIdentity(client.platform, 0, client.wallet!!)
            blockchainIdentity.recoverIdentity()
            val identity = blockchainIdentity.identity!!

            val txMetadata = TxMetadata(client.platform)

            val documents = txMetadata.get(identity.id)

            println("Tx Metadata for ${identity.id}: -----------------------------------")
            for (doc in documents) {
                println("new document: ${doc.id}; createdAt ${Date(doc.createdAt!!)}")
                val txDoc = TxMetadataDocument(doc)
                try {
                    val txList = blockchainIdentity.decryptTxMetadata(txDoc, null)
                    // println("  $txList")
                    for (txmd in txList) {
                        println("* $txmd")
                    }
                } catch (e: Exception) {
                    println("-> ERROR: decryption")
                }
            }
        }
    }
}
