/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.tools

import org.dashevo.Client
import org.dashevo.client.ClientOptions
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashevo.dashpay.ContactRequest
import org.dashevo.dashpay.ContactRequests
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.dpp.identifier.Identifier
import org.dashevo.platform.Documents
import org.dashevo.platform.Names
import org.dashevo.platform.Platform

class NetworkActivity {
    companion object {
        lateinit var sdk: Client
        lateinit var platform: Platform
        lateinit var network: String

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: NetworkActivity network")
                return
            }
            network = args[0]
            sdk = Client(
                ClientOptions(network = network)
            )
            platform = sdk.platform
            getNetworkActivity()
        }

        private fun getNetworkActivity() {

            val nameDocuments = getNameDocuments()
            val contactRequests = getContactRequests()
            val establishedContacts = getEstablishedContacts(contactRequests)
            val identitiesWithoutUsernames = getIdentitiesWithoutUsernames(nameDocuments, contactRequests)

            // Display Report
            println()
            println("Network Activity Report")
            println("-----------------------")
            println("Network: $network")
            println("Registered usernames (.dash): ${nameDocuments.size}")
            println("Contact Requests:             ${contactRequests.size} [all sent] ")
            println("Established Contacts:         ${establishedContacts.size} [both sent and accepted]")
            println("Average Contacts per user:    ${"%.2f".format(establishedContacts.size.toDouble()/nameDocuments.size)}")

            println("--------------------------------------------")
            val identities = getIdentitiesWithoutUsernames(nameDocuments, contactRequests)
            println("Identities without usernames: ${identities.size}")
            println("                            : $identities")

        }

        fun getIdentityForName(nameDocument: Document): Identifier {
            val records = nameDocument.data["records"] as Map<String, Any?>
            return try {
                Identifier.from(records["dashUniqueIdentityId"])
            } catch (e: Exception) {
                Identifier.from(records["dashAliasIdentityId"])
            }
        }

        private fun getIdentitiesWithoutUsernames(nameDocuments: List<Document>, contactRequests: List<ContactRequest>): List<Identifier> {
            val contactRequestByOwnerId = contactRequests.associateBy({it.ownerId}, {it})
            val namesByOwnerId = nameDocuments.associateBy({getIdentityForName(it)}, {it})

            return contactRequestByOwnerId.filter { !namesByOwnerId.contains(it.key) }.map { it.key }
        }

        //TODO: This could use Documents.getAll
        private fun getAllDocuments(contractDocument: String): List<Document> {
            var startAt = 0
            var documents: List<Document>? = null
            val allDocuments = arrayListOf<Document>()
            var requests = 0
            do {
                val queryOpts = DocumentQuery.Builder()
                    .startAt(startAt)
                    .build()

                try {
                    documents = platform.documents.get(contractDocument, queryOpts)

                    requests += 1
                    allDocuments.addAll(documents)

                    startAt += Documents.DOCUMENT_LIMIT
                } catch (e: Exception) {
                    println("\nError retrieving results (startAt =  $startAt)")
                    println(e.message);
                }
            } while (requests == 0 || documents!!.size >= 100)

            return allDocuments
        }

        private fun getNameDocuments(): List<Document> {
            val allNameDocuments = getAllDocuments(Names.DPNS_DOMAIN_DOCUMENT)
            val nameDocuments = arrayListOf<Document>()

            allNameDocuments.forEach {
                if (it.data["normalizedParentDomainName"] == "dash")
                    nameDocuments.add(it)
            }

            return nameDocuments
        }

        private fun getContactRequests(): List<ContactRequest> {
            val allDocuments = getAllDocuments(ContactRequests.CONTACTREQUEST_DOCUMENT);
            return allDocuments.map { ContactRequest(it) }
        }

        private fun getEstablishedContacts(contactRequests: List<ContactRequest>) :List<Pair<ContactRequest, ContactRequest>> {
            val establishedContacts = arrayListOf<Pair<ContactRequest, ContactRequest>>()
            val contactsRequestByOwnerId = contactRequests.associateBy({it.ownerId}, {it})
            val ownerIds = contactsRequestByOwnerId.keys
            val contactRequestsByToUserId = contactRequests.associateBy({it.toUserId}, {it})

            for (sentContactRequest in contactRequests) {
                val sender = sentContactRequest.ownerId
                val recipient = sentContactRequest.toUserId
                val receivedContactRequest = contactRequests.find { it.toUserId == sender && it.ownerId == recipient}
                if (receivedContactRequest != null) {
                    val contact = Pair(sentContactRequest, receivedContactRequest)
                    establishedContacts.add(contact)
                }
            }
            return establishedContacts
        }
    }
}