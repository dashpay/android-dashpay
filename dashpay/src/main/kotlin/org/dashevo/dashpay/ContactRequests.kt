package org.dashevo.dashpay

import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.document.Document
import org.dashevo.platform.Documents
import org.dashevo.platform.Platform

class ContactRequests(val platform: Platform) {

    companion object {
        const val CONTACTREQUEST_DOCUMENT = "dashpay.contactRequest"
    }

    /**
     * Gets the contactRequest documents for the given userId
     * @param userId String
     * @param toUserId Boolean (true if getting toUserId, false if $userId)
     * @param retrieveAll Boolean get all results (true) or 100 at a time (false)
     * @param startAt Int where to start getting results
     * @return List<Documents>
     */
    fun get(userId: String, toUserId: Boolean, retrieveAll: Boolean = true, startAt: Int = 0): List<Document> {
        val documentQuery = DocumentQuery.Builder()

        if (toUserId)
            documentQuery.where(listOf("toUserId", "==", userId))
        else
            documentQuery.where(listOf("\$userId", "==", userId))

        // TODO: Refactor the rest of this code since it is also used in Names.search
        // TODO: This block of code can get all the results of a query, or 100 at a time
        var startAt = startAt
        var documents = ArrayList<Document>()
        var requests = 0

        do {
            try {
                val documentList =
                    platform.documents.get(CONTACTREQUEST_DOCUMENT, documentQuery.startAt(startAt).build())
                requests += 1
                startAt += Documents.DOCUMENT_LIMIT
                if (documentList.isNotEmpty())
                    documents.addAll(documentList)
            } catch (e: Exception) {
                throw e
            }
        } while ((requests == 0 || documents!!.size >= Documents.DOCUMENT_LIMIT) && retrieveAll)

        return documents
    }
}