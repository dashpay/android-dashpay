/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.bitcoinj.core.Base58
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.params.EvoNetParams
import org.dashevo.dapiclient.DapiClient
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identity.Identity
import org.dashevo.dpp.util.Entropy
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.Thread.sleep

class Names(val platform: Platform) {

    companion object {
        const val DEFAULT_PARENT_DOMAIN = "dash"
        const val DPNS_DOMAIN_DOCUMENT = "dpns.domain"
        const val DPNS_PREORDER_DOCUMENT = "dpns.preorder"

        // 5620 (hex) is the prefix for a hash that is used
        // in DPNS related documents (preorder, domain)
        // 56 = SHA256D
        // 20 = 32 bytes
        val HASH_PREFIX_BYTES = byteArrayOf(0x56, 0x20)
        const val HASH_PREFIX_STRING = "5620"
    }

    fun register(name: String, identity: Identity, identityHDPrivateKey: ECKey): Document? {
        val entropy = Entropy.generate()
        val document = preorder(name, identity, identityHDPrivateKey, entropy)
        return if (document != null) {
            registerName(name, identity, identityHDPrivateKey, entropy, document)
        } else null
    }

    fun preorder(name: String, identity: Identity, identityHDPrivateKey: ECKey, preorderSaltBase58: String): Document? {

        val identityType = if (identity.type.value == 2) "application" else "user"

        val (normalizedParentDomainName, normalizedLabel) = normalizedNames(name)
        val fullDomainName = "$normalizedLabel.$normalizedParentDomainName"

        val nameHash = Sha256Hash.twiceOf(fullDomainName.toByteArray())
        val nameHashHex = nameHash.toString()

        val preOrderSaltRaw = Base58.decode(preorderSaltBase58)

        val saltedDomainHash = getSaltedDomainHash(preOrderSaltRaw, nameHash)

        if (platform.apps["dpns"] == null) {
            throw Error("DPNS is required to register a new name.")
        }
        // 1. Create preorder document

        val preorderDocument = createPreorderDocument(saltedDomainHash, identity)

        val preorderTransition = platform.dpp.document.createStateTransition(listOf(preorderDocument))
        preorderTransition.sign(identity.getPublicKeyById(1)!!, identityHDPrivateKey.privateKeyAsHex)

        return try {
            platform.client.applyStateTransition(preorderTransition)
            preorderDocument
        } catch (x: Exception) {
            null
        }
    }

    fun createPreorderDocument(
        saltedDomainHash: Sha256Hash,
        identity: Identity
    ): Document {
        val map = HashMap<String, Any?>(1)
        map["saltedDomainHash"] = "$HASH_PREFIX_STRING$saltedDomainHash"

        val preorderDocument = platform.documents.create(
            DPNS_PREORDER_DOCUMENT,
            identity,
            map
        )
        return preorderDocument
    }

    fun normalizedNames(name: String): Pair<String, String> {
        val nameSlice = name.indexOf('.')
        val normalizedParentDomainName =
            if (nameSlice == -1) DEFAULT_PARENT_DOMAIN else name.slice(nameSlice + 1..name.length)

        val label = if (nameSlice == -1) name else name.slice(0..nameSlice)

        val normalizedLabel = label.toLowerCase()
        return Pair(normalizedParentDomainName, normalizedLabel)
    }

    private fun getLabel(name: String): String {
        val nameSlice = name.indexOf('.')
        return if (nameSlice == -1) name else name.slice(0..nameSlice)
    }

    fun getSaltedDomainHashString(
        preOrderSaltRaw: ByteArray,
        nameHash: Sha256Hash
    ): String {
        return getSaltedDomainHash(preOrderSaltRaw, nameHash).toString()
    }

    fun getSaltedDomainHashBytes(
        preOrderSaltRaw: ByteArray,
        nameHash: Sha256Hash
    ): ByteArray {
        return getSaltedDomainHash(preOrderSaltRaw, nameHash).bytes
    }

    fun getSaltedDomainHashBytes(
        preOrderSaltRaw: ByteArray,
        name: String
    ): ByteArray {
        return getSaltedDomainHash(preOrderSaltRaw, nameHash(name)).bytes
    }

    fun getSaltedDomainHash(
        preOrderSaltRaw: ByteArray,
        nameHash: Sha256Hash
    ): Sha256Hash {
        val baos = ByteArrayOutputStream(preOrderSaltRaw.size + nameHash.bytes.size)
        baos.write(preOrderSaltRaw)
        // Add prefix bytes for a hash
        baos.write(HASH_PREFIX_BYTES)
        baos.write(nameHash.bytes)

        return Sha256Hash.twiceOf(baos.toByteArray())
    }

    fun nameHash(name: String): Sha256Hash {
        val (normalizedParentDomainName, normalizedLabel) = normalizedNames(name)
        val fullDomainName = "$normalizedLabel.$normalizedParentDomainName"
        return Sha256Hash.twiceOf(fullDomainName.toByteArray())
    }

    fun registerName(
        name: String,
        identity: Identity,
        identityHDPrivateKey: ECKey,
        preorderSaltBase58: String,
        preorder: Document
    ): Document? {
        val domainDocument = createDomainDocument(identity, name, preorderSaltBase58)

        println(domainDocument.toJSON())

        // 4. Create and send domain state transition
        val domainTransition = platform.dpp.document.createStateTransition(listOf(domainDocument))
        domainTransition.sign(identity.getPublicKeyById(1)!!, identityHDPrivateKey.privateKeyAsHex)

        println(domainTransition.toJSON())

        platform.client.applyStateTransition(domainTransition)

        return domainDocument
    }

    fun createDomainDocument(
        identity: Identity,
        name: String,
        preorderSaltBase58: String
    ): Document {
        val records = HashMap<String, Any?>(1)
        records["dashIdentity"] = identity.id

        val (normalizedParentDomainName, normalizedLabel) = normalizedNames(name)
        val fullDomainName = "$normalizedLabel.$normalizedParentDomainName"

        val nameHash = Sha256Hash.twiceOf(fullDomainName.toByteArray())
        val nameHashHex = nameHash.toString()

        val fields = HashMap<String, Any?>(6)
        fields["nameHash"] = "$HASH_PREFIX_STRING$nameHashHex"
        fields["label"] = getLabel(name)
        fields["normalizedLabel"] = normalizedLabel
        fields["normalizedParentDomainName"] = normalizedParentDomainName
        fields["preorderSalt"] = preorderSaltBase58
        fields["records"] = records

        // 3. Create domain document
        val domainDocument = platform.documents.create(
            DPNS_DOMAIN_DOCUMENT,
            identity,
            fields
        )
        return domainDocument
    }

    private fun getDocumentQuery(name: String, parentDomain: String = DEFAULT_PARENT_DOMAIN): DocumentQuery {
        return DocumentQuery.Builder()
            .where(listOf("normalizedLabel", "==", name.toLowerCase()))
            .where(listOf("normalizedParentDomainName", "==", parentDomain))
            .build()
    }

    fun get(name: String): Document? {
        return get(name, DEFAULT_PARENT_DOMAIN)
    }

    fun get(name: String, parentDomain: String): Document? {

        try {
            val documents = platform.documents.get(DPNS_DOMAIN_DOCUMENT, getDocumentQuery(name, parentDomain))
            return if (documents != null && documents.isNotEmpty()) documents[0] else null
        } catch (e: Exception) {
            throw e
        }
    }
}