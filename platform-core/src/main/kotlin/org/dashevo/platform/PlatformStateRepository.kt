/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.bitcoinj.core.Block
import org.bitcoinj.core.Transaction
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.StateRepository
import org.dashevo.dpp.contract.DataContract
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identifier.Identifier
import org.dashevo.dpp.identity.Identity

open class PlatformStateRepository(val platform: Platform) : StateRepository {
    private val identityMap = hashMapOf<Identifier, Identity>()
    private val identityHashesMap = hashMapOf<Identifier, List<ByteArray>>()
    private val contractsMap = hashMapOf<Identifier, DataContract>()
    private val outPointBufferSet = hashSetOf<ByteArray>()

    override fun checkAssetLockTransactionOutPointExists(outPointBuffer: ByteArray): Boolean {
        return outPointBufferSet.contains(outPointBuffer)
    }

    override fun fetchDataContract(id: Identifier): DataContract? {
        if (contractsMap.containsKey(id))
            return contractsMap[id]

        val contractInfo = platform.apps.values.find { it.contractId == id }
        if (contractInfo?.dataContract != null)
            return contractInfo.dataContract

        val contract = platform.contracts.get(id)
        if (contract != null)
            storeDataContract(contract)

        return contract
    }

    override fun fetchDocuments(contractId: Identifier, type: String, where: Any): List<Document> {
        return platform.documents.get(contractId, type, where as DocumentQuery)
    }

    override fun fetchTransaction(id: String): Transaction? {
        val txData = platform.client.getTransaction(id)?: return null
        return Transaction(null, txData.toByteArray())
    }

    override fun removeDocument(contractId: Identifier, type: String, id: Identifier) {
        // do nothing for now
    }

    override fun storeAssetLockTransactionOutPoint(outPointBuffer: ByteArray) {
        outPointBufferSet.add(outPointBuffer)
    }

    override fun storeDataContract(dataContract: DataContract) {
        if (!contractsMap.containsKey(dataContract.id))
            contractsMap[dataContract.id] = dataContract
    }

    override fun storeDocument(document: Document) {
        // do nothing for now
    }

    override fun storeIdentity(identity: Identity) {
        if (!identityMap.containsKey(identity.id))
            identityMap[identity.id]
    }

    override fun storeIdentityPublicKeyHashes(identifier: Identifier, publicKeyHashes: List<ByteArray>) {
        identityHashesMap[identifier] = publicKeyHashes
    }

    override fun fetchIdentity(id: Identifier): Identity? {
        if (identityMap.containsKey(id))
            return identityMap[id]

        val identity = platform.identities.get(id)

        if (identity != null) {
            storeIdentity(identity)
        }
        return identity
    }

    override fun fetchLatestPlatformBlockHeader(): Block {
        TODO("Not yet implemented")
    }
}