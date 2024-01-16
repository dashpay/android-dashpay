/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.sdk.platform

import io.grpc.StatusRuntimeException
import org.bitcoinj.core.ECKey
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.dpp.errors.DriveErrorMetadata
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.dpp.identity.Identity
import org.dashj.platform.sdk.platform.multicall.MulticallQuery
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Documents(val platform: Platform) {

    companion object {
        const val DOCUMENT_LIMIT = 100
        private val log: Logger = LoggerFactory.getLogger(Documents::class.java)
    }

    fun broadcast(identity: Identity, privateKey: ECKey, create: List<Document>?, replace: List<Document>? = null, delete: List<Document>? = null) {
        val transitionMap = hashMapOf<String, List<Document>?>()
        if (create != null) {
            transitionMap["create"] = create
        }
        if (replace != null) {
            transitionMap["replace"] = replace
        }
        if (delete != null) {
            transitionMap["delete"] = delete
        }

        val batch = platform.dpp.document.createStateTransition(transitionMap)

        platform.broadcastStateTransition(batch, identity, privateKey)
    }

    fun create(typeLocator: String, userId: Identifier, opts: MutableMap<String, Any?>): Document {
        val dpp = platform.dpp

        val appNames = platform.apps.keys

        val (appName, fieldType) = getAppnameAndType(typeLocator, appNames)

        if (!platform.apps.containsKey(appName)) {
            throw Exception("Cannot find contractId for $appName")
        }

        val dataContract = platform.contracts.get(platform.apps[appName]!!.contractId)

        return dpp.document.create(
            dataContract!!,
            userId,
            fieldType,
            opts
        )
    }

    /**
     * Takes a document name in the form of "appname.document_type" and returns
     * "appname" and "document_type"
     * @param typeLocator String
     * @param appNames MutableSet<String>
     * @return Pair<String, String>
     */
    private fun getAppnameAndType(
        typeLocator: String,
        appNames: MutableSet<String>
    ): Pair<String, String> {
        val appName: String
        val fieldType: String
        if (typeLocator.contains('.')) {
            val split = typeLocator.split('.')
            appName = split[0]
            fieldType = split[1]
        } else {
            appName = appNames.first()
            fieldType = typeLocator
        }
        return Pair(appName, fieldType)
    }

    /**
     * Fetches all results that match the query this allows limit to be greater than 100
     * and will return more than 100 results
     */
    fun getAll(
        typeLocator: String,
        documentQuery: DocumentQuery,
        callType: MulticallQuery.Companion.CallType = MulticallQuery.Companion.CallType.FIRST
    ): List<Document> {
        val query = documentQuery.clone()
        val limit = query.limit
        var total = 0
        if (limit > 100) {
            query.limit = 100
        }
        val documents = ArrayList<Document>()
        var documentList: List<Document>
        var requests = 0

        do {
            try {
                documentList =
                    platform.documents.get(
                        typeLocator,
                        query,
                        callType
                    )
                requests += 1
                if (documentList.isNotEmpty()) {
                    when {
                        limit == -1 -> documents.addAll(documentList)
                        total + documentList.size > limit -> {
                            total - limit
                            for (i in 0 until limit - total) {
                                documents.add(documentList[i])
                            }
                        }
                        else -> documents.addAll(documentList)
                    }
                    query.startAt = documentList.last().id
                }
                total += documentList.size
            } catch (e: Exception) {
                log.warn("Exception $e")
                throw e
            }
        } while ((requests == 0 || documentList.size >= DOCUMENT_LIMIT))

        return documents
    }

    fun get(typeLocator: String, opts: DocumentQuery): List<Document> {
        return get(typeLocator, opts, MulticallQuery.Companion.CallType.FIRST)
    }

    fun get(typeLocator: String, opts: DocumentQuery, callType: MulticallQuery.Companion.CallType = MulticallQuery.Companion.CallType.FIRST): List<Document> {
        val appNames = platform.apps.keys

        val (appName, fieldType) = getAppnameAndType(typeLocator, appNames)

        if (!platform.apps.containsKey(appName)) {
            throw Exception("No app named $appName specified.")
        }
        val appDefinition = platform.apps[appName]
        if (appDefinition == null || appDefinition.contractId.toBuffer().isEmpty()) {
            throw Exception("Missing contract ID for $appName")
        }

        val contractId = appDefinition.contractId

        return get(contractId, fieldType, opts, callType)
    }

    fun get(
        dataContractId: Identifier,
        documentType: String,
        opts: DocumentQuery,
        callType: MulticallQuery.Companion.CallType = MulticallQuery.Companion.CallType.FIRST
    ): List<Document> {
        try {
            return platform.stateRepository.fetchDocuments(dataContractId, documentType, opts)
        } catch (e: StatusRuntimeException) {
            log.error(
                "Document query: unable to get documents of $dataContractId: " +
                    "${DriveErrorMetadata(e.trailers.toString())}",
                e
            )
            throw e
        } catch (e: Exception) {
            log.error("Document query: unable to get documents of $dataContractId", e)
            throw e
        }
    }
}
