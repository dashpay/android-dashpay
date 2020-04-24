package org.dashevo.platform

import org.dashevo.dpp.contract.Contract

/**
 *
 * @property contractId String
 * @property contract Contract?
 * @constructor
 */
data class ContractInfo(val contractId: String) {
    var contract: Contract? = null

    constructor(contractId: String, contract: Contract) : this(contractId) {
        this.contract = contract
    }
}