/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions
import org.dashj.platform.sdk.platform.Documents
import org.json.JSONObject

class DashPayProfiles {
    companion object {
        lateinit var sdk: Client

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: DashPayProfiles network")
                return
            }
            sdk = Client(ClientOptions(network = args[0]))
            getDocuments()
        }

        fun getDocuments() {
            val platform = sdk.platform

            var startAt = 0
            var documents: List<Document>? = null
            var requests = 0
            var queryOpts = DocumentQuery.Builder().build()

            do {
                println(queryOpts.toJSON())
                try {
                    documents = platform.documents.get("dashpay.profile", queryOpts)

                    requests += 1

                    for (doc in documents) {
                        println(
                            "displayName: " + doc.data["displayName"] +
                                " (avatar: " + doc.data["avatarUrl"] +
                                ") Identity: " + doc.ownerId
                        )
                        println("-> publicMessage: " + doc.data["publicMessage"])
                        println()
                        println(JSONObject(doc.toJSON()).toString())
                        println("------------------------------------------------------------")
                    }

                    startAt += Documents.DOCUMENT_LIMIT
                    if (documents.isNotEmpty()) {
                        queryOpts = DocumentQuery.Builder().startAfter(documents.last().id).build()
                    }
                } catch (e: Exception) {
                    println("\nError retrieving results (startAt =  $startAt)")
                    println(e.message)
                }
            } while (requests == 0 || documents!!.size >= 100)
        }
    }
}
