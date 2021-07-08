/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.dashpay.callback

import org.dashj.platform.dpp.identifier.Identifier

interface SendContactRequestCallback {
    fun onComplete(fromUser: Identifier, toUser: Identifier)
    fun onTimeout(fromUser: Identifier, toUser: Identifier)
}
