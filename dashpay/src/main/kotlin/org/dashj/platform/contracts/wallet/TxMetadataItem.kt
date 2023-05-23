/**
 * Copyright (c) 2022-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashj.platform.contracts.wallet

import com.google.common.primitives.Ints
import org.dashj.platform.dpp.toHex
import org.dashj.platform.dpp.util.Cbor
/**
 * Transaction metadata item
 *
 * @property txId The transaction id (hash) in big endian format
 * @property memo
 * @property exchangeRate
 * @property currencyCode
 * @property taxCategory
 * @property service
 * @property customIconUrl
 * @property giftCardNumber
 * @property giftCardPin
 * @property merchantName
 * @property originalPrice
 * @property barcodeValue
 * @property barcodeFormat
 * @property merchantUrl
 * @constructor Create empty Tx metadata item
 */

class TxMetadataItem(
    val txId: ByteArray,
    val timestamp: Long? = 0,
    val memo: String? = null,
    val exchangeRate: Double? = null,
    val currencyCode: String? = null,
    val taxCategory: String? = null,
    val service: String? = null,
    val customIconUrl: String? = null,
    val giftCardNumber: String? = null,
    val giftCardPin: String? = null,
    val merchantName: String? = null,
    val originalPrice: Double? = null,
    val barcodeValue: String? = null,
    val barcodeFormat: String? = null,
    val merchantUrl: String? = null,
    val version: Int = 0
) {
    val data = hashMapOf<String, Any?>()

    constructor(rawObject: Map<String, Any?>) : this(
        rawObject["txId"] as ByteArray,
        rawObject["timestamp"] as? Long,
        rawObject["memo"] as? String,
        rawObject["exchangeRate"] as? Double,
        rawObject["currencyCode"] as? String,
        rawObject["taxCategory"] as? String,
        rawObject["service"] as? String,
        rawObject["customIconUrl"] as? String,

        // Gift Cards
        rawObject["giftCardNumber"] as? String,
        rawObject["giftCardPin"] as? String,
        rawObject["merchantName"] as? String,
        rawObject["originalPrice"] as? Double,
        rawObject["barcodeValue"] as? String,
        rawObject["barcodeFormat"] as? String,
        rawObject["merchantUrl"] as? String,

        rawObject["version"] as Int
    ) {
        data.putAll(rawObject)
    }

    fun toObject(): Map<String, Any?> {
        val map = hashMapOf<String, Any?>(
            "txId" to txId,
            "timestamp" to timestamp,
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

        customIconUrl?.let {
            map["customIconUrl"] = it
        }

        giftCardNumber?.let {
            map["giftCardNumber"] = it
        }

        giftCardPin?.let {
            map["giftCardPin"] = it
        }

        merchantName?.let {
            map["merchantName"] = it
        }

        originalPrice?.let {
            map["originalPrice"] = it
        }

        barcodeValue?.let {
            map["barcodeValue"] = it
        }

        barcodeFormat?.let {
            map["barcodeFormat"] = it
        }

        merchantUrl?.let {
            map["merchantUrl"] = it
        }

        return map
    }
    fun getSize(): Int {
        return Cbor.encode(toObject()).size
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (other is TxMetadataItem) {
            return txId.contentEquals(other.txId) &&
                version == other.version &&
                memo == other.memo &&
                exchangeRate == other.exchangeRate &&
                currencyCode == other.currencyCode &&
                taxCategory == other.taxCategory &&
                service == other.service &&
                customIconUrl == other.customIconUrl &&
                giftCardNumber == other.giftCardNumber &&
                giftCardPin == other.giftCardPin &&
                merchantName == other.merchantName &&
                originalPrice == other.originalPrice &&
                barcodeValue == other.barcodeValue &&
                barcodeFormat == other.barcodeFormat &&
                merchantUrl == other.merchantUrl
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
        return "TxMetadataItem(ver=$version, ${txId.toHex()}, memo=$memo, rate=$exchangeRate, " +
            "code=$currencyCode, taxCategory=$taxCategory, service=$service, customIconUrl=$customIconUrl, " +
            "giftCardNumber=$giftCardNumber, giftCardPin=$giftCardPin, merchantName=$merchantName, " +
            "originalPrice=$originalPrice, barcodeValue=$barcodeValue, barcodeFormat=$barcodeFormat, merchantUrl=$merchantUrl)"
    }

    fun isNotEmpty(): Boolean {
        return (timestamp != null && timestamp != 0L) || taxCategory != null || memo != null ||
            currencyCode != null || exchangeRate != null || service != null || customIconUrl != null ||
            giftCardNumber != null || giftCardPin != null || merchantName != null || originalPrice != null ||
            barcodeValue != null || barcodeFormat != null || merchantUrl != null
    }
}
