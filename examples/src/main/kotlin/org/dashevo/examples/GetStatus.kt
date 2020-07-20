/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.dashevo.Client

class GetStatus {
    companion object {
        val sdk = Client("palinka")

        @JvmStatic
        fun main(args: Array<String>) {
            getStatus()
        }

        fun getStatus() {
            sdk.isReady()

            val total = 20
            var success = 0
            var successFallback = 0
            for (i in 0 until total) {
                try {
                    sdk.platform.client.getStatus()
                    success++
                    println("$i: Get status successful")
                } catch (e: Exception) {
                    println("$i: Get status failed: ${e.message}")
                    try {
                        sdk.platform.client.getBlockByHeight(100)
                        successFallback++
                        println("$i: getBlockByHeight successful")
                    } catch (e: Exception) {
                        println("$i: getBlockByHeight failed: ${e.message}")
                    }
                }
            }
            println("getStatus() Results: $success/$total (${success.toDouble()/total})")
            println("getBlockByHeight() Results: $successFallback/$total (${successFallback.toDouble()/total})")
            println("Overall Results: ${(success + successFallback)}/$total (${(successFallback + success).toDouble()/total})")
        }
    }
}