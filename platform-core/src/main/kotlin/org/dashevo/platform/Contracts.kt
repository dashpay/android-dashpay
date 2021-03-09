/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.bitcoinj.core.ECKey
import org.dashevo.client.ClientAppDefinition
import org.dashevo.dpp.contract.DataContract
import org.dashevo.dpp.contract.DataContractCreateTransition
import org.dashevo.dpp.identifier.Identifier
import org.dashevo.dpp.identity.Identity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class Contracts(val platform: Platform) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(Contracts::class.java)
    }

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
        var localContract: ClientAppDefinition? = null;

        for (appName in platform.apps.keys) {
            val app = platform.apps[appName]
            if (app!!.contractId == identifier) {
                localContract = app
                break
            }
        }

        if (localContract?.contract != null) {
            return localContract.contract;
        } else {
            try {
                val rawContract = platform.client.getDataContract(identifier.toBuffer(), platform.contractsRetryCallback) ?: return null

                val contract = platform.dpp.dataContract.createFromBuffer(rawContract.toByteArray())
                val app = ClientAppDefinition(contract.id, contract)
                // If we do not have even the identifier in this.apps, we add it with timestamp as key
                if (localContract == null) {
                    platform.apps[Date().toString()] = app
                } else {
                    localContract.contract = contract
                }
                return contract;
            } catch (e: Exception) {
                log.error("Failed to get dataContract: $e")
                throw e
            }

        }
    }
}