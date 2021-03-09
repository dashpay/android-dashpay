/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashevo.client

import org.dashevo.dpp.contract.DataContract
import org.dashevo.dpp.identifier.Identifier

data class ClientAppDefinition(
    val contractId: Identifier,
    var contract: DataContract?
) {
    constructor(contractId: String, contract: DataContract? = null) :
            this(Identifier.from(contractId), contract)
}