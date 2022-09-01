/**
 * Copyright (c) 2022-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashj.platform.contracts.wallet

import java.util.Date
import kotlin.collections.HashMap
import org.bitcoinj.core.ECKey
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.dpp.document.DocumentCreateTransition
import org.dashj.platform.dpp.document.DocumentsBatchTransition
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.dpp.identity.Identity
import org.dashj.platform.sdk.platform.Platform

class TxMetadata(
    val platform: Platform
) {

    companion object {
        const val DOCUMENT: String = "dashwallet.tx_metadata"
    }

    fun create(
        keyIndex: Int,
        encryptionKeyIndex: Int,
        encryptedMetadata: ByteArray,
        identity: Identity,
        id: Int,
        signingKey: ECKey
    ): Document {
        val profileDocument = createDocument(keyIndex, encryptionKeyIndex, encryptedMetadata, identity)
        profileDocument.createdAt = Date().time

        val transitionMap = hashMapOf(
            "create" to listOf(profileDocument)
        )

        val transition = signAndBroadcast(transitionMap, identity, id, signingKey)

        val rawDocument = transition.transitions[0].toObject().toMutableMap()
        rawDocument["\$ownerId"] = transition.ownerId

        return platform.dpp.document.createFromObject(rawDocument)
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

    fun createDocument(
        keyIndex: Int,
        encryptionKeyIndex: Int,
        encryptedMetadata: ByteArray,
        identity: Identity
    ): Document {
        val document = platform.documents.create(
            DOCUMENT,
            identity.id,
            mutableMapOf(
                "keyIndex" to keyIndex,
                "encryptionKeyIndex" to encryptionKeyIndex,
                "encryptedMetadata" to encryptedMetadata
            )
        )
        document.revision = DocumentCreateTransition.INITIAL_REVISION
        document.createdAt = Date().time
        return document
    }

    fun get(userId: String): List<Document> {
        return get(Identifier.from(userId))
    }

    fun get(userId: Identifier, createdAfter: Long = -1): List<Document> {
        val queryBuilder = DocumentQuery.Builder()
            .where("\$ownerId", "==", userId)
            .orderBy("\$createdAt")

        if (createdAfter != -1L) {
            queryBuilder.where(listOf("\$createdAt", ">=", createdAfter))
        }

        val query = queryBuilder.build()

        return platform.documents.get(DOCUMENT, query)
    }
}
