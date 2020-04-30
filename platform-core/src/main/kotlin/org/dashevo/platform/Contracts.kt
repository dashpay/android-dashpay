/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.dashevo.dpp.contract.Contract
import org.dashevo.dpp.identity.Identity
import java.util.*

class Contracts(val platform: Platform) {
    fun create(documentDefinitions: MutableMap<String, Any?>, identity: Identity): Contract {
        return platform.dpp.dataContract.create(identity.id, documentDefinitions)
    }

    fun get(identifier: String): Contract? {
        var localContract: ContractInfo? = null;

        for (appName in platform.apps.keys) {
            val app = platform.apps[appName]
            if (app!!.contractId == identifier) {
                localContract = app
                break;
            }
        }

        if (localContract?.contract != null) {
            return localContract.contract;
        } else {
            try {
                val rawContract = platform.client.getDataContract(identifier) ?: return null

                val contract = platform.dpp.dataContract.createFromSerialized(rawContract.toByteArray())
                val app = ContractInfo(contract.contractId, contract)
                // If we do not have even the identifier in this.apps, we add it with timestamp as key
                if (localContract == null) {
                    platform.apps[Date().toString()] = app
                }
                return contract;
            } catch (e: Exception) {
                println("Failed to get dataContract" + e)
                throw e
            }

        }
    }
}