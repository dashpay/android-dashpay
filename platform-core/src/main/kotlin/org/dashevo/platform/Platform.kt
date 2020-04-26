/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.params.EvoNetParams
import org.bitcoinj.params.MobileDevNetParams
import org.dashevo.dapiclient.DapiClient
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.DashPlatformProtocol
import org.dashevo.dpp.DataProvider
import org.dashevo.dpp.contract.Contract
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identity.Identity

class Platform(val params: NetworkParameters) {

    var dataProvider: DataProvider = object : DataProvider {
        override fun fetchDataContract(s: String): Contract? {
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
        if(params.id.contains("evonet")) {
            apps["dpns"] = ContractInfo("77w8Xqn25HwJhjodrHW133aXhjuTsTv9ozQaYpSHACE3")
            client = DapiClient(EvoNetParams.MASTERNODES[1], true)
        } else if(params.id.contains("mobile")){
            apps["dpns"] = ContractInfo("ForwNrvKy8jdyoCNTYBK4gcV6o15n79DmFQio2gGac5p")
            apps["dashpay"] = ContractInfo("FW2BGfVdTLgGWGkJRjC838MPpEcL2cSfkNkwao8ooxm5")
            client = DapiClient(MobileDevNetParams.MASTERNODES[1], true)

        }
    }

}