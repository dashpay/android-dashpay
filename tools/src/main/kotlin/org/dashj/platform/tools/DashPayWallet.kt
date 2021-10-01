/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.tools

import com.google.common.base.Stopwatch
import io.grpc.StatusRuntimeException
import java.lang.Long.max
import java.util.concurrent.atomic.AtomicBoolean
import org.bitcoinj.core.Context
import org.bitcoinj.core.PeerGroup
import org.bitcoinj.crypto.KeyCrypterException
import org.bitcoinj.evolution.EvolutionContact
import org.bouncycastle.crypto.params.KeyParameter
import org.dashj.platform.dashpay.BlockchainIdentity
import org.dashj.platform.dashpay.Contact
import org.dashj.platform.dashpay.ContactRequest
import org.dashj.platform.dashpay.ContactRequests
import org.dashj.platform.dashpay.Profile
import org.dashj.platform.dashpay.Profiles
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.sdk.platform.DomainDocument
import org.slf4j.LoggerFactory

class DashPayWallet(val blockchainIdentity: BlockchainIdentity, val peerGroup: PeerGroup?, val password: String? = null) {
    val wallet = blockchainIdentity.wallet!!
    val platform = blockchainIdentity.platform
    val contactRequests = arrayListOf<ContactRequest>()
    val profiles = hashMapOf<Identifier, Profile>()
    val names = hashMapOf<Identifier, DomainDocument>()

    private val preDownloadBlocks = AtomicBoolean(true)
    private val log = LoggerFactory.getLogger(DashPayWallet::class.java)

    val updatingContacts = AtomicBoolean(false)

    fun getContactRequestLastTimestamp(): Long {
        var lastTimestamp = 0L
        contactRequests.forEach {
            lastTimestamp = max(lastTimestamp, it.createdAt ?: 0L)
        }
        return lastTimestamp
    }

    fun getSentContactRequests(): List<ContactRequest> {
        return contactRequests.filter {
            it.ownerId == blockchainIdentity.uniqueIdentifier
        }
    }

    fun getRecievedContactRequests(): List<ContactRequest> {
        return contactRequests.filter {
            it.toUserId == blockchainIdentity.uniqueIdentifier
        }
    }

    fun getSentContactRequestsMap(): Map<Identifier, ContactRequest> {
        return getSentContactRequests().associateBy({ it.toUserId }, { it })
    }

    fun getRecievedContactRequestsMap(): Map<Identifier, ContactRequest> {
        return getRecievedContactRequests().associateBy({ it.ownerId }, { it })
    }

    fun getContactIdentities(): Set<Identifier> {
        val result = hashSetOf<Identifier>()
        getRecievedContactRequests().forEach {
            result.add(it.ownerId)
        }
        getSentContactRequests().forEach {
            result.add(it.toUserId)
        }
        return result
    }

    // contacts
    fun updateContactRequests() {
        try {
            // only allow this method to execute once at a time
            if (updatingContacts.get()) {
                log.info("updateContactRequests is already running")
                return
            }

            if (!platform.hasApp("dashpay")) {
                log.info("update contacts not completed because there is no dashpay contract")
                return
            }

            if (blockchainIdentity.registrationStatus != BlockchainIdentity.RegistrationStatus.REGISTERED) {
                log.info("update contacts not completed username registration/recovery is not complete")
                return
            }

            val userId = blockchainIdentity.uniqueIdentifier
            val userIdString = blockchainIdentity.uniqueIdString

            if (blockchainIdentity.currentUsername == null) {
                return // this is here because the wallet is being reset without removing blockchainIdentityData
            }

            val userIdList = HashSet<Identifier>()
            val watch = Stopwatch.createStarted()
            var addedContact = false
            Context.propagate(wallet.context)
            var encryptionKey: KeyParameter? = null

            var lastContactRequestTime = if (contactRequests.size > 0) {
                getContactRequestLastTimestamp() - (60 * 1000) * 12
            } else {
                0L
            }

            updatingContacts.set(true)
            // checkDatabaseIntegrity()

            // Get all out our contact requests
            val toContactDocuments = ContactRequests(platform).get(userId, toUserId = false, afterTime = lastContactRequestTime, retrieveAll = true)
            toContactDocuments.forEach {
                val contactRequest = ContactRequest(it)
                userIdList.add(contactRequest.toUserId)
                contactRequests.add(contactRequest)

                // add our receiving from this contact keychain if it doesn't exist
                val contact = EvolutionContact(userIdString, contactRequest.toUserId.toString())
                try {
                    if (!wallet.hasReceivingKeyChain(contact)) {
                        val contactIdentity = platform.identities.get(contactRequest.toUserId)
                        if (encryptionKey == null && wallet.isEncrypted) {
                            // Don't bother with DeriveKeyTask here, just call deriveKey
                            encryptionKey = wallet.keyCrypter!!.deriveKey(password)
                        }
                        blockchainIdentity.addPaymentKeyChainFromContact(contactIdentity!!, contactRequest, encryptionKey)
                        addedContact = true
                    }
                } catch (e: KeyCrypterException) {
                    // we can't send payments to this contact due to an invalid encryptedPublicKey
                    log.info("ContactRequest: error ${e.message}")
                }
            }
            // Get all contact requests where toUserId == userId, the users who have added me
            val fromContactDocuments = ContactRequests(platform).get(userId, toUserId = true, afterTime = lastContactRequestTime, retrieveAll = true)
            fromContactDocuments.forEach {
                val contactRequest = ContactRequest(it)
                userIdList.add(contactRequest.ownerId)
                contactRequests.add(contactRequest)

                // add the sending to contact keychain if it doesn't exist
                val contact = EvolutionContact(userIdString, contactRequest.ownerId.toString())
                try {
                    if (!wallet.hasSendingKeyChain(contact)) {
                        val contactIdentity = platform.identities.get(contactRequest.ownerId)
                        if (encryptionKey == null && wallet.isEncrypted) {
                            encryptionKey = wallet.keyCrypter!!.deriveKey(password)
                        }
                        blockchainIdentity.addContactPaymentKeyChain(contactIdentity!!, it, encryptionKey)
                        addedContact = true
                    }
                } catch (e: KeyCrypterException) {
                    // we can't send payments to this contact due to an invalid encryptedPublicKey
                    log.info("ContactRequest: error ${e.message}")
                }
            }

            // If new keychains were added to the wallet, then update the bloom filters
            if (addedContact) {
                peerGroup?.recalculateFastCatchupAndFilter(PeerGroup.FilterRecalculateMode.SEND_IF_CHANGED)
            }

            // obtain profiles from new contacts
            if (userIdList.isNotEmpty()) {
                updateContactProfiles(userIdList.toList(), 0L)
            }

            // fetch updated profiles from the network
            updateContactProfiles(lastContactRequestTime)

            // fire listeners if there were new contacts
            if (fromContactDocuments.isNotEmpty() || toContactDocuments.isNotEmpty()) {
                //    fireContactsUpdatedListeners()
            }

            log.info("updating contacts and profiles took $watch")
        } catch (e: Exception) {
            log.error(formatExceptionMessage("error updating contacts", e))
        } finally {
            updatingContacts.set(false)
            if (preDownloadBlocks.get()) {
                log.info("PreDownloadBlocks: complete")
                peerGroup?.triggerPreBlockDownloadComplete()
                preDownloadBlocks.set(false)
            }
        }
    }

