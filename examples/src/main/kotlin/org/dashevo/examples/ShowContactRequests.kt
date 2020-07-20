/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.dashevo.Client
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dashpay.ContactRequests
import org.dashevo.dpp.document.Document
import org.dashevo.platform.Documents
import org.json.JSONObject
import java.util.*


class ShowContactRequests {
    companion object {
        lateinit var sdk: Client

        @JvmStatic
        fun main(args: Array<String>) {
            val scanner = Scanner(System.`in`)

            val network = if (args.isNotEmpty()) {
                args[0]
            } else {
                println("Enter the network in which to make a contact request query (palinka, evonet, mobile): ")
                scanner.next()
            }

            sdk = Client(network)
            if (!sdk.platform.hasApp("dashpay")) {
                println("$network does not support dashpay")
                return
            }
            sdk.isReady()

            val prompt = "Enter identity or name for which to list contact requests (all = show all, !quit = quit): "
            println(prompt)
            var text = scanner.next()
            do {
                if (text == "!quit")
                    return
                searchContactRequests(text)
                println(prompt)
                text = scanner.next()
            } while (text.isNotEmpty())
        }

        private fun searchContactRequests(id: String) {
            val platform = sdk.platform

            var startAt = 0
            var documents: List<Document>? = null
            var requests = 0
            do {
                val queryOpts = DocumentQuery.Builder().startAt(startAt).build()
                println("query: ${queryOpts.toJSON()}")
                var identityId = id

                try {
                    documents = if (id == "all") {
                        platform.documents.get(ContactRequests.CONTACTREQUEST_DOCUMENT, queryOpts)
                    } else {
                        // resolve name if possible
                        if (identityId.length != 44 && identityId.length != 43) {
                            val nameDocument = platform.names.resolve(id)
                            if (nameDocument != null) {
                                val records = nameDocument.data["records"] as MutableMap<String, Any?>
                                identityId = records["dashIdentity"] as String
                            }
                        }
                        ContactRequests(platform).get(identityId, false, false, startAt)
                    }

                    requests += 1;

                    for (doc in documents) {
                        println("===================================================")
                        println("from: ${doc.ownerId} -> to: ${doc.data["toUserId"]}")
                        println()
                        println("contactRequest: ${JSONObject(doc.toJSON())}")
                    }

                    if (documents.isEmpty()) {
                        println("No identities found matching $id")
                    }

                    startAt += Documents.DOCUMENT_LIMIT;
                } catch (e: Exception) {
                    println("\nError retrieving results (startAt =  $startAt)")
                    println(e.message);
                }
            } while (requests == 0 || documents!!.size >= Documents.DOCUMENT_LIMIT);
        }
    }
}