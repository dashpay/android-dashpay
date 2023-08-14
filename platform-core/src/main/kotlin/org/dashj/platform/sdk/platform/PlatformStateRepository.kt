/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.sdk.platform

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Transaction
import org.bitcoinj.quorums.InstantSendLock
import org.dashj.platform.dapiclient.errors.NotFoundException
import org.dashj.platform.dapiclient.grpc.BroadcastRetryCallback
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dapiclient.model.MerkLibVerifyProof
import org.dashj.platform.dpp.Factory
import org.dashj.platform.dpp.StateRepository
import org.dashj.platform.dpp.contract.DataContract
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.dpp.identity.Identity
import org.dashj.platform.dpp.identity.IdentityPublicKey
import org.dashj.platform.dpp.statetransition.StateTransitionIdentitySigned
import org.dashj.platform.dpp.toHex
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class PlatformStateRepository(val platform: Platform) : StateRepository {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(PlatformStateRepository::class.java)
    }

    protected val identityMap = hashMapOf<Identifier, Identity>()
    protected val validIdentities = hashSetOf<Identifier>()
    protected val documentsMap = hashMapOf<Identifier, Document>()
    protected val validDocuments = hashSetOf<Identifier>()
    protected val identityHashesMap = hashMapOf<Identifier, List<ByteArray>>()
    protected val contractsMap = hashMapOf<Identifier, DataContract>()
    protected val outPointBufferSet = hashSetOf<ByteArray>()
    protected val preorderSalts = hashMapOf<Sha256Hash, Sha256Hash>()

    private val broadcastRetryCallback = object : BroadcastRetryCallback(this@PlatformStateRepository) {
        override val retryContractIds
            get() = platform.getAppList() // always use the latest app list
        override val retryIdentityIds: List<Identifier>
            get() = validIdentityIdList()
        override val retryDocumentIds: List<Identifier>
            get() = validDocumentIdList()
        override val retryPreorderSalts: Map<Sha256Hash, Sha256Hash>
            get() = validPreorderSalts()
    }

    override fun fetchDataContract(id: Identifier): DataContract? {
        if (contractsMap.containsKey(id)) {
            return contractsMap[id]
        }
        val contractInfo = platform.apps.values.find { it.contractId == id }
        if (contractInfo?.contract != null) {
            return contractInfo.contract
        }

        val contractResponse =
            platform.client.getDataContract(id.toBuffer(), Features.proveContracts, platform.contractsRetryCallback)

        return if (contractResponse.dataContract.isNotEmpty()) {
            val contract = platform.dpp.dataContract.createFromBuffer(contractResponse.dataContract)
            contract.metadata = contractResponse.metadata.getMetadata()
            storeDataContract(contract)
            contract
        } else null
    }


    override fun fetchDocuments(contractId: Identifier, documentType: String, where: Any): List<Document> {
        where as DocumentQuery

        val documentResponse = platform.client.getDocuments(
            contractId.toBuffer(),
            documentType,
            where,
            Features.proveDocuments,
            platform.documentsRetryCallback
        )
        return documentResponse.documents.map {
            val document = platform.dpp.document.createFromBuffer(it, Factory.Options(true))
            document.metadata = documentResponse.metadata.getMetadata()
            document
        }
    }

    override fun fetchTransaction(id: String): Transaction? {
        val txData = platform.client.getTransactionBytes(id) ?: return null
        return Transaction(null, txData)
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
        log.info("store dataContract: {}", dataContract.toBuffer().toHex())
    }

    override fun storeDocument(document: Document) {
        documentsMap[document.id] = document
        log.info("store document[{}]: {}", document.type, document.toBuffer().toHex())
    }

    override fun storeIdentity(identity: Identity) {
        if (!identityMap.containsKey(identity.id)) {
            identityMap[identity.id] = identity
        }
        storeIdentityPublicKeyHashes(identity.id, identity.publicKeys.map {
            when (it.type) {
                IdentityPublicKey.Type.ECDSA_HASH160, IdentityPublicKey.Type.BIP13_SCRIPT_HASH -> it.data
                IdentityPublicKey.Type.ECDSA_SECP256K1 -> ECKey.fromPublicOnly(it.data).pubKeyHash
                else -> Sha256Hash.twiceOf(it.data).bytes
            }
        })
        log.info("store identity: {}", identity.toBuffer().toHex())
    }

    override fun storeIdentityPublicKeyHashes(identity: Identifier, publicKeyHashes: List<ByteArray>) {
        identityHashesMap[identity] = publicKeyHashes
    }

    override fun verifyInstantLock(instantLock: InstantSendLock): Boolean {
        // TODO: can we do anything here?
        return false
    }

    override fun fetchIdentity(id: Identifier): Identity? {
        if (identityMap.containsKey(id)) {
            return identityMap[id]
        }

        val identity = try {
            val identityResponse =
                platform.client.getIdentity(id.toBuffer(), Features.proveIdentities, platform.identitiesRetryCallback)
            val identity = platform.dpp.identity.createFromBuffer(identityResponse.identity)
            identity.metadata = identityResponse.metadata.getMetadata()
            identity
        } catch (e: NotFoundException) {
            null
        }

        if (identity != null) {
            storeIdentity(identity)
        }
        return identity
    }

    override fun fetchIdentityFromPubKeyHash(pubKeyHash: ByteArray): Identity? {
        return try {
            val identifier = identityHashesMap.filter { entry ->
                entry.value.any { it.contentEquals(pubKeyHash) }
            }.keys.first()
            fetchIdentity(identifier)
        } catch (e: NoSuchElementException) {
            platform.client.getIdentityByFirstPublicKey(pubKeyHash, false)?.let {
                platform.dpp.identity.createFromBuffer(it)
            }
        }
    }

    override fun fetchLatestPlatformBlockHeader(): ByteArray {
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

    fun dump() {
        println("dataContracts:")
        contractsMap.forEach {
            println(it.value.toBuffer().toHex())
        }
        println("identities:")
        identityMap.forEach {
            println(it.value.toBuffer().toHex())
        }
        println("documents:")
        documentsMap.forEach {
            println("${it.value.type}: ${it.value.toBuffer().toHex()}")
        }
    }

    open fun broadcastStateTransition(signedStateTransition: StateTransitionIdentitySigned) {
        // TODO: validate transition structure here

        platform.client.broadcastStateTransitionAndWait(
            signedStateTransition,
            retryCallback = broadcastRetryCallback,
            verifyProof = MerkLibVerifyProof(signedStateTransition)
        )
    }

    fun storeDataContract(dataContractBytes: ByteArray) {
        val (protocolVersion, rawDataContract) = Factory.decodeProtocolEntity(dataContractBytes)
        rawDataContract["protocolVersion"] = protocolVersion
        val contract = DataContract(rawDataContract)
        storeDataContract(contract)
    }

    fun storeDocument(documentBytes: ByteArray) {
        val (protocolVersion, rawDocument) = Factory.decodeProtocolEntity(documentBytes)
        rawDocument["protocolVersion"] = protocolVersion
        val document = Document(rawDocument, fetchDataContract(Identifier.from(rawDocument["\$dataContractId"]))!!)
        storeDocument(document)
    }

    fun storeIdentity(identityBytes: ByteArray) {
        val (protocolVersion, rawIdentity) = Factory.decodeProtocolEntity(identityBytes)
        rawIdentity["protocolVersion"] = protocolVersion
        val identity = Identity(rawIdentity)
        storeIdentity(identity)
    }
}
