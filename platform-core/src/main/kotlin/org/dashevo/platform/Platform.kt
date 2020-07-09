/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.params.EvoNetParams
import org.bitcoinj.params.MobileDevNetParams
import org.bitcoinj.params.PalinkaDevNetParams
import org.dashevo.dapiclient.DapiClient
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.DashPlatformProtocol
import org.dashevo.dpp.DataProvider
import org.dashevo.dpp.contract.DataContract
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identity.Identity
import org.dashevo.dpp.statetransition.StateTransitionIdentitySigned

class Platform(val params: NetworkParameters) {

    var dataProvider: DataProvider = object : DataProvider {
        override fun fetchDataContract(s: String): DataContract? {
            return contracts.get(s)
        }

        override fun fetchDocuments(s: String, s1: String, o: Any): List<Document> {
            return documents.get(s, o as DocumentQuery)
        }

        override fun fetchTransaction(s: String): Int {
            TODO()
        }

        override fun fetchIdentity(s: String): Identity? {
            return identities.get(s)
        }
    }

    val dpp = DashPlatformProtocol(dataProvider)
    val apps = HashMap<String, ContractInfo>()
    val contracts = Contracts(this)
    val documents = Documents(this)
    val identities = Identities(this)
    var names = Names(this)
    lateinit var client: DapiClient

    init {
        when {
            params.id.contains("evonet") -> {
                apps["dpns"] = ContractInfo("7DVe2cDyZMf8sDjQ46XqDzbeGKncrmkD6L96QohLmLbg")
                client = DapiClient(EvoNetParams.MASTERNODES.toList(), true)
            }
            params.id.contains("mobile") -> {
                apps["dpns"] = ContractInfo("ForwNrvKy8jdyoCNTYBK4gcV6o15n79DmFQio2gGac5p")
                apps["dashpay"] = ContractInfo("FW2BGfVdTLgGWGkJRjC838MPpEcL2cSfkNkwao8ooxm5")
                client = DapiClient(MobileDevNetParams.MASTERNODES.toList())
            }
            params.id.contains("palinka") -> {
                apps["dpns"] = ContractInfo("E56D2XC5oh5bw9o8n4Vtk9QRZczSWXweMNzbphtKSfzk")
                apps["dashpay"] = ContractInfo("Ci1eGCnhRo4op8k7wRPLsycpQfTpKMtPEm5MME5o8sj5")
                client = DapiClient(PalinkaDevNetParams.get().defaultMasternodeList.toList(), true)
            }
        }
    }

    fun broadcastStateTransition(stateTransition: StateTransitionIdentitySigned, identity: Identity, privateKey: ECKey, keyIndex: Int = 0) {
        stateTransition.sign(identity.getPublicKeyById(keyIndex)!!, privateKey.privateKeyAsHex)
        //TODO: validate transition structure here
        client.applyStateTransition(stateTransition);
    }

    fun hasApp(appName: String): Boolean {
        return apps.containsKey(appName)
    }

}