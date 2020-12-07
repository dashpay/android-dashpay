/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.bitcoinj.core.Block
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.params.EvoNetParams
import org.bitcoinj.params.MobileDevNetParams
import org.bitcoinj.params.PalinkaDevNetParams
import org.dashevo.dapiclient.DapiClient
import org.dashevo.dapiclient.grpc.DefaultBroadcastRetryCallback
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.DashPlatformProtocol
import org.dashevo.dpp.StateRepository
import org.dashevo.dpp.contract.DataContract
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identifier.Identifier
import org.dashevo.dpp.identity.Identity
import org.dashevo.dpp.statetransition.StateTransitionIdentitySigned

class Platform(val params: NetworkParameters) {

    val stateRepository: StateRepository = object : StateRepository {
        override fun fetchDataContract(id: Identifier): DataContract? {
            val contractInfo = apps.values.find { it.contractId == id }
            if (contractInfo?.dataContract != null)
                return contractInfo.dataContract
            return contracts.get(id)
        }

        override fun fetchDocuments(contractId: Identifier, type: String, where: Any): List<Document> {
            return documents.get(contractId, type, where as DocumentQuery)
        }

        override fun fetchTransaction(id: String): Int {
            TODO()
        }

        override fun removeDocument(contractId: Identifier, type: String, id: Identifier) {
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
            return identities.get(id)
        }

        override fun fetchLatestPlatformBlockHeader(): Block {
            TODO("Not yet implemented")
        }
    }

    val broadcastRetryCallback = DefaultBroadcastRetryCallback(stateRepository)

    val dpp = DashPlatformProtocol(stateRepository)
    val apps = HashMap<String, ContractInfo>()
    val contracts = Contracts(this)
    val documents = Documents(this)
    val identities = Identities(this)
    var names = Names(this)
    lateinit var client: DapiClient

    init {
        when {
            params.id.contains("evonet") -> {
                apps["dpns"] = ContractInfo("3VvS19qomuGSbEYWbTsRzeuRgawU3yK4fPMzLrbV62u8")
                apps["dashpay"] = ContractInfo("FrXpVEsxFZ9hgCpiXwWbsQe4xHB9wZHGj4Lg5UjgxtHb")
                client = DapiClient(EvoNetParams.MASTERNODES.toList(), true)
            }
            params.id.contains("mobile") -> {
                apps["dpns"] = ContractInfo("CVZzFCbz4Rcf2Lmu9mvtC1CmvPukHy5kS2LNtNaBFM2N")
                apps["dashpay"] = ContractInfo("Du2kswW2h1gNVnTWdfNdSxBrC2F9ofoaZsXA6ki1PhG6")
                client = DapiClient(MobileDevNetParams.MASTERNODES.toList())
            }
            params.id.contains("palinka") -> {
                apps["dpns"] = ContractInfo("H9AxLAvgxEpq72pDg41nsqR3bY5Cv9hTT6yZdKzY3PaE")
                apps["dashpay"] = ContractInfo("Fxf3w1rsUvRxW8WsVnQcUNgtgVn8w47BwZtQPAsJWkkH")
                apps["thumbnail"] = ContractInfo("3GV8H5ha68pchFyJF46dzdpfgPDhSr6iLht3EcYgqFKw")
                client = DapiClient(PalinkaDevNetParams.get().defaultMasternodeList.toList(), true)
            }
        }
    }

    fun broadcastStateTransition(
        stateTransition: StateTransitionIdentitySigned,
        identity: Identity,
        privateKey: ECKey,
        keyIndex: Int = 0
    ) {
        stateTransition.sign(identity.getPublicKeyById(keyIndex)!!, privateKey.privateKeyAsHex)
        //TODO: validate transition structure here
        client.broadcastStateTransition(stateTransition, retryCallback = broadcastRetryCallback);
    }

    fun hasApp(appName: String): Boolean {
        return apps.containsKey(appName)
    }

    fun getDataContractIdAndType(typeLocator: String): Pair<Identifier, String>? {
        val (dataContractName, documentType) = getAppnameAndType(typeLocator)

        return apps[dataContractName]?.let { Pair(it.contractId, documentType) }
    }

    fun getAppnameAndType(
        typeLocator: String
    ): Pair<String, String> {
        val appNames = apps.keys
        val appName: String
        val fieldType: String
        if (typeLocator.contains('.')) {
            val split = typeLocator.split('.')
            appName = split[0]
            fieldType = split[1]
        } else {
            appName = appNames.first()
            fieldType = typeLocator
        }
        return Pair(appName, fieldType)
    }
}