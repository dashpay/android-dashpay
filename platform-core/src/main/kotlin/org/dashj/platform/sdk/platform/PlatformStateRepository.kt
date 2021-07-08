/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.sdk.platform

import org.bitcoinj.core.Block
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Transaction
import org.bitcoinj.quorums.InstantSendLock
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dpp.StateRepository
import org.dashj.platform.dpp.contract.DataContract
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.dpp.identity.Identity

open class PlatformStateRepository(val platform: Platform) : StateRepository {
    private val identityMap = hashMapOf<Identifier, Identity>()
    private val validIdentities = hashSetOf<Identifier>()
    private val documentsMap = hashMapOf<Identifier, Document>()
    private val validDocuments = hashSetOf<Identifier>()
    private val identityHashesMap = hashMapOf<Identifier, List<ByteArray>>()
    private val contractsMap = hashMapOf<Identifier, DataContract>()
    private val outPointBufferSet = hashSetOf<ByteArray>()
    private val preorderSalts = hashMapOf<Sha256Hash, Sha256Hash>()

    override fun fetchDataContract(id: Identifier): DataContract? {
        if (contractsMap.containsKey(id)) {
            return contractsMap[id]
        }
        val contractInfo = platform.apps.values.find { it.contractId == id }
        if (contractInfo?.contract != null) {
            return contractInfo.contract
        }
        val contract = platform.contracts.get(id)
        if (contract != null) {
            storeDataContract(contract)
        }
        return contract
    }

    override fun fetchDocuments(contractId: Identifier, type: String, where: Any): List<Document> {
        return platform.documents.get(contractId, type, where as DocumentQuery)
    }

    override fun fetchTransaction(id: String): Transaction? {
        val txData = platform.client.getTransaction(id) ?: return null
        return Transaction(null, txData.toByteArray())
    }

    override fun isAssetLockTransactionOutPointAlreadyUsed(outPointBuffer: ByteArray): Boolean {
        return outPointBufferSet.contains(outPointBuffer)
    }

    override fun markAssetLockTransactionOutPointAsUsed(outPointBuffer: ByteArray) {
        outPointBufferSet.add(outPointBuffer)
    }

    override fun removeDocument(contractId: Identifier, type: String, id: Identifier) {
        // do nothing for now
    }

    override fun storeDataContract(dataContract: DataContract) {
        if (!contractsMap.containsKey(dataContract.id)) {
            contractsMap[dataContract.id] = dataContract
        }
    }

    override fun storeDocument(document: Document) {
        documentsMap[document.id] = document
    }

    override fun storeIdentity(identity: Identity) {
        if (!identityMap.containsKey(identity.id)) {
            identityMap[identity.id]
        }
    }

    override fun storeIdentityPublicKeyHashes(identifier: Identifier, publicKeyHashes: List<ByteArray>) {
        identityHashesMap[identifier] = publicKeyHashes
    }

    override fun verifyInstantLock(instantLock: InstantSendLock): Boolean {
        // TODO: can we do anything here?
        return false
    }

    override fun fetchIdentity(id: Identifier): Identity? {
        if (identityMap.containsKey(id)) {
            return identityMap[id]
        }

        val identity = platform.identities.get(id)

        if (identity != null) {
            storeIdentity(identity)
        }
        return identity
    }

    override fun fetchLatestPlatformBlockHeader(): Block {
        TODO("Not yet implemented")
    }

    fun addValidIdentity(identityId: Identifier) {
        validIdentities.add(identityId)
    }

    fun validIdentityIdList(): List<Identifier> {
        val result = identityMap.keys.toMutableList()
        result.addAll(validIdentities)
        return result
    }

    fun addValidDocument(identityId: Identifier) {
        validDocuments.add(identityId)
    }

    fun validDocumentIdList(): List<Identifier> {
        val result = documentsMap.keys.toMutableList()
        result.addAll(validDocuments)
        return result
    }

    fun addValidPreorderSalt(preorderSalt: ByteArray, saltedDomainHash: ByteArray) {
        preorderSalts[Sha256Hash.wrap(preorderSalt)] = Sha256Hash.wrap(saltedDomainHash)
    }

    fun validPreorderSalts(): Map<Sha256Hash, Sha256Hash> {
        return preorderSalts
    }
}
