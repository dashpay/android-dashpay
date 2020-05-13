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
import java.util.*


class SearchNames {
    companion object {
        val sdk = Client("mobile")

        @JvmStatic
        fun main(args: Array<String>) {
            println("Enter search text for names: ")
            val scanner = Scanner(System.`in`)
            var text = scanner.next()
            do {
                searchDocuments(text)
                println("Enter search text for names: ")
                text = scanner.next()
            } while (text.isNotEmpty())
        }

        fun searchDocuments(text: String) {
            val platform = sdk.platform
            sdk.isReady();

            var startAt = 0
            var documents: List<Document>? = null
            var requests = 0
            do {
                val queryOpts = DocumentQuery.Builder().startAt(startAt).build()
                println(queryOpts.toJSON())

                try {
                    documents = platform.names.search(text, Names.DEFAULT_PARENT_DOMAIN, false, startAt)

                    requests += 1;

                    for (doc in documents) {
                        println(
                            "Name: " + doc.data["label"] +
                                    " (domain: " + doc.data["normalizedParentDomainName"] +
                                    ") Identity: " + doc.userId
                        )
                    }

                    if(documents.isEmpty()) {
                        println("No names found starting with $text")
                    }

                    startAt += 100;
                } catch (e: Exception) {
                    println("\nError retrieving results (startAt =  $startAt)")
                    println(e.message);
                }
            } while (requests == 0 || documents!!.size >= Documents.DOCUMENT_LIMIT);
        }
    }
}