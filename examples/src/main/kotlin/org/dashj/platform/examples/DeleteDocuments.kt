/**
 * Copyright (c) 2022-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import java.util.Scanner
import org.dashj.platform.dashpay.BlockchainIdentity
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions
import org.dashj.platform.sdk.client.WalletOptions

class DeleteDocuments {
    companion object {
        lateinit var client: Client

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: DeleteDocuments network")
                return
            }
            println("Enter a recovery phrase that owns the documents to delete: ")
            val scanner = Scanner(System.`in`)
            val phrase = scanner.nextLine()

            val recoveryPhrase = if (phrase == "default") { DefaultIdentity(args[0]).seed } else phrase
            client = Client(ClientOptions(network = args[0], walletOptions = WalletOptions(recoveryPhrase)))

            deleteDocuments(scanner)
        }

        private fun deleteDocuments(scanner: Scanner) {
            val blockchainIdentity = BlockchainIdentity(client.platform, 0, client.wallet!!)
            blockchainIdentity.recoverIdentity()
            val identity = blockchainIdentity.identity!!

            print("Enter the document type locator: ")
            val typeLocator = scanner.nextLine()

            print("Enter the document id to delete (enter \"quit\" when finished): ")
            var documentId = scanner.nextLine()
            while (documentId != "quit") {
                if (!blockchainIdentity.deleteDocument(typeLocator, Identifier.from(documentId), null)) {
                    println("$documentId for $typeLocator was not found")
                }
                print("Enter the document id to delete (enter \"quit\" when finished): ")
                documentId = scanner.nextLine()
            }
        }
    }
}
