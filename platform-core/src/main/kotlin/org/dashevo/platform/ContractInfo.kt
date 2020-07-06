/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.dashevo.dpp.contract.DataContract

/**
 *
 * @property contractId String
 * @property contract Contract?
 * @constructor
 */
data class ContractInfo(val contractId: String) {
    var contract: DataContract? = null

    constructor(contractId: String, contract: DataContract) : this(contractId) {
        this.contract = contract
    }
}