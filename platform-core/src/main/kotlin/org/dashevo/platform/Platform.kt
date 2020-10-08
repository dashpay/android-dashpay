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
                apps["dpns"] = ContractInfo("566vcJkmebVCAb2Dkj2yVMSgGFcsshupnQqtsz1RFbcy")
                client = DapiClient(EvoNetParams.MASTERNODES.toList(), true)
            }
            params.id.contains("mobile") -> {
                apps["dpns"] = ContractInfo("CVZzFCbz4Rcf2Lmu9mvtC1CmvPukHy5kS2LNtNaBFM2N")
                apps["dashpay"] = ContractInfo("Du2kswW2h1gNVnTWdfNdSxBrC2F9ofoaZsXA6ki1PhG6")
                client = DapiClient(MobileDevNetParams.MASTERNODES.toList())
            }
            params.id.contains("palinka") -> {
                apps["dpns"] = ContractInfo("CpUg99yVZDK3CDkauTKtzSTbRJN7uH5u31zgTFYor5E8")
                apps["dashpay"] = ContractInfo("FZpu8tK7biyRdQfCdbsYt17gJmvLBycY8TJXQRocToph")
                client = DapiClient(PalinkaDevNetParams.get().defaultMasternodeList.toList(), true)
            }
        }
    }

    fun broadcastStateTransition(stateTransition: StateTransitionIdentitySigned, identity: Identity, privateKey: ECKey, keyIndex: Int = 0) {
        stateTransition.sign(identity.getPublicKeyById(keyIndex)!!, privateKey.privateKeyAsHex)
        //TODO: validate transition structure here
        client.broadcastStateTransition(stateTransition);
    }

    fun hasApp(appName: String): Boolean {
        return apps.containsKey(appName)
    }

}