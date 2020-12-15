/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identifier.Identifier
import org.dashevo.dpp.util.HashUtils

class DomainDocument(document: Document) : AbstractDocument(document) {
    val label: String
        get() = getFieldString("label")!!
    val normalizedLabel: String
        get() = getFieldString("normalizedLabel")!!
    val normalizedParentDomainName: String
        get() = getFieldString("normalizedParentDomainName")!!
    val dashAliasIdentityId: Identifier?
        get() {
            val records = getFieldMap("records")
            return if (records != null && records.containsKey("dashAliasIdentityId")) {
                Identifier.from(records["dashAliasIdentityId"])
            } else {
                null
            }
        }
    val dashUniqueIdentityId: Identifier?
        get() {
            val records = getFieldMap("records")
            return if (records != null && records.containsKey("dashUniqueIdentityId")) {
                Identifier.from(records["dashUniqueIdentityId"])
            } else {
                null
            }
        }
    val allowSubdomains: Boolean
        get() {
            val subdomainRules = getFieldMap("subdomainRules")
            return if (subdomainRules != null && subdomainRules.containsKey("allowSubdomains")) {
                subdomainRules["allowSubdomains"] as Boolean
            } else {
                false
            }
        }
    val preorderSalt: ByteArray
        get() = getFieldByteArray("preorderSalt")!!
}