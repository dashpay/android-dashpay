/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.dashpay.callback

import org.dashevo.dpp.identifier.Identifier

interface SendContactRequestCallback {
    fun onComplete(fromUser: Identifier, toUser: Identifier)
    fun onTimeout(fromUser: Identifier, toUser: Identifier)
}