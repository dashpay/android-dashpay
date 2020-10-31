/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashevo.dashpay

import org.bitcoinj.core.ECKey
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.document.*
import org.dashevo.dpp.identifier.Identifier
import org.dashevo.dpp.identity.Identity
import org.dashevo.platform.Documents
import org.dashevo.platform.Platform
import java.util.*
import kotlin.collections.HashMap

class Profiles(
    val platform: Platform
) {

    companion object {
        private const val DOCUMENT: String = "dashpay.profile"
    }

    fun create(
        displayName: String,
        publicMessage: String,
        avatarUrl: String?,
        identity: Identity,
        id: Int,
        signingKey: ECKey
    ) : Document {
        val profileDocument = createProfileDocument(displayName, publicMessage, avatarUrl, identity)
        profileDocument.createdAt = Date().time

        val transitionMap = hashMapOf(
            "create" to listOf(profileDocument)
        )

        val transition = signAndBroadcast(transitionMap, identity, id, signingKey)

        return DocumentFactory().createFromObject(transition.transitions[0].toJSON().toMutableMap())

    }

    fun replace(
        displayName: String,
        publicMessage: String,
        avatarUrl: String?,
        identity: Identity,
        id: Int,
        signingKey: ECKey
    ) : Document {
        val currentProfile = get(identity.id)

        val profileData = hashMapOf<String, Any?>()
        profileData.putAll(currentProfile!!.toJSON())
        profileData["displayName"] = displayName
        profileData["publicMessage"] = publicMessage
        profileData["avatarUrl"] = avatarUrl

        val profileDocument = DocumentFactory().createFromObject(profileData)
        profileDocument.updatedAt = Date().time

        val transitionMap = hashMapOf(
            "replace" to listOf(profileDocument)
        )

        val transition = signAndBroadcast(transitionMap, identity, id, signingKey)

        return DocumentFactory().createFromObject(transition.transitions[0].toJSON().toMutableMap())
    }

    private fun signAndBroadcast(
        transitionMap: HashMap<String, List<Document>>,
        identity: Identity,
        id: Int,
        signingKey: ECKey
    ) : DocumentsBatchTransition {
        val profileStateTransition =
            platform.dpp.document.createStateTransition(transitionMap)
        profileStateTransition.sign(identity.getPublicKeyById(id)!!, signingKey.privateKeyAsHex)
        platform.client.broadcastStateTransition(profileStateTransition)
        return profileStateTransition
    }

    fun createProfileDocument(
        displayName: String?,
        publicMessage: String?,
        avatarUrl: String?,
        identity: Identity,
        revision: Int = DocumentCreateTransition.INITIAL_REVISION
    ): Document {
        val document = platform.documents.create(
            DOCUMENT, identity.id,
            mutableMapOf<String, Any?>(
                "publicMessage" to publicMessage,
                "displayName" to displayName,
                "avatarUrl" to avatarUrl
            )
        )
        document.revision = revision
        if (revision == DocumentCreateTransition.INITIAL_REVISION) {
            document.createdAt = Date().time
            document.updatedAt = document.createdAt
        } else {
            document.updatedAt = Date().time
        }
        return document
    }

    fun get(userId: String): Document? {
        return get(Identifier.from(userId))
    }

    fun get(userId: Identifier): Document? {
        val query = DocumentQuery.Builder()
            .where("\$ownerId", "==", userId)
            .build()
        try {
            val documents = platform.documents.get(DOCUMENT, query)
            return if (documents.isNotEmpty()) documents[0] else null
        } catch (e: Exception) {
            throw e
        }
    }

    //TODO: handle case where userIds's contains more than 100 items
    fun getList(
        userIds: List<Identifier>,
        timestamp: Long = 0L,
        retrieveAll: Boolean = true,
        startAt: Int = 0
    ): List<Document> {
        val documentQuery = DocumentQuery.Builder()
        documentQuery.whereIn("\$ownerId", userIds)
            .where(listOf("\$updatedAt", ">", timestamp))

        var requests = 0

        val documents = arrayListOf<Document>()
        do {
            val result = platform.documents.get(DOCUMENT, documentQuery.startAt(startAt).build())
            documents.addAll(result)
            requests += 1
        } while ((requests == 0 || result.size >= Documents.DOCUMENT_LIMIT) && retrieveAll)

        return documents
    }

    suspend fun watchProfile(
        userId: String,
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType
    ): Document? {
        val documentQuery = DocumentQuery.Builder()
        documentQuery.where("\$ownerId", "==", userId)
        val result = platform.documents.get(DOCUMENT, documentQuery.build())

        if (result.isNotEmpty()) {
            return result[0]
        } else {
            if (retryCount > 0) {
                val nextDelay = delayMillis * when (retryDelayType) {
                    RetryDelayType.SLOW20 -> 5 / 4
                    RetryDelayType.SLOW50 -> 3 / 2
                    else -> 1
                }
                kotlinx.coroutines.delay(nextDelay)
                return watchProfile(userId, retryCount - 1, nextDelay, retryDelayType)
            }
        }
        return null
    }
}