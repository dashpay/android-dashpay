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
import org.bitcoinj.evolution.SimplifiedMasternodeListManager
import org.dashj.platform.dapiclient.DapiClient
import org.dashj.platform.dapiclient.MaxRetriesReachedException
import org.dashj.platform.dapiclient.NoAvailableAddressesForRetryException
import org.dashj.platform.dapiclient.SystemIds
import org.dashj.platform.dapiclient.grpc.DefaultGetDataContractWithContractIdRetryCallback
import org.dashj.platform.dapiclient.grpc.DefaultGetDocumentsWithContractIdRetryCallback
import org.dashj.platform.dapiclient.grpc.DefaultGetIdentityWithIdentitiesRetryCallback
import org.dashj.platform.dapiclient.model.DocumentQuery
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
        private const val MOCK_DAPI = true
        fun mockDAPI() = MOCK_DAPI
    }

    var stateRepository = if (mockDAPI()) {
        MockPlatformStateRepository(this)
    } else {
        PlatformStateRepository(this)
    }

    val dpp = DashPlatformProtocol(stateRepository, params)
    val apps = HashMap<String, ClientAppDefinition>()
    val contracts = Contracts(this)
    val documents = Documents(this)
    val identities = Identities(this)
    var names = Names(this)
    lateinit var client: DapiClient
    private var useWhiteList = false
    val documentsRetryCallback = object : DefaultGetDocumentsWithContractIdRetryCallback(apps.map { it.value.contractId }) {
        override val retryContractIds
            get() = getAppList() // always use the latest app list
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
        apps["dpns"] = ClientAppDefinition(SystemIds.dpnsDataContractId)
        apps["dashpay"] = ClientAppDefinition(SystemIds.dashpayDataContractId)
        when {
            params.id.contains("test") -> {
                useWhiteList = true
                apps["dashwallet"] = ClientAppDefinition("Fds5DDfXoLwpUZ71AAVYZP1uod8S7Ze2bR28JExBvZKR")
            }
            params.id.contains("bintang") -> {
                apps["dashwallet"] = ClientAppDefinition("Fds5DDfXoLwpUZ71AAVYZP1uod8S7Ze2bR28JExBvZKR")
            }
        }
        client = DapiClient(params.defaultHPMasternodeList.toList(), dpp)
    }

    fun getAppList(): List<Identifier> {
        return apps.map { it.value.contractId }
    }

    fun broadcastStateTransition(
        stateTransition: StateTransitionIdentitySigned,
        identity: Identity,
        privateKey: ECKey,
        keyIndex: Int = 1
    ) {
        stateTransition.sign(identity.getPublicKeyById(keyIndex)!!, privateKey.privateKeyAsHex)

        broadcastStateTransition(stateTransition)
    }

    fun broadcastStateTransition(signedStateTransition: StateTransitionIdentitySigned) {
        // TODO: validate transition structure here
        stateRepository.broadcastStateTransition(signedStateTransition)
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
        client.setSimplifiedMasternodeListManager(masternodeListManager, params.defaultHPMasternodeList.toList())
        appendWhiteList(params.defaultHPMasternodeList.toList())
    }

    /**
     * Permanently add a list of masternodes that can only be used by the DAPI client
     * @param banList List<String> a list of IP addresses of masternodes to use
     */
    fun appendWhiteList(banList: List<String>) {
        banList.forEach {
            client.dapiAddressListProvider.addAcceptedAddress(it)
        }
    }

    fun useValidNodes() {
        val mnList = getMnList()
        val validList = mnList.filter {
            it["isValid"] == true
        }
        client = DapiClient(validList.map { (it["service"] as String).split(":")[0] }, dpp)
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
