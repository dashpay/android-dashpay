/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashj.platform.dashpay

class Contact(
    val username: String,
    val sentRequest: ContactRequest?,
    val receivedRequest: ContactRequest?
) {
    val isContactEstablished: Boolean
        get() = sentRequest != null && receivedRequest != null
}
