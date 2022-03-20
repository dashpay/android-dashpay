/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashj.platform.dashpay

import java.util.Date
import kotlin.collections.HashMap
import org.bitcoinj.core.ECKey
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.dpp.document.DocumentCreateTransition
import org.dashj.platform.dpp.document.DocumentsBatchTransition
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.dpp.identity.Identity
import org.dashj.platform.sdk.platform.Documents
import org.dashj.platform.sdk.platform.Platform

class Profiles(
    val platform: Platform
) {

    companion object {
        const val DOCUMENT: String = "dashpay.profile"
    }

    fun create(
        displayName: String,
        publicMessage: String,
        avatarUrl: String?,
        avatarHash: ByteArray?,
        avatarFingerprint: ByteArray?,
        identity: Identity,
        id: Int,
        signingKey: ECKey
    ): Document {
        val profileDocument = createProfileDocument(displayName, publicMessage, avatarUrl, avatarHash, avatarFingerprint, identity)
        profileDocument.createdAt = Date().time

        val transitionMap = hashMapOf(
            "create" to listOf(profileDocument)
        )

        val transition = signAndBroadcast(transitionMap, identity, id, signingKey)

        return platform.dpp.document.createFromObject(transition.transitions[0].toObject())
    }

    fun replace(
        displayName: String,
        publicMessage: String,
        avatarUrl: String?,
        avatarHash: ByteArray?,
        avatarFingerprint: ByteArray?,
        identity: Identity,
        id: Int,
        signingKey: ECKey
    ): Document {
        val currentProfile = get(identity.id)

        val profileData = hashMapOf<String, Any?>()
        profileData.putAll(currentProfile!!.toJSON())
        profileData["displayName"] = displayName
        profileData["publicMessage"] = publicMessage
        profileData["avatarUrl"] = avatarUrl
        profileData["avatarHash"] = avatarHash
        profileData["avatarFingerprint"] = avatarFingerprint

        val profileDocument = platform.dpp.document.createFromObject(profileData)
        profileDocument.updatedAt = Date().time

        val transitionMap = hashMapOf(
            "replace" to listOf(profileDocument)
        )

        val transition = signAndBroadcast(transitionMap, identity, id, signingKey)

        return platform.dpp.document.createFromObject(transition.transitions[0].toObject())
    }

    private fun signAndBroadcast(
        transitionMap: HashMap<String, List<Document>>,
        identity: Identity,
        id: Int,
        signingKey: ECKey
    ): DocumentsBatchTransition {
        val profileStateTransition =
            platform.dpp.document.createStateTransition(transitionMap)
        profileStateTransition.sign(identity.getPublicKeyById(id)!!, signingKey.privateKeyAsHex)
        platform.broadcastStateTransition(profileStateTransition)
        return profileStateTransition
    }

    fun createProfileDocument(
        displayName: String?,
        publicMessage: String?,
        avatarUrl: String?,
        avatarHash: ByteArray?,
        avatarFingerprint: ByteArray?,
        identity: Identity,
        revision: Int = DocumentCreateTransition.INITIAL_REVISION
    ): Document {
        val document = platform.documents.create(
            DOCUMENT, identity.id,
            mutableMapOf<String, Any?>(
                "publicMessage" to publicMessage,
                "displayName" to displayName,
                "avatarUrl" to avatarUrl,
                "avatarHash" to avatarHash,
                "avatarFingerprint" to avatarFingerprint
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

    fun get(userId: Identifier, updatedAt: Long = -1): Document? {
        val queryBuilder = DocumentQuery.Builder()
            .where("\$ownerId", "==", userId)

        if (updatedAt != -1L) {
            queryBuilder.where(listOf("\$updatedAt", "==", updatedAt))
        }

        val query = queryBuilder.build()
        try {
            val documents = platform.documents.get(DOCUMENT, query)
            return if (documents.isNotEmpty()) documents[0] else null
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Returns all profiles associated with the given identity ids
     *
     * @param userIds The identities for which to obtain the profiles.  This supports more than 100 identities
     * @param timestamp The timestamp that the profile updatedAt time must be after
     */
    fun getList(
        userIds: List<Identifier>,
        timestamp: Long = 0L
    ): List<Document> {
        var startAt = 0 // this parameter is not used by a getDocuments query, so 0 is good
        val documents = ArrayList<Document>()

        while (startAt < userIds.size) {
            val subsetSize = if (startAt + Documents.DOCUMENT_LIMIT > userIds.size) {
                userIds.size - startAt
            } else {
                Documents.DOCUMENT_LIMIT
            }
            val userIdSubSet = userIds.subList(startAt, startAt + subsetSize)
            val documentSubset = getListHelper(userIdSubSet, timestamp)
            documents.addAll(documentSubset)
            startAt += subsetSize
        }

        return documents
    }

    /**
     * gets a list of profiles using the identities in userIds (max 100)
     *
     * This query never has more than 100 results, since userIds are primary keys
     */
    private fun getListHelper(
        userIds: List<Identifier>,
        timestamp: Long = 0L
    ): List<Document> {
        val documentQuery = DocumentQuery.builder()
            .whereIn("\$ownerId", userIds)
            .where("\$updatedAt", ">", timestamp)
            .orderBy("\$ownerId", true)
            .orderBy("\$updatedAt", true)
            .build()

        return platform.documents.get(DOCUMENT, documentQuery)
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
