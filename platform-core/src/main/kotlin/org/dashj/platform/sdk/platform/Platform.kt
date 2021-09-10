/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.sdk.platform

import com.google.common.base.Stopwatch
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlin.collections.HashMap
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.evolution.SimplifiedMasternodeListManager
import org.bitcoinj.params.SchnappsDevNetParams
import org.bitcoinj.params.TestNet3Params
import org.dashj.platform.dapiclient.DapiClient
import org.dashj.platform.dapiclient.MaxRetriesReachedException
import org.dashj.platform.dapiclient.NoAvailableAddressesForRetryException
import org.dashj.platform.dapiclient.grpc.BroadcastRetryCallback
import org.dashj.platform.dapiclient.grpc.DefaultGetDataContractWithContractIdRetryCallback
import org.dashj.platform.dapiclient.grpc.DefaultGetDocumentsWithContractIdRetryCallback
import org.dashj.platform.dapiclient.grpc.DefaultGetIdentityWithIdentitiesRetryCallback
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dapiclient.model.MerkLibVerifyProof
import org.dashj.platform.dpp.DashPlatformProtocol
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.dpp.identity.Identity
import org.dashj.platform.dpp.statetransition.StateTransitionIdentitySigned
import org.dashj.platform.dpp.util.Entropy
import org.dashj.platform.sdk.client.ClientAppDefinition
import org.dashj.platform.sdk.platform.multicall.MulticallException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Platform(val params: NetworkParameters) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(Platform::class.java)
    }

    var stateRepository = PlatformStateRepository(this)

    val dpp = DashPlatformProtocol(stateRepository)
    val apps = HashMap<String, ClientAppDefinition>()
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
    val broadcastRetryCallback = object : BroadcastRetryCallback(stateRepository) {
        override val retryContractIds
            get() = getAppList() // always use the latest app list
        override val retryIdentityIds: List<Identifier>
            get() = stateRepository.validIdentityIdList()
        override val retryDocumentIds: List<Identifier>
            get() = stateRepository.validDocumentIdList()
        override val retryPreorderSalts: Map<Sha256Hash, Sha256Hash>
            get() = stateRepository.validPreorderSalts()
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
                apps["dpns"] = ClientAppDefinition("H2P7t8e3z2Naf55RiUrEK63fLfeGzaza5x5etm9J1ppG")
                apps["dashpay"] = ClientAppDefinition("HpJZGdjnHjUucndek2kc1P9RBhTQZxjHFeQKnanxVVJp")
                apps["featureFlags"] = ClientAppDefinition("2ddvAY6hEb514Bog78DLcdAzP8PuiqdmijpAXP5nyVX5")
                client = DapiClient(TestNet3Params.MASTERNODES.toList())
                permanentBanList = listOf(
                    "3.90.167.67",
                    "18.236.78.191",
                    "45.48.168.16", // unavailable
                    "34.214.102.160", // no dashpay contract
                    "174.34.233.98",
                    "174.34.233.112",
                    "174.34.233.119", // unavailable
                    "174.34.233.133", // unavailable
                    "174.34.233.134", // unavailable
                    "52.11.252.174", // no dashpay contract
                    "34.209.124.112", // no dashpay contract
                    "35.247.4.64",
                    "136.244.113.166",
                    "174.34.233.113", // no dashpay contract
                    "174.34.233.114", // no dashpay contract
                    "174.34.233.115",
                    "174.34.233.116",
                    "174.34.233.118", // unavailable
                    "174.34.233.120", // unavailable
                    "178.62.203.249", // unavailable
                    "60.205.218.5",
                    "51.68.175.79",
                    "51.195.122.4",
                    "51.195.122.6",
                    "54.190.73.116", // no dashpay contract
                    "80.208.231.9", // no dashpay contract
                    "206.189.147.240", // unavailable
                    "178.62.203.249", // unavailable
                    "65.21.248.22",
                    "65.21.248.190",
                    "65.21.240.224",
                    "35.164.180.39", // no dashpay contract
                    "80.209.231.29", // no dashpay contract
                    "80.208.228.172", // no dashpay contract
                    "95.179.171.177",
                    "34.208.190.130", // no dashpay contract
                    "174.34.233.117", // no dashpay contract
                    "174.34.233.127", // no dashpay contract
                    "143.110.156.147", // no dashpay contract
                    "174.34.233.121", // no dashpay contract
                    "174.34.233.132", // no waitForStateTransitionResult, no dashpay contract
                    "135.125.232.20",
                    "135.125.239.254",
                    "135.181.37.140",
                    "159.224.190.244",
                    "54.148.229.157"
                )
            }
            params.id.contains("schnapps") -> {
                apps["dpns"] = ClientAppDefinition("4KJF8PDRAw1rFrFTD95fE3QeutG47TwsJWjBs3wUhNWK")
                apps["dashpay"] = ClientAppDefinition("3RcLJv6CRo7arseKxt82swXQ5t7snaPdNsdP7Kv4LeEr")
                apps["featureFlags"] = ClientAppDefinition("7PZZ9fLygvBzY6V5XeD6JuKb2CgxwAiVzcUtSiMnzydf")
                // apps["thumbnail"] = ClientAppDefinition("3GV8H5ha68pchFyJF46dzdpfgPDhSr6iLht3EcYgqFKw")
                client = DapiClient(SchnappsDevNetParams.get().defaultMasternodeList.toList())
            }
        }
    }

    fun getAppList(): List<Identifier> {
        return apps.map { it.value.contractId }
    }

    fun broadcastStateTransition(
        stateTransition: StateTransitionIdentitySigned,
        identity: Identity,
        privateKey: ECKey,
        keyIndex: Int = 0
    ) {
        stateTransition.sign(identity.getPublicKeyById(keyIndex)!!, privateKey.privateKeyAsHex)

        broadcastStateTransition(stateTransition)
    }

    fun broadcastStateTransition(signedStateTransition: StateTransitionIdentitySigned) {
        // TODO: validate transition structure here
        client.broadcastStateTransitionAndWait(signedStateTransition, retryCallback = broadcastRetryCallback, verifyProof = MerkLibVerifyProof(signedStateTransition))
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
                log.warn("Error: $e")
            }
        } while (success == 0)
        return listOf()
    }

    fun check(fullTest: Boolean = false): Boolean {
        return try {
            // check getDataContract
            val watch = Stopwatch.createStarted()
            if (fullTest) {
                contracts.get(apps["dpns"]!!.contractId) ?: return false
            }

            // check getDocuments
            documents.get(apps["dpns"]!!.contractId, "domain", DocumentQuery.builder().limit(5).build())

            // check getIdentity
            if (fullTest) {
                identities.get(Identifier.from(Entropy.generate())) // this should return null
            }

            try {
                if (fullTest) {
                    val response = client.getStatus()
                    response!!.network.peerCount > 0 &&
                        /*response.errors.isBlank() &&*/
                        params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.MINIMUM) <= response.version.protocolVersion
                }
                log.info("platform check: $watch")
                true
            } catch (e: StatusRuntimeException) {
                log.info("platform check: $watch")
                e.status.code == Status.Code.INTERNAL
            } catch (e: MaxRetriesReachedException) {
                log.info("platform check: $watch")
                if (e.cause is StatusRuntimeException) {
                    (e.cause as StatusRuntimeException).status.code == io.grpc.Status.Code.INTERNAL
                } else {
                    log.warn("platform check: $e")
                    false
                }
            } catch (e: NoAvailableAddressesForRetryException) {
                log.warn("platform check: $e")
                false
            }
        } catch (e: StatusRuntimeException) {
            log.warn("platform check: $e")
            false
        } catch (e: MaxRetriesReachedException) {
            log.warn("platform check: $e")
            false
        } catch (e: MulticallException) {
            log.warn("platform check: $e")
            false
        } catch (e: NoAvailableAddressesForRetryException) {
            log.warn("platform check: $e")
            false
        }
    }
}
