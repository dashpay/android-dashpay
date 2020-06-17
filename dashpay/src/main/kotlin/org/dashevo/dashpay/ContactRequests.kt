package org.dashevo.dashpay

import org.bitcoinj.core.Base58
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.util.encoders.Base64
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identity.Identity
import org.dashevo.platform.Documents
import org.dashevo.platform.Platform
import java.util.*
import kotlin.collections.ArrayList

class ContactRequests(val platform: Platform) {

    companion object {
        const val CONTACTREQUEST_DOCUMENT = "dashpay.contactRequest"
    }

    fun create(fromUser: BlockchainIdentity, toUser: Identity, aesKey: KeyParameter) {
        val encryptedPublicKey = fromUser.getReceiveFromContactChain(toUser, aesKey)
        val xpubBase58 = encryptedPublicKey.watchingKey.serializePubB58(platform.params)
        val xpubBase64 = Base64.encode(Base58.decode(xpubBase58)).toString()
        val timeStamp = Date().time

        val contactRequestDocument = platform.documents.create(
            CONTACTREQUEST_DOCUMENT, fromUser.identity!!, mutableMapOf(
                "encryptedPublicKey" to xpubBase64,
                "recipientKeyIndex" to toUser.publicKeys[0].id,
                "senderKeyIndex" to fromUser.identity!!.publicKeys[0].id,
                "toUserId" to toUser.id,
                "timestamp" to timeStamp
            )
        )

        val transition = platform.dpp.document.createStateTransition(listOf(contactRequestDocument))
        fromUser.signStateTransition(transition, aesKey)

        platform.client.applyStateTransition(transition)
    }

    /**
     * Gets the contactRequest documents for the given userId
     * @param userId String
     * @param toUserId Boolean (true if getting toUserId, false if $userId)
     * @param retrieveAll Boolean get all results (true) or 100 at a time (false)
     * @param startAt Int where to start getting results
     * @return List<Documents>
     */
    fun get(
        userId: String,
        toUserId: Boolean,
        retrieveAll: Boolean = true,
        startAt: Int = 0
    ): List<Document> {
        val documentQuery = DocumentQuery.Builder()

        if (toUserId)
            documentQuery.where(listOf("toUserId", "==", userId))
        else
            documentQuery.where(listOf("\$userId", "==", userId))

        // TODO: Refactor the rest of this code since it is also used in Names.search
        // TODO: This block of code can get all the results of a query, or 100 at a time
        var startAt = startAt
        val documents = ArrayList<Document>()
        var documentList: List<Document>
        var requests = 0

        do {
            try {
                documentList =
                    platform.documents.get(
                        CONTACTREQUEST_DOCUMENT,
                        documentQuery.startAt(startAt).build()
                    )
                requests += 1
                startAt += Documents.DOCUMENT_LIMIT
                if (documentList.isNotEmpty())
                    documents.addAll(documentList)
            } catch (e: Exception) {
                throw e
            }
        } while ((requests == 0 || documentList.size >= Documents.DOCUMENT_LIMIT) && retrieveAll)

        return documents
    }
}