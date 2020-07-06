/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.dashevo.Client
import org.json.JSONObject

class DisplayContracts {
    companion object {
        val sdk = Client("mobile")

        @JvmStatic
        fun main(args: Array<String>) {
            getDocuments()
        }

        fun getDocuments() {
            val platform = sdk.platform
            sdk.isReady();

            for (app in platform.apps) {
                try {
                    val contract = platform.contracts.get(app.value.contractId)

                    println("app: ${app.key} DataContract: ${contract!!.id}")
                    println(JSONObject(contract.toJSON()).toString(2))
                } catch (e: Exception) {
                    println("\nError retrieving results for app:${app.key}")
                    println(e.message);
                }
            }
        }
    }
}