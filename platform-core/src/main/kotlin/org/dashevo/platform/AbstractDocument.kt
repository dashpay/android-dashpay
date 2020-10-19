/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.dashevo.dpp.document.Document

abstract class AbstractDocument(val document: Document) {

    val dataContractId: String
        get() = document.dataContractId
    val id: String
        get() = document.id
    val ownerId: String
        get() = document.ownerId
    val protocolVersion: Int
        get() = document.protocolVersion
    val revision: Int
        get() = document.revision
    val createdAt: Long?
        get() = document.createdAt
    val updatedAt: Long?
        get() = document.updatedAt

    fun toJSON() : Map<String, Any?> {
        return document.toJSON()
    }

    protected fun getFieldString(fieldName: String): String? {
        val field = document.data[fieldName]
        return if (field != null)
            field as String
        else null
    }

    protected fun getFieldByteArray(fieldName: String): ByteArray? {
        val field = document.data[fieldName]
        return if (field != null)
            field as ByteArray
        else null
    }

    protected fun getFieldMap(fieldName: String): Map<String, Any>? {
        val field = document.data[fieldName]
        return if (field != null)
            field as Map<String, Any>
        else null
    }
}