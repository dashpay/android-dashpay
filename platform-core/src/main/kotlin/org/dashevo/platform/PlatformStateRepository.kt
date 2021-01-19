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

class PlatformStateRepository(val platform: Platform) : StateRepository {
    override fun checkAssetLockTransactionOutPointExists(outPointBuffer: ByteArray): Boolean {
        TODO("Not yet implemented")
    }

    override fun fetchDataContract(id: Identifier): DataContract? {
        val contractInfo = platform.apps.values.find { it.contractId == id }
        if (contractInfo?.dataContract != null)
            return contractInfo.dataContract
        return platform.contracts.get(id)
    }

    override fun fetchDocuments(contractId: Identifier, type: String, where: Any): List<Document> {
        return platform.documents.get(contractId, type, where as DocumentQuery)
    }

    override fun fetchTransaction(id: String): Transaction {
        TODO()
    }

    override fun removeDocument(contractId: Identifier, type: String, id: Identifier) {
        TODO("Not yet implemented")
    }

    override fun storeAssetLockTransactionOutPoint(outPointBuffer: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun storeDataContract(dataContract: DataContract) {
        TODO("Not yet implemented")
    }

    override fun storeDocument(document: Document) {
        TODO("Not yet implemented")
    }

    override fun storeIdentity(identity: Identity) {
        TODO("Not yet implemented")
    }

    override fun storeIdentityPublicKeyHashes(identity: Identifier, publicKeyHashes: List<ByteArray>) {
        TODO("Not yet implemented")
    }

    override fun fetchIdentity(id: Identifier): Identity? {
        return platform.identities.get(id)
    }

    override fun fetchLatestPlatformBlockHeader(): Block {
        TODO("Not yet implemented")
    }
}