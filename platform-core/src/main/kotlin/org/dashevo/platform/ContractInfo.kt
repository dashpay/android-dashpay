/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
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