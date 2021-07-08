/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.examples

import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.bitcoinj.core.ECKey
import org.dashj.platform.dapiclient.DapiClient
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.sdk.Client
import org.dashj.platform.sdk.client.ClientOptions
import org.dashj.platform.sdk.platform.Platform

class GetNetworkStatus {

    companion object {

        private const val identityId = "5y69D5k71omozvFU55gv3os62Ynwd8DstyJFUCdiZr1P"
        private val identifier = Identifier.from(identityId)
        private lateinit var dataContractId: Identifier
        private val pubKeyHash = ECKey().pubKeyHash

        lateinit var sdk: Client
        lateinit var platform: Platform

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: GetStatus network")
                return
            }
            sdk = Client(ClientOptions(network = args[0]))
            platform = sdk.platform
            dataContractId = platform.apps["dashpay"]!!.contractId

            start()
        }

        private fun getMnList(): List<Map<String, Any>> {
            val success = 0
            do {
                try {
                    val baseBlockHash = platform.client.getBlockHash(0)
                    val blockHash = platform.client.getBestBlockHash()

                    val mnListDiff = platform.client.getMnListDiff(baseBlockHash!!, blockHash!!)
                    return mnListDiff!!["mnList"] as List<Map<String, Any>>
                } catch (e: Exception) {
                    println("Error: $e")
                }
            } while (success == 0)
            return listOf()
        }

        private fun start() {
            // Get full masternode list
            val mnList = getMnList()
            val invalidNodes = mnList.filter { it["isValid"] == false }
            val validNodes = mnList.filter { it["isValid"] == true }

            println("${mnList.size} masternodes found")
            println("Ignoring ${mnList.filter { it["isValid"] == false }.size} invalid masternodes")
            println("Checking RPC connections for ${mnList.filter { it["isValid"] == true }.size} valid masternodes\n****************")

            val badNodes = arrayListOf<Map<String, Any>>()
            val goodNodes = arrayListOf<String>()
            for (mn in validNodes) {
                val service = (mn["service"] as String).split(":")[0]

                val rpcClient = DapiClient(service)

                println(service)
                var jsonRpcSuccess = true
                var coreGrpcSuccess = true
                var platformGrpcSuccess = true
                var getDataContractSuccess = true
                var getDocumentsSuccess = true
                var getIdentitiesByPublicKeyHashesSuccess = true
                try {
                    val jsonRpcResponse = rpcClient.getBestBlockHash()
                    println("\t+ L1 JSON-RPC Success.\tBest hash $jsonRpcResponse")
                } catch (e: Exception) {
                    println("\tX L1 JSON-RPC Failure")
                    jsonRpcSuccess = false
                }

                try {
                    val status = rpcClient.getStatus()
                    println("\t\u2713 L1 gRPC Success.\tBlock count: ${status!!.chain.blockCount}")
                } catch (e: Exception) {
                    println("\tX L1 gRPC Failure ($e)")
                    coreGrpcSuccess = false
                }

                try {
                    rpcClient.getDataContract(dataContractId.toBuffer())
                    println("\t+ L1 gRPC Success.\tgetDataContract DashPay")
                } catch (e: Exception) {
                    println("\tX L1 gRPC Failure.\tgetDataContract DashPay")
                    getDataContractSuccess = false
                }

                try {
                    rpcClient.getDocuments(dataContractId.toBuffer(), "profile", DocumentQuery.builder().build())
                    println("\t+ L1 gRPC Success.\tgetDocuments dashpay.profile")
                } catch (e: Exception) {
                    println("\tX L1 gRPC Failure.\tgetDocuments dashpay.profile")
                    getDocumentsSuccess = false
                }

                try {
                    rpcClient.getIdentitiesByPublicKeyHashes(listOf(pubKeyHash))
                    println("\t+ L1 gRPC Success.\tgetIdentitiesByPublicKeyHashes")
                } catch (e: Exception) {
                    println("\tX L1 gRPC Failure.\tgetIdentitiesByPublicKeyHashes")
                    getIdentitiesByPublicKeyHashesSuccess = false
                }

                // Attempt to retrieve an identity to see if gRPC is responding on the node
                // Note: this does not need to be an identity that actually exists
                try {
                    val response = rpcClient.getIdentity(identifier.toBuffer())
                    val id = platform.dpp.identity.createFromBuffer(response!!.toByteArray()).id.toString()
                    println("\t+ L2 gRPC Success.\tRetrieved identity: $id")
                } catch (e: StatusRuntimeException) {
                    if (e.status == Status.NOT_FOUND) {
                        println("\t+ L2 gRPC Success")
                    } else {
                        println("\tX L2 gRPC Failure")
                        platformGrpcSuccess = false
                    }
                } catch (e: Exception) {
                }
                println(rpcClient.reportErrorStatus())

                if (!jsonRpcSuccess || !coreGrpcSuccess || !platformGrpcSuccess || !getDataContractSuccess || !getDocumentsSuccess ||
                    !getIdentitiesByPublicKeyHashesSuccess
                ) {
                    badNodes.add(
                        mapOf(
                            "ip" to service.split(":")[0],
                            "response" to mapOf(
                                "JSON-RPC Success" to jsonRpcSuccess,
                                "Core gRPC Success" to coreGrpcSuccess,
                                "Platform gRPC Success" to platformGrpcSuccess,
                                "getDataContract Success" to getDataContractSuccess,
                                "getDocuments Success" to getDocumentsSuccess,
                                "Platform gRPC Success" to platformGrpcSuccess,
                                "getDataContract Success" to getDataContractSuccess,
                                "getDocuments Success" to getDocumentsSuccess,
                                "getIdentitiesByPublicKeyHashes Success" to getIdentitiesByPublicKeyHashesSuccess
                            ),
                            "report" to rpcClient.reportErrorStatus()
                        )
                    )
                } else {
                    goodNodes.add(service.split(":")[0])
                }
            }

            println("${badNodes.size} masternodes failed to respond to one or more requests")
            println("List of non-responsive nodes:\n $badNodes")
            println("\nList of nodes with no errors:\n\t${goodNodes}\n")

            println("${invalidNodes.size} masternodes were marked invalid in DML")

            // Failure summary
            println("Nodes with JSON-RPC Failures:      ${badNodes.filter { (it["response"] as Map<String, Any>)["JSON-RPC Success"] == false }.size}")
            println("Nodes with Core gRPC Failures:     ${badNodes.filter { (it["response"] as Map<String, Any>)["Core gRPC Success"] == false }.size}")
            println("Nodes with Platform gRPC Failures: ${badNodes.filter { (it["response"] as Map<String, Any>)["Platform gRPC Success"] == false }.size}")
            println("Nodes with getDataContract Failures: ${badNodes.filter { (it["response"] as Map<String, Any>)["getDataContract Success"] == false }.size}")
            println("Nodes with getDocument Failures: ${badNodes.filter { (it["response"] as Map<String, Any>)["getDocuments Success"] == false }.size}")
            println("Nodes with getIdentitiesByPublicKeyHashes Failures: ${badNodes.filter { (it["response"] as Map<String, Any>)["getIdentitiesByPublicKeyHashes Success"] == false }.size}")
            println("Nodes with getDocument(dashpay) Failures: ${badNodes.filter { (it["response"] as Map<String, Any>)["getDocuments Success"] == false }.map { it["ip"] }}")
            println("Nodes with getDataContract(dashpay) Failures: ${badNodes.filter { (it["response"] as Map<String, Any>)["getDataContract Success"] == false }.map { it["ip"] } }")
            println("Nodes with getIdentitiesByPublicKeyHashes Failures: ${badNodes.filter { (it["response"] as Map<String, Any>)["getIdentitiesByPublicKeyHashes Success"] == false }.map { it["ip"] } }")
            // Success summary
            println("Nodes with no errors: ${goodNodes.size}")
        }
    }
}
