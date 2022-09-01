package org.dashj.platform.contracts.wallet

import com.google.common.primitives.Ints
import org.dashj.platform.dpp.toHex

/**
 * Transaction metadata item
 *
 * @property txId The transaction id (hash) in big endian format
 * @property memo
 * @property exchangeRate
 * @property currencyCode
 * @property taxCategory
 * @property service
 * @constructor Create empty Tx metadata item
 */

class TxMetadataItem(
    val txId: ByteArray,
    val memo: String? = null,
    val exchangeRate: Double? = null,
    val currencyCode: String? = null,
    val taxCategory: String? = null,
    val service: String? = null,
    val version: Int = 0
) {
    val data = hashMapOf<String, Any?>()

    constructor(rawObject: Map<String, Any?>) :
    this(
        rawObject["txId"] as ByteArray,
        rawObject["memo"] as? String,
        rawObject["exchangeRate"] as? Double,
        rawObject["currencyCode"] as? String,
        rawObject["taxCategory"] as? String,
        rawObject["service"] as? String,
        rawObject["version"] as Int
    ) {
        data.putAll(rawObject)
    }

    fun toObject(): Map<String, Any?> {
        val map = hashMapOf<String, Any?>(
            "txId" to txId,
            "version" to version
        )

        memo?.let {
            map["memo"] = it
        }

        exchangeRate?.let {
            map["exchangeRate"] = it
        }

        currencyCode?.let {
            map["currencyCode"] = it
        }

        taxCategory?.let {
            map["taxCategory"] = it
        }

        service?.let {
            map["service"] = it
        }

        return map
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (other is TxMetadataItem) {
            return txId.contentEquals(other.txId) &&
                version == other.version
            memo == other.memo &&
                exchangeRate == other.exchangeRate &&
                currencyCode == other.currencyCode &&
                taxCategory == other.taxCategory &&
                service == other.service
        }
        return false
    }

    override fun hashCode(): Int {
        return Ints.fromBytes(
            txId.get(3),
            txId.get(2),
            txId.get(1),
            txId.get(0)
        )
    }

    override fun toString(): String {
        return "TxMetadataItem(ver=$version, ${txId.toHex()}, memo=$memo, rate=$exchangeRate, code=$currencyCode, taxCategory=$taxCategory, service=$service}"
    }
}
