/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import org.bitcoinj.core.Sha256Hash
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions

class RegisteredNames {
    companion object {
        lateinit var sdk: Client

        @JvmStatic
        fun main(args: Array<String>) {
            sdk = Client(ClientOptions(network = args[0]))
            sdk.platform.useValidNodes()
            getDocuments()
        }

        fun getDocuments() {
            val platform = sdk.platform

            var lastItem = Identifier.from(Sha256Hash.ZERO_HASH)
            var documents: List<Document>? = null
            var requests = 0
            val limit = 100
            var queryOpts = DocumentQuery.Builder().limit(limit).build()

            do {
                println(queryOpts.toJSON())

                try {
                    documents = platform.documents.get("dpns.domain", queryOpts)

                    requests += 1

                    for (doc in documents) {
                        println(
                            "Name: %-20s".format(doc.data["label"]) +
                                " (domain: " + doc.data["normalizedParentDomainName"] +
                                ") Identity: " + doc.ownerId
                        )
                    }

                    lastItem = documents.last().id
                    if (documents.isNotEmpty()) {
                        queryOpts = DocumentQuery.Builder().startAfter(lastItem).limit(100).build()
                    }
                } catch (e: Exception) {
                    println("\nError retrieving results (startAt =  $lastItem)")
                    println(e.message)
                    return
                }
            } while (requests >= 0 || documents!!.size >= limit)
        }
    }
}
