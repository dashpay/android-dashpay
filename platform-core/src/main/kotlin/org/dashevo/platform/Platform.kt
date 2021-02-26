/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.evolution.SimplifiedMasternodeListManager
import org.bitcoinj.params.EvoNetParams
import org.bitcoinj.params.MobileDevNetParams
import org.bitcoinj.params.PalinkaDevNetParams
import org.bitcoinj.params.TestNet3Params
import org.dashevo.dapiclient.DapiClient
import org.dashevo.dapiclient.grpc.DefaultBroadcastRetryCallback
import org.dashevo.dapiclient.grpc.DefaultGetDataContractWithContractIdRetryCallback
import org.dashevo.dapiclient.grpc.DefaultGetDocumentsWithContractIdRetryCallback
import org.dashevo.dapiclient.grpc.DefaultGetIdentityWithIdentitiesRetryCallback
import org.dashevo.dpp.DashPlatformProtocol
import org.dashevo.dpp.identifier.Identifier
import org.dashevo.dpp.identity.Identity
import org.dashevo.dpp.statetransition.StateTransitionIdentitySigned
import kotlin.collections.HashMap

class Platform(val params: NetworkParameters) {

    var stateRepository = PlatformStateRepository(this)

    val dpp = DashPlatformProtocol(stateRepository)
    val apps = HashMap<String, ContractInfo>()
    val contracts = Contracts(this)
    val documents = Documents(this)
    val identities = Identities(this)
    var names = Names(this)
    lateinit var client: DapiClient
    private var permanentBanList: List<String> = listOf()
    val documentsRetryCallback = object : DefaultGetDocumentsWithContractIdRetryCallback(apps.map { it.value.contractId }) {
        override val retryContractIds
            get() = getAppList() // always use the latest app list
    }
    val broadcastRetryCallback = object : DefaultBroadcastRetryCallback(stateRepository) {
        override val retryContractIds
            get() = getAppList() // always use the latest app list
        override val retryIdentityIds: List<Identifier>
            get() = stateRepository.validIdentityIdList()
        override val retryDocumentIds: List<Identifier>
            get() = stateRepository.validDocumentIdList()
    }
    val identitiesRetryCallback = object : DefaultGetIdentityWithIdentitiesRetryCallback() {
        override val retryIdentityIds: List<Identifier>
            get() = stateRepository.validIdentityIdList()
    }
    val contractsRetryCallback = object : DefaultGetDataContractWithContractIdRetryCallback() {
        override val retryContractIds: List<Identifier>
            get() = getAppList()
    }

    init {
        when {
            params.id.contains("test") -> {
                apps["dpns"] = ContractInfo("36ez8VqoDbR8NkdXwFaf9Tp8ukBdQxN8eYs8JNMnUyKz")
                // matk8g1YRpzZskecRfpG5GCAgRmWCGJfjUemrsLkFDg - contract with coreHeightCreatedAt required field
                apps["dashpay"] = ContractInfo("2DAncD4YTjfhSQZYrsQ659xbM7M5dNEkyfBEAg9SsS3W")
                client = DapiClient(TestNet3Params.MASTERNODES.toList())
                permanentBanList = listOf("45.48.168.16", "71.239.154.151", "174.34.233.98")
            }
            params.id.contains("evonet") -> {
                apps["dpns"] = ContractInfo("3VvS19qomuGSbEYWbTsRzeuRgawU3yK4fPMzLrbV62u8")
                apps["dashpay"] = ContractInfo("5kML7KqerxF2wU7acywVhpVRHtJGrNGh9swcmqNmFg2s")
                client = DapiClient(EvoNetParams.MASTERNODES.toList())
            }
            params.id.contains("mobile") -> {
                apps["dpns"] = ContractInfo("CVZzFCbz4Rcf2Lmu9mvtC1CmvPukHy5kS2LNtNaBFM2N")
                apps["dashpay"] = ContractInfo("Du2kswW2h1gNVnTWdfNdSxBrC2F9ofoaZsXA6ki1PhG6")
                client = DapiClient(MobileDevNetParams.MASTERNODES.toList())
            }
            params.id.contains("palinka") -> {
                apps["dpns"] = ContractInfo("FZ2MkyR8YigXX7K7m9sq3PikzubV8i4rwUMheAQTLLCw")
                apps["dashpay"] = ContractInfo("GmCL5grcMBHumKVXvWpRZU4BaGzGC7p6mbsJSR4K6yhd")
                //apps["thumbnail"] = ContractInfo("3GV8H5ha68pchFyJF46dzdpfgPDhSr6iLht3EcYgqFKw")
                client = DapiClient(PalinkaDevNetParams.get().defaultMasternodeList.toList())
            }
        }
    }

    fun getAppList() : List<Identifier> {
        return apps.map { it.value.contractId }
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

    /**
     * Sets the DAPI clients masternode list manager from which it will obtain masternodes
     *
     * @param masternodeListManager SimplifiedMasternodeListManager
     */
    fun setMasternodeListManager(masternodeListManager: SimplifiedMasternodeListManager) {
        client.setSimplifiedMasternodeListManager(masternodeListManager, params.defaultMasternodeList.toList())
        banMasternodes(permanentBanList)
    }

    /**
     * Permanently ban a list of masternodes from being used by the DAPI client
     * @param banList List<String> a list of IP addresses of masternodes to ban
     */
    fun banMasternodes(banList: List<String>) {
        banList.forEach {
            client.dapiAddressListProvider.addBannedAddress(it)
        }
    }

    fun useValidNodes() {
        val mnList = getMnList()
        val validList = mnList.filter {
            it["isValid"] == true
        }
        client = DapiClient(validList.map { (it["service"] as String).split(":")[0] })
    }

    private fun getMnList(): List<Map<String, Any>> {
        val success = 0
        do {
            try {
                val baseBlockHash = client.getBlockHash(0)
                val blockHash = client.getBestBlockHash()

                val mnListDiff = client.getMnListDiff(baseBlockHash!!, blockHash!!)
                return mnListDiff!!["mnList"] as List<Map<String, Any>>
            } catch (e: Exception) {
                println("Error: $e")
            }
        } while (success == 0)
        return listOf()
    }
}