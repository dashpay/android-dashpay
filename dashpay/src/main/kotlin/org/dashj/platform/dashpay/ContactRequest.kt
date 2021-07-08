/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashj.platform.dashpay

import java.util.Date
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.sdk.platform.AbstractDocument
import org.dashj.platform.sdk.platform.Platform

class ContactRequest(document: Document) : AbstractDocument(document) {
    val toUserId: Identifier
        get() = Identifier.from(document.data["toUserId"])
    val encryptedPublicKey: ByteArray
        get() = getFieldByteArray("encryptedPublicKey")!!
    val senderKeyIndex: Int
        get() = document.data["senderKeyIndex"] as Int
    val recipientKeyIndex: Int
        get() = document.data["recipientKeyIndex"] as Int
    val accountReference: Int
        get() = document.data["accountReference"] as Int
    val version: Int
        get() = accountReference ushr 28
    val encryptedAccountLabel: ByteArray?
        get() = getFieldByteArray("encryptedAccountLabel")
    val autoAcceptProof: ByteArray?
        get() = getFieldByteArray("autoAcceptProof")
    val coreHeightCreatedAt: Int
        get() = document.data["coreHeightCreatedAt"] as Int

    class Builder(val platform: Platform) {
        val data = hashMapOf<String, Any?>()
        var ownerId: Identifier? = null

        fun from(userId: Identifier) = apply {
            ownerId = userId
        }

        fun to(userId: Identifier) = apply {
            data["toUserId"] = userId
        }

        fun encryptedPubKey(encryptedPublicKey: ByteArray, senderKeyIndex: Int, recipientKeyIndex: Int) = apply {
            data["encryptedPublicKey"] = encryptedPublicKey
            data["senderKeyIndex"] = senderKeyIndex
            data["recipientKeyIndex"] = recipientKeyIndex
        }

        fun accountReference(accountReference: Int) = apply {
            data["accountReference"] = accountReference
        }

        fun encryptedAccountLabel(encryptedAccountLabel: ByteArray) = apply {
            data["encryptedAccountLabel"] = encryptedAccountLabel
        }

        fun autoAcceptProof(autoAcceptProof: ByteArray) = apply {
            data["autoAcceptProof"] = autoAcceptProof
        }

        fun coreHeightCreatedAt(coreHeightCreatedAt: Int) = apply {
            data["coreHeightCreatedAt"] = coreHeightCreatedAt
        }

        fun build(): ContactRequest {
            data["\$createdAt"] = Date().time
            val document = platform.documents.create(ContactRequests.CONTACTREQUEST_DOCUMENT, ownerId!!, data)

            return ContactRequest(document)
        }
    }

    companion object {
        fun builder(platform: Platform): Builder {
            return Builder(platform)
        }
    }
}
