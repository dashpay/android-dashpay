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

            for(app in platform.apps) {
                try {
                    val contract = platform.contracts.get(app.value.contractId)

                    println("app: ${app.key} Contract: ${contract!!.contractId}")
                    println(contract.toJSON().toString())
                    println(JSONObject(contract.toJSON()).toString(2))
                } catch (e: Exception) {
                    println("\nError retrieving results for app:${app.key}")
                    println(e.message);
                }
            }
        }
    }
}