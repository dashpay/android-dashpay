/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.Factory
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identity.Identity

class Documents(val platform: Platform) {

    companion object {
        const val DOCUMENT_LIMIT = 100
    }
    fun create(typeLocator: String, identity: Identity, opts: MutableMap<String, Any?>): Document {
        val dpp = platform.dpp

        val appNames = platform.apps.keys

        val (appName, fieldType) = getAppnameAndType(typeLocator, appNames)

        if (!platform.apps.containsKey(appName)) {
            throw Exception("Cannot find contractId for $appName")
        }

        val dataContract = platform.contracts.get(platform.apps[appName]!!.contractId);

        return dpp.document.create(
            dataContract!!,
            identity.id,
            fieldType,
            opts as Map<String, Any>
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
        val app = platform.apps[appName];
        if (app!!.contractId.isEmpty()) {
            throw Exception("Missing contract ID for $appName")
        }
        val contractId = app.contractId;
        try {
            val rawDataList = platform.client.getDocuments(contractId, fieldType, opts);
            val documents = ArrayList<Document>()

            for (rawData in rawDataList!!) {
                try {
                    val doc = platform.dpp.document.createFromSerialized(rawData, Factory.Options(true))
                    documents.add(doc);
                } catch (e: Exception) {
                    println("Document creation: failure: " + e);
                }
            }
            return documents
        } catch (e: Exception) {
            println("Document creation: unable to get documents of ${contractId}");
            throw e;
        }
    }
}