    fun getEstablishedContacts(): List<Contact> {
        val establishedContacts = arrayListOf<Contact>()
        val sentRequests = getSentContactRequestsMap()
        val receivedRequests = getRecievedContactRequestsMap()
        val sentToIds = sentRequests.keys

        for (id in sentToIds) {
            val receivedRequest = receivedRequests[id]
            if (receivedRequest != null) {
                establishedContacts.add(Contact(names[id]!!.label, sentRequests[id], receivedRequests[id]))
            }
        }
        return establishedContacts
    }

    /**
     * Fetches updated profiles associated with contacts of userId after lastContactRequestTime
     */
    private fun updateContactProfiles(lastContactRequestTime: Long) {
        val watch = Stopwatch.createStarted()
        val userIdSet = hashSetOf<Identifier>()

        val toContactDocuments = getSentContactRequests()
        toContactDocuments!!.forEach {
            userIdSet.add(it.toUserId)
        }
        val fromContactDocuments = getRecievedContactRequests()
        fromContactDocuments!!.forEach {
            userIdSet.add(it.ownerId)
        }

        // Also add our ownerId to get our profile, in case it was updated on a different device
        userIdSet.add(blockchainIdentity.uniqueIdentifier)

        updateContactProfiles(userIdSet.toList(), lastContactRequestTime)
        log.info("updating contacts and profiles took $watch")
    }

    fun getIdentityForName(nameDocument: Document): Identifier {
        val records = nameDocument.data["records"] as Map<String, Any?>
        return Identifier.from(records["dashUniqueIdentityId"])
    }

    /**
     * Fetches updated profiles of users in userIdList after lastContactRequestTime
     *
     * if lastContactRequestTime is 0, then all profiles are retrieved
     */
    private fun updateContactProfiles(userIdList: List<Identifier>, lastContactRequestTime: Long, checkingIntegrity: Boolean = false) {
        if (userIdList.isNotEmpty()) {
            val identifierList = userIdList.map { Identifier.from(it) }
            val profileDocuments =
                Profiles(platform).getList(identifierList, lastContactRequestTime) // only handles 100 userIds
            val profileById = profileDocuments.associateBy({ it.ownerId }, { it })

            val nameDocuments = platform.names.getList(identifierList)
            val nameById = nameDocuments.associateBy({ getIdentityForName(it) }, { it })

            for (id in nameById.keys) {
                val nameDocument = nameById[id] // what happens if there is no username for the identity? crash
                val username = nameDocument!!.data["normalizedLabel"] as String
                val identityId = getIdentityForName(nameDocument)
                val name = DomainDocument(nameDocument)
                names[identityId] = name

                val profileDocument = profileById[id]

                if (profileDocument != null) {
                    profiles[id] = Profile(profileDocument)
                }

                if (checkingIntegrity) {
                    log.info("check database integrity: adding missing profile $username:$id")
                }
            }
        }
    }

    private fun formatExceptionMessage(description: String, e: Exception): String {
        var msg = if (e.localizedMessage != null) {
            e.localizedMessage
        } else {
            e.message
        }
        if (msg == null) {
            msg = "Unknown error - ${e.javaClass.simpleName}"
        }
        log.error("$description: $msg")
        if (e is StatusRuntimeException) {
            log.error("---> ${e.trailers}")
        }
        log.error(msg)
        e.printStackTrace()
        return msg
    }
}
