package org.dashj.platform.dashpay

import org.dashj.platform.dpp.document.Document
import org.dashj.platform.sdk.platform.AbstractDocument

class Profile(document: Document) : AbstractDocument(document) {

    val displayName: String?
        get() = getFieldString("displayName")
    val publicMessage: String?
        get() = getFieldString("publicMessage")
    val avatarUrl: String?
        get() = getFieldString("avatarUrl")
    val avatarHash: ByteArray?
        get() = getFieldByteArray("avatarHash")
    val avatarFingerprint: ByteArray?
        get() = getFieldByteArray("avatarFingerprint")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Profile

        return document.equals(other.document)
    }

    override fun toString(): String {
        return "Profile(${document.toJSON()})"
    }
}
