/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.dashpay.callback

import org.dashevo.dpp.document.Document

interface UpdateProfileCallback {
    fun onComplete(uniqueId: String, profileDocument: Document)
    fun onTimeout()
}