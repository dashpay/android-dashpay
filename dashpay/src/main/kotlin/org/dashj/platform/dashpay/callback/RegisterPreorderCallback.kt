/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.dashpay.callback

interface RegisterPreorderCallback {
    fun onComplete(names: List<String>)
    fun onTimeout(incompleteNames: List<String>)
}
