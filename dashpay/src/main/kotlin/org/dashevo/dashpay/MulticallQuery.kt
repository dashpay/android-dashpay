/**
 * Copyright (c) 2021-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.dashpay

class MulticallQuery<T> (
    val calls: Int,
    val failures: Int,
    val results: HashSet<T?>
) {
    val successRate: Double = (calls - failures).toDouble() / calls.toDouble()

    fun success() : Boolean {
        return successRate > 0.51 && results.size == 1
    }
}
