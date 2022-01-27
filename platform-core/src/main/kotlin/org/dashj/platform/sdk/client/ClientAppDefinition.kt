/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashj.platform.sdk.client

import org.dashj.platform.dpp.contract.DataContract
import org.dashj.platform.dpp.identifier.Identifier

data class ClientAppDefinition(
    val contractId: Identifier,
    var contract: DataContract? = null
) {
    constructor(contractId: String, contract: DataContract? = null) :
    this(Identifier.from(contractId), contract)
}
