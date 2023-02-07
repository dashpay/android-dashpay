/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashj.platform.contracts.wallet

import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.KeyCrypterAESCBC
import org.bitcoinj.crypto.KeyCrypterException
import org.dashj.platform.assertListEquals
import org.dashj.platform.assertMapEquals
import org.dashj.platform.dashpay.BlockchainIdentity
import org.dashj.platform.dashpay.PlatformNetwork
import org.dashj.platform.dpp.identity.IdentityPublicKey
import org.dashj.platform.dpp.util.Cbor
import org.dashj.platform.dpp.util.Converters
import org.dashj.platform.dpp.util.Entropy
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TxMetaDataTests : PlatformNetwork() {
    lateinit var txId: ByteArray
    lateinit var txMetadataItem: TxMetadataItem
    lateinit var txMetadataItemTwo: TxMetadataItem
    lateinit var txMetadataItems: List<TxMetadataItem>

    @BeforeEach
    fun beforeEach() {
        txId = Entropy.generateRandomBytes(32)
        txMetadataItem = TxMetadataItem(
            txId,
            System.currentTimeMillis() / 1000,
            "Alice's Pizza Party",
            51.00,
            "USD",
            "expense",
            null,
            version = 0
        )

        txMetadataItemTwo = TxMetadataItem(
            Converters.fromHex("c44d1077cd4628d0ac06e22032a4e8458f9d01be6342453de3eef88657b193ce"),
            System.currentTimeMillis() / 1000,
            "Bob's Burger Joint",
            52.23,
            "USD",
            "expense",
            "DashDirect"
        )

        txMetadataItems = listOf(txMetadataItem, txMetadataItemTwo)
    }

    @Test
    fun toObjectTest() {
        assertMapEquals(
            mapOf(
                "txId" to txId,
                "memo" to "Alice's Pizza Party",
                "exchangeRate" to 51.00,
                "currencyCode" to "USD",
                "taxCategory" to "expense",
                "version" to 0
            ),
            txMetadataItem.toObject()
        )
    }

    @Test
    fun toCborTestForOneItemTest() {
        assertArrayEquals(
            Converters.fromHex("a7646d656d6f72426f62277320427572676572204a6f696e7464747849645820c44d1077cd4628d0ac06e22032a4e8458f9d01be6342453de3eef88657b193ce67736572766963656a446173684469726563746776657273696f6e006b74617843617465676f727967657870656e73656c63757272656e6379436f6465635553446c65786368616e676552617465fb404a1d70a3d70a3d"),
            Cbor.encode(txMetadataItemTwo.toObject())
        )
    }

    @Test
    fun toCborTestForAllItemsTest() {
        val cborData = Cbor.encode(txMetadataItems.map { it.toObject() })
        val map = Cbor.decodeList(cborData)
    }

    @Test
    fun roundTripTest() {
        val blockchainIdentity = BlockchainIdentity(platform, 0, wallet)

        val privateKey = blockchainIdentity.privateKeyAtPath(1, TxMetadataDocument.childNumber, 0, IdentityPublicKey.Type.ECDSA_SECP256K1, null)

        val metadataBytes = Cbor.encode(txMetadataItems.map { it.toObject() })

        // encrypt data
        val cipher = KeyCrypterAESCBC()
        val keyParameter = cipher.deriveKey(privateKey)
        val encryptedData = cipher.encrypt(metadataBytes, keyParameter)

        // now decrypt
        val decryptedData = cipher.decrypt(encryptedData, keyParameter)
        assertArrayEquals(metadataBytes, decryptedData)

        val decryptedList = Cbor.decodeList(decryptedData)

        assertListEquals(txMetadataItems, decryptedList.map { TxMetadataItem(it as Map<String, Any?>) })

        // use the wrong key to decrypt
        val incorrectKey = blockchainIdentity.privateKeyAtPath(1, ChildNumber.ONE_HARDENED, 0, IdentityPublicKey.Type.ECDSA_SECP256K1, null)
        val incorrectKeyParameter = cipher.deriveKey(incorrectKey)
        assertThrows<KeyCrypterException.InvalidCipherText> {
            cipher.decrypt(encryptedData, incorrectKeyParameter)
        }
    }

    @Test
    fun emptyListTest() {
        val blockchainIdentity = BlockchainIdentity(platform, 0, wallet)

        val privateKey = blockchainIdentity.privateKeyAtPath(1, TxMetadataDocument.childNumber, 0, IdentityPublicKey.Type.ECDSA_SECP256K1, null)

        val metadataBytes = Cbor.encode(listOf<TxMetadataItem>())

        // encrypt data
        val cipher = KeyCrypterAESCBC()
        val keyParameter = cipher.deriveKey(privateKey)
        val encryptedData = cipher.encrypt(metadataBytes, keyParameter)

        // 32 bytes = IV (16 bytes) + 1 block (16 bytes)
        assertEquals(32, encryptedData.initialisationVector.size + encryptedData.encryptedBytes.size)
    }
}
