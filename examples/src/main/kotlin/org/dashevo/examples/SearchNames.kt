/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.dashevo.Client
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.document.Document
import org.dashevo.platform.Documents
import org.dashevo.platform.Names
import org.json.JSONObject
import java.util.*


class SearchNames {
    companion object {
        lateinit var sdk: Client

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: SearchNames network")
                return
            }
            sdk = Client(args[0])
            println("Enter search text for names: ")
            val scanner = Scanner(System.`in`)
            var text = scanner.next()
            do {
                searchDocuments(text)
                println()
                println("Enter search text for names: ")
                text = scanner.next()
            } while (text.isNotEmpty())
        }

        private fun searchDocuments(text: String) {
            val platform = sdk.platform
            sdk.isReady()

            var startAt = 0
            var documents: List<Document>? = null
            var requests = 0
            do {
                try {
                    documents = platform.names.search(text, Names.DEFAULT_PARENT_DOMAIN, false, startAt)

                    requests += 1;

                    for (doc in documents) {
                        println(
                            "Name: " + doc.data["label"] +
                                    " (domain: " + doc.data["normalizedParentDomainName"] +
                                    ") Identity: " + doc.ownerId
                        )
                    }

                    if (documents.isEmpty()) {
                        println("No names found starting with $text")
                    }

                    startAt += Documents.DOCUMENT_LIMIT
                } catch (e: Exception) {
                    println("\nError retrieving results (startAt =  $startAt)")
                    println(e.message)
                }
            } while (requests == 0 || documents!!.size >= Documents.DOCUMENT_LIMIT)
        }
    }
}