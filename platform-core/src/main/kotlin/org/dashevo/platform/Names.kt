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
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identity.Identity
import org.dashevo.dpp.toHexString
import org.dashevo.dpp.util.Entropy
import java.io.ByteArrayOutputStream

class Names(val platform: Platform) {

    companion object {
        const val DEFAULT_PARENT_DOMAIN = "dash"
        const val DPNS_DOMAIN_DOCUMENT = "dpns.domain"
        const val DPNS_PREORDER_DOCUMENT = "dpns.preorder"

        fun isUniqueIdentity(domainDocument: Document): Boolean {
            val records = domainDocument.data["records"] as Map<String, Any>
            return records.containsKey("dashUniqueIdentityId")
        }
    }

    fun register(name: String, identity: Identity, identityHDPrivateKey: ECKey, isUniqueIdentity: Boolean = true): Document? {
        val entropy = Entropy.generate()
        val document = preorder(name, identity, identityHDPrivateKey, entropy)
        return if (document != null) {
            registerName(name, identity, identityHDPrivateKey, Base58.decode(entropy), isUniqueIdentity)
        } else null
    }

    fun preorder(name: String, identity: Identity, identityHDPrivateKey: ECKey, preorderSaltBase58: String): Document? {

        val (normalizedParentDomainName, normalizedLabel) = normalizedNames(name)
        val fullDomainName = "$normalizedLabel.$normalizedParentDomainName"

        val preOrderSaltRaw = Base58.decode(preorderSaltBase58)

        val saltedDomainHash = getSaltedDomainHash(preOrderSaltRaw, fullDomainName)

        if (platform.apps["dpns"] == null) {
            throw Error("DPNS is required to register a new name.")
        }
        // 1. Create preorder document

        val preorderDocument = createPreorderDocument(saltedDomainHash, identity)

        val map = hashMapOf(
            "create" to listOf(preorderDocument)
        )

        val preorderTransition = platform.dpp.document.createStateTransition(map)
        preorderTransition.sign(identity.getPublicKeyById(0)!!, identityHDPrivateKey.privateKeyAsHex)

        return try {
            platform.client.broadcastStateTransition(preorderTransition)
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
        map["saltedDomainHash"] = saltedDomainHash.bytes;//.bytes.toBase64()

        val preorderDocument = platform.documents.create(
            DPNS_PREORDER_DOCUMENT,
            identity.id,
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

    fun getSaltedDomainHashBytes(
        preOrderSaltRaw: ByteArray,
        name: String
    ): ByteArray {
        return getSaltedDomainHash(preOrderSaltRaw, name).bytes
    }

    fun getSaltedDomainHash(
        preOrderSaltRaw: ByteArray,
        fullName: String
    ): Sha256Hash {
        val baos = ByteArrayOutputStream(preOrderSaltRaw.size + fullName.length)
        baos.write(preOrderSaltRaw)
        baos.write(fullName.toByteArray())
        return Sha256Hash.twiceOf(baos.toByteArray())
    }

    fun registerName(
        name: String,
        identity: Identity,
        identityHDPrivateKey: ECKey,
        preorderSaltBase: ByteArray,
        isUniqueIdentity: Boolean = true
    ): Document? {
        val domainDocument = createDomainDocument(identity, name, preorderSaltBase, isUniqueIdentity)

        println(domainDocument.toJSON())

        // 4. Create and send domain state transition
        val map = hashMapOf<String, List<Document>?>(
            "create" to listOf(domainDocument)
        )
        val domainTransition = platform.dpp.document.createStateTransition(map)
        domainTransition.sign(identity.getPublicKeyById(1)!!, identityHDPrivateKey.privateKeyAsHex)

        println(domainTransition.toJSON())

        platform.client.broadcastStateTransition(domainTransition)

        return domainDocument
    }

    fun createDomainDocument(
        identity: Identity,
        name: String,
        preorderSaltBase: ByteArray,
        isUniqueIdentity: Boolean = true
    ): Document {
        val recordType = if (isUniqueIdentity)
            "dashUniqueIdentityId"
        else
            "dashAliasIdentityId"

        val records = hashMapOf<String, Any?>(
            recordType to identity.id
        )

        val subdomainRules = hashMapOf<String, Any?>(
            "allowSubdomains" to false // do not allow
        )

        val (normalizedParentDomainName, normalizedLabel) = normalizedNames(name)
        val fullDomainName = "$normalizedLabel.$normalizedParentDomainName"

        val fields = HashMap<String, Any?>(6)
        fields["label"] = getLabel(name)
        fields["normalizedLabel"] = normalizedLabel
        fields["normalizedParentDomainName"] = normalizedParentDomainName
        fields["preorderSalt"] = preorderSaltBase
        fields["records"] = records
        fields["subdomainRules"] = subdomainRules

        // 3. Create domain document
        val domainDocument = platform.documents.create(
            DPNS_DOMAIN_DOCUMENT,
            identity.id,
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

    /**
     * Gets the document for the given name if it exists under the default parent domain
     * @param name String
     * @return Document? The document for the given name or null if the name does not exist
     */
    fun get(name: String): Document? {
        return get(name, DEFAULT_PARENT_DOMAIN)
    }

    fun resolve(name: String): Document? {
        return get(name)
    }

    /**
     * Gets the document for the given name if it exists
     * @param name String
     * @param parentDomain String
     * @return Document? The document for the given name or null if the name does not exist
     */
    fun get(name: String, parentDomain: String): Document? {

        try {
            val documents = platform.documents.get(DPNS_DOMAIN_DOCUMENT, getDocumentQuery(name, parentDomain))
            return if (documents != null && documents.isNotEmpty()) documents[0] else null
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Searches for and returns a list of all name documents that match the given name based
     * on these criteria: starts with.  Contains is not supported
     * @param text String
     * @param parentDomain String
     * @param startAtIndex Int
     * @return List<Documents>
     */
    fun search(text: String, parentDomain: String, retrieveAll: Boolean, startAtIndex: Int = 0): List<Document> {
        val documentQuery = DocumentQuery.Builder()
            .where(listOf("normalizedParentDomainName", "==", parentDomain))
            .orderBy(listOf("normalizedLabel", "asc"))
            .where(listOf("normalizedLabel", "startsWith", text.toLowerCase()))

        var startAt = startAtIndex
        val documents = ArrayList<Document>()
        var documentList: List<Document>
        var requests = 0

        do {
            try {
                documentList = platform.documents.get(DPNS_DOMAIN_DOCUMENT, documentQuery.startAt(startAt).build())
                requests += 1
                startAt += Documents.DOCUMENT_LIMIT
                if (documentList.isNotEmpty())
                    documents.addAll(documentList)
            } catch (e: Exception) {
                throw e
            }
        } while ((requests == 0 || documentList.size >= Documents.DOCUMENT_LIMIT) && retrieveAll)

        return documents
    }

    /**
     * Gets all of the usernames associated with userId
     */
    fun getByUserId(ownerId: String): List<Document> {
        return resolveByRecord("dashUniqueIdentityId", ownerId)
    }

    /**
     * Gets all of the alias usernames associated with userId
     */
    fun getByUserIdAlias(ownerId: String): List<Document> {
        return resolveByRecord("dashAliasIdentityId", ownerId)
    }

    fun resolveByRecord(record: String, value: String): List<Document> {
        val documentQuery = DocumentQuery.Builder()
            .where(listOf("records.$record", "==", value))

        val results = platform.documents.get(DPNS_DOMAIN_DOCUMENT, documentQuery.build())

        return results
    }

    /**
     * Gets all of the unique usernames associated with a list of userId's
     */
    fun getList(
        userIds: List<String>,
        retrieveAll: Boolean = true,
        startAt: Int = 0
    ): List<Document> {
        val documentQuery = DocumentQuery.Builder()
        documentQuery.whereIn("records.dashUniqueIdentityId", userIds)
        var requests = 0

        val documents = arrayListOf<Document>()
        do {
            val result = platform.documents.get(DPNS_DOMAIN_DOCUMENT, documentQuery.startAt(startAt).build())
            documents.addAll(result)
            requests += 1
        } while ((requests == 0 || result.size >= Documents.DOCUMENT_LIMIT) && retrieveAll)

        return documents
    }
}