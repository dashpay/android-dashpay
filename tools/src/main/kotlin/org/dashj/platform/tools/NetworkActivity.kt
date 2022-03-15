/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.tools

import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dashpay.ContactRequest
import org.dashj.platform.dashpay.ContactRequests
import org.dashj.platform.dashpay.Profile
import org.dashj.platform.dashpay.Profiles
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions
import org.dashj.platform.sdk.platform.DomainDocument
import org.dashj.platform.sdk.platform.Names
import org.dashj.platform.sdk.platform.Platform

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
            val profileDocuments = getProfileDocuments()
            val totalCredits = getTotalCreditBalance(nameDocuments)

            // Display Report
            println()
            println("Network Activity Report")
            println("-----------------------")
            println("Network: $network")
            println("Registered usernames (.dash): ${nameDocuments.size}")
            println("Profiles:                     ${profileDocuments.size}")
            println("Contact Requests:             ${contactRequests.size} [all sent] ")
            println("Established Contacts:         ${establishedContacts.size} [both sent and accepted]")
            println("Average Contacts per user:    ${"%.2f".format(establishedContacts.size.toDouble() / nameDocuments.size)}")
            println("Total Credits Available:      $totalCredits")
            println("Total Credits/User:           ${totalCredits / nameDocuments.size}")

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

        private fun getIdentitiesWithoutUsernames(nameDocuments: List<DomainDocument>, contactRequests: List<ContactRequest>): List<Identifier> {
            val contactRequestByOwnerId = contactRequests.associateBy({ it.ownerId }, { it })
            val namesByOwnerId = nameDocuments.associateBy({ getIdentityForName(it.document) }, { it })

            return contactRequestByOwnerId.filter { !namesByOwnerId.contains(it.key) }.map { it.key }
        }

        // TODO: This could use Documents.getAll
        private fun getAllDocuments(contractDocument: String): List<Document> {
            var startAfter: Identifier? = null
            var documents: List<Document>? = null
            val allDocuments = arrayListOf<Document>()
            var requests = 0
            var queryOpts = DocumentQuery.Builder().build()
            do {
                try {
                    documents = platform.documents.get(contractDocument, queryOpts)

                    requests += 1
                    allDocuments.addAll(documents)

                    startAfter = documents.last().id
                    queryOpts = DocumentQuery.Builder()
                        .startAfter(startAfter)
                        .build()
                } catch (e: Exception) {
                    println("\nError retrieving results (startAfter =  $startAfter)")
                    println(e.message)
                }
            } while (documents!!.size >= 100)

            return allDocuments
        }

        private fun getNameDocuments(): List<DomainDocument> {
            val allNameDocuments = getAllDocuments(Names.DPNS_DOMAIN_DOCUMENT)
            val nameDocuments = arrayListOf<DomainDocument>()

            allNameDocuments.forEach {
                if (it.data["normalizedParentDomainName"] == "dash") {
                    nameDocuments.add(DomainDocument(it))
                }
            }

            return nameDocuments
        }

        private fun getContactRequests(): List<ContactRequest> {
            val allDocuments = getAllDocuments(ContactRequests.CONTACTREQUEST_DOCUMENT)
            return allDocuments.map { ContactRequest(it) }
        }

        private fun getEstablishedContacts(contactRequests: List<ContactRequest>): List<Pair<ContactRequest, ContactRequest>> {
            val establishedContacts = arrayListOf<Pair<ContactRequest, ContactRequest>>()
            val contactsRequestByOwnerId = contactRequests.associateBy({ it.ownerId }, { it })
            val ownerIds = contactsRequestByOwnerId.keys
            val contactRequestsByToUserId = contactRequests.associateBy({ it.toUserId }, { it })

            for (sentContactRequest in contactRequests) {
                val sender = sentContactRequest.ownerId
                val recipient = sentContactRequest.toUserId
                val receivedContactRequest = contactRequests.find { it.toUserId == sender && it.ownerId == recipient }
                if (receivedContactRequest != null) {
                    val contact = Pair(sentContactRequest, receivedContactRequest)
                    establishedContacts.add(contact)
                }
            }
            return establishedContacts
        }

        fun getProfileDocuments(): List<Profile> {
            val allDocuments = getAllDocuments(Profiles.DOCUMENT)
            return allDocuments.map { Profile(it) }
        }

        private fun getTotalCreditBalance(names: List<DomainDocument>): Long {
            var credits = 0L
            names.forEach {
                if (it.dashUniqueIdentityId != null) {
                    val identity = platform.identities.get(it.dashUniqueIdentityId!!)

                    if (identity != null) {
                        credits += identity.balance
                    }
                }
            }
            return credits
        }
    }
}
