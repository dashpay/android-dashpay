/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import com.google.common.base.Stopwatch
import org.dashj.platform.dapiclient.model.GetStatusResponse
import org.dashj.platform.dapiclient.provider.DAPIAddress
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions

/*
    Calls getStatus on every masternode in the default list for a network

    Reports time to reply in ms for each node
 */
class GetStatus {
    companion object {
        lateinit var sdk: Client

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: GetStatus network")
                return
            }
            sdk = Client(ClientOptions(network = args[0]))
            getStatus()
        }

        private fun getStatus() {
            val results = hashMapOf<DAPIAddress, GetStatusResponse?>()

            val nodeList = sdk.platform.params.defaultHPMasternodeList.toMutableList()
            val total = nodeList.size
            var success = 0
            var successFallback = 0
            for (node in nodeList) {
                val watch = Stopwatch.createStarted()
                try {
                    val status = sdk.platform.client.getStatus(DAPIAddress(node), 0)
                    success++
                    results[status!!.address!!] = status
                    println("$node: Get status successful: $watch; errors: ${status.status.name}")
                } catch (e: Exception) {
                    watch.stop()
                    println("$node: Get status failed: ${e.message} after $watch")
                    results[DAPIAddress(node)] = null
                    try {
                        sdk.platform.client.getBlockByHeight(100)
                        successFallback++
                        println("$node: getBlockByHeight successful")
                    } catch (e: Exception) {
                        println("$node: getBlockByHeight failed: ${e.message}")
                    }
                }
            }
            println("getStatus() Results: $success/$total (${success.toDouble() / total})")
            println("getBlockByHeight() Results: $successFallback/$total (${successFallback.toDouble() / total})")
            println("Overall Results: ${(success + successFallback)}/$total (${(successFallback + success).toDouble() / total})")
            for (s in results) {
                println("${s.key.host}: ${s.value?.duration} ms; errors: ${s.value?.status}")
            }
        }
    }
}
