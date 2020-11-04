/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.bitcoinj.core.ECKey
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.Factory
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identifier.Identifier
import org.dashevo.dpp.identity.Identity
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Documents(val platform: Platform) {

    companion object {
        const val DOCUMENT_LIMIT = 100
        private val log: Logger = LoggerFactory.getLogger(Documents::class.java)
    }

    fun broadcast(identity: Identity, privateKey: ECKey, create: List<Document>?, replace: List<Document>? = null, delete: List<Document>? = null) {
        val transitionMap = hashMapOf<String, List<Document>?>()
        if (create != null)
            transitionMap["create"] = create
        if (replace != null)
            transitionMap["replace"] = replace
        if (delete != null)
            transitionMap["delete"] = delete

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

        val dataContract = platform.contracts.get(platform.apps[appName]!!.contractId);

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
        var appName: String
        var fieldType: String
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

    fun get(typeLocator: String, opts: DocumentQuery): List<Document> {
        val appNames = platform.apps.keys

        val (appName, fieldType) = getAppnameAndType(typeLocator, appNames)

        if (!platform.apps.containsKey(appName)) {
            throw Exception("No app named $appName specified.")
        }
        val appDefinition = platform.apps[appName];
        if (appDefinition == null || appDefinition.contractId.toBuffer().isEmpty()) {
            throw Exception("Missing contract ID for $appName")
        }

        val contractId = appDefinition.contractId;
        try {
            val rawDocuments = platform.client.getDocuments(appDefinition.contractId.toBuffer(), fieldType, opts)

            return rawDocuments!!.map {
                platform.dpp.document.createFromBuffer(it, Factory.Options(true))
            }
        } catch (e: Exception) {
            log.error("Document creation: unable to get documents of ${contractId}: $e")
            throw e
        }
    }
}