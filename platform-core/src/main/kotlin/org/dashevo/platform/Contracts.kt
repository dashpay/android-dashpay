/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.bitcoinj.core.ECKey
import org.dashevo.dpp.contract.DataContractCreateTransition
import org.dashevo.dpp.contract.DataContract
import org.dashevo.dpp.identifier.Identifier
import org.dashevo.dpp.identity.Identity
import java.util.*

class Contracts(val platform: Platform) {

    fun broadcast(dataContract: DataContract, identity: Identity, privateKey: ECKey, index: Int) : DataContractCreateTransition {
        val  dataContractCreateTransition = platform.dpp.dataContract.createStateTransition(dataContract);

        platform.broadcastStateTransition(dataContractCreateTransition, identity, privateKey, index)

        return dataContractCreateTransition;
    }

    fun create(documentDefinitions: MutableMap<String, Any?>, identity: Identity): DataContract {
        return platform.dpp.dataContract.createDataContract(identity.id.toBuffer(), documentDefinitions)
    }

    fun get(identifier: String): DataContract? {
        return get(Identifier.from(identifier))
    }

    fun get(identifier: Identifier): DataContract? {
        var localContract: ContractInfo? = null;

        for (appName in platform.apps.keys) {
            val app = platform.apps[appName]
            if (app!!.contractId == identifier) {
                localContract = app
                break;
            }
        }

        if (localContract?.dataContract != null) {
            return localContract!!.dataContract;
        } else {
            try {
                val rawContract = platform.client.getDataContract(identifier.toBuffer()) ?: return null

                val contract = platform.dpp.dataContract.createFromBuffer(rawContract.toByteArray())
                val app = ContractInfo(contract.id, contract)
                // If we do not have even the identifier in this.apps, we add it with timestamp as key
                if (localContract == null) {
                    platform.apps[Date().toString()] = app
                } else {
                    localContract.dataContract = contract
                }
                return contract;
            } catch (e: Exception) {
                println("Failed to get dataContract" + e)
                throw e
            }

        }
    }
}