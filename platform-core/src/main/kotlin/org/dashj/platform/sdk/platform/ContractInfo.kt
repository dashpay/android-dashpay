/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.sdk.platform

import org.dashj.platform.dpp.contract.DataContract
import org.dashj.platform.dpp.identifier.Identifier

/**
 *
 * @property contractId String
 * @property dataContract Contract?
 * @constructor
 */
data class ContractInfo(val contractId: Identifier, var dataContract: DataContract? = null) {

    constructor(contractId: String, dataContract: DataContract? = null) :
    this(Identifier.Companion.from(contractId), dataContract)

    constructor(contractId: ByteArray, dataContract: DataContract? = null) :
    this(Identifier.from(contractId), dataContract)
}
