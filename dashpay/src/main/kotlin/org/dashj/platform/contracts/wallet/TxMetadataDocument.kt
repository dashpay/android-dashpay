/**
 * Copyright (c) 2022-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashj.platform.contracts.wallet

import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.EncryptedData
import org.bitcoinj.crypto.KeyCrypterAESCBC
import org.bouncycastle.crypto.params.KeyParameter
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.dpp.util.Cbor
import org.dashj.platform.sdk.platform.AbstractDocument

class TxMetadataDocument(document: Document) : AbstractDocument(document) {

    companion object {
        // 2^16 + 2
        val childNumber = ChildNumber(2 shl 15 + 1, true)
        const val MAX_ENCRYPTED_SIZE = 4096 - 32 // leave room for a partially filled block and the IV
    }

    val keyIndex: Int
        get() = document.data["keyIndex"] as Int
    val encryptionKeyIndex: Int
        get() = document.data["encryptionKeyIndex"] as Int
    val encryptedMetadata: ByteArray
        get() = getFieldByteArray("encryptedMetadata")!!

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TxMetadataDocument

        return document.equals(other.document)
    }

    override fun toString(): String {
        return "TxMetadata(${document.toJSON()})"
    }

    fun decrypt(keyParameter: KeyParameter): List<TxMetadataItem> {
        val cipher = KeyCrypterAESCBC()
        // use AES-CBC-256 to obtain byte data
        val iv = encryptedMetadata.copyOfRange(0, 16)
        val encryptedData = encryptedMetadata.copyOfRange(16, encryptedMetadata.size)
        val decryptedData = cipher.decrypt(EncryptedData(iv, encryptedData), keyParameter)
        // use Cbor.decodeList
        val list = Cbor.decodeList(decryptedData)
        // use .map to convert to List<TxMetadataItem>
        return list.map { TxMetadataItem(it as Map<String, Any?>) }
    }
}
