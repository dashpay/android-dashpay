/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.bitcoinj.core.Coin
import org.dashevo.Client
import org.dashevo.dashpay.ContactRequest
import org.dashevo.dashpay.ContactRequests
import org.dashevo.dashpay.Profile
import org.dashevo.dashpay.Profiles
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.dpp.identity.Identity
import org.dashj.platform.dpp.util.CreditsConverter
import org.dashevo.client.ClientOptions
import org.dashevo.platform.Documents
import org.dashevo.platform.DomainDocument
import org.dashevo.platform.Names
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
            sdk = Client(ClientOptions(network = args[0]))
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

            var startAt = 0
            val documents = hashMapOf<Identifier, DomainDocument>()
            val profiles = hashMapOf<Identifier, Profile>()
            val identities = hashMapOf<Identifier, Identity>()
            val contactRequests = hashMapOf<Identifier, List<ContactRequest>>()
            var requests = 0
            var transitionCount = 3 //identity + preorder + domain
            do {
                try {
                    val results = platform.names.search(text, Names.DEFAULT_PARENT_DOMAIN, false, startAtIndex = startAt)
                    val theseNames = hashMapOf<Identifier, DomainDocument>()
                    results.forEach {
                        val doc = DomainDocument(it)
                        if (doc.dashUniqueIdentityId != null) {
                            theseNames[doc.dashUniqueIdentityId!!] = doc
                            identities[doc.dashUniqueIdentityId!!] = platform.identities.get(doc.dashUniqueIdentityId!!)!!
                        }
                    }
                    documents.putAll(theseNames)
                    val theseProfiles = Profiles(platform).getList(theseNames.keys.toList())
                    theseProfiles.forEach {
                        val profile = Profile(it)
                        profiles[it.ownerId] = profile
                    }

                    theseNames.forEach {
                        val requests = ContactRequests(platform).get(it.key, false, 0, true, 0)
                        val list = requests.map { request -> ContactRequest(request) }
                        contactRequests[it.key] = list
                    }

                    requests += 1

                    startAt += Documents.DOCUMENT_LIMIT
                } catch (e: Exception) {
                    println("\nError retrieving results (startAt =  $startAt)")
                    println(e.message)
                }
            } while (requests == 0 || documents!!.size >= Documents.DOCUMENT_LIMIT)


            for (doc in documents) {
                println(
                    "Name: " + doc.value.label +
                            " (domain: " + doc.value.normalizedParentDomainName +
                            ") Identity: " + doc.value.ownerId
                )

                if (profiles.containsKey(doc.key)) {
                    val profile = profiles[doc.key]!!
                    println("  DisplayName: ${profile.displayName}")
                    println("  Public Message: ${profile.publicMessage}")
                    transitionCount += profile.revision + 1
                }
                val balance = identities[doc.key]!!.balance
                println("  Credit Balance:  $balance")
                val sentContactRequestCount = if (contactRequests[doc.key] != null) {
                    contactRequests[doc.key]!!.size
                } else {
                    0
                }
                println("  Sent Contact Requests:  $sentContactRequestCount")
                transitionCount += sentContactRequestCount

                println("  transitions made: $transitionCount")
                println("  credits/transition: ${(CreditsConverter.convertSatoshiToCredits(Coin.CENT) - balance) / transitionCount}")
            }

            if (documents.isEmpty()) {
                println("No names found starting with $text")
            }
        }
    }
}