/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashevo.dashpay

import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identifier.Identifier
import org.dashevo.platform.AbstractDocument

class ContactRequest(document: Document) : AbstractDocument(document) {
    val toUserId: Identifier
        get() = Identifier.from(document.data["toUserId"])
    val encryptedPublicKey: ByteArray
        get() = document.data["encryptedPublicKey"] as ByteArray
    val senderKeyIndex: Int
        get() = document.data["senderKeyIndex"] as Int
    val recipientKeyIndex: Int
        get() = document.data["recipientKeyIndex"] as Int
    val accountReference: Int
        get() = document.data["accountReference"] as Int
}