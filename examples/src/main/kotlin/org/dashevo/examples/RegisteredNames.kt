/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.bitcoinj.core.Base58
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.utils.BriefLogFormatter
import org.dashevo.Client
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.toHexString
import org.dashevo.platform.Documents

class RegisteredNames {
    companion object {
        val sdk = Client("mobile")

        @JvmStatic
        fun main(args: Array<String>) {
            getDocuments()
        }

        fun getDocuments() {
            val platform = sdk.platform
            sdk.isReady();

            var startAt = 0
            var documents: List<Document>? = null
            var requests = 0
            do {
                val queryOpts = DocumentQuery.Builder().startAt(startAt).build()
                println(queryOpts.toJSON())

                try {
                    documents = platform.documents.get("dpns.domain", queryOpts)

                    requests += 1

                    for (doc in documents) {
                        println(
                            "Name: %-20s".format(doc.data["label"]) +
                                    " (domain: " + doc.data["normalizedParentDomainName"] +
                                    ") Identity: " + doc.userId
                        )
                    }

                    startAt += Documents.DOCUMENT_LIMIT;
                } catch (e: Exception) {
                    println("\nError retrieving results (startAt =  $startAt)")
                    println(e.message);
                }
            } while (requests == 0 || documents!!.size >= 100);
        }
    }
}