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
}