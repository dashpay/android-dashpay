/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.dashevo.dpp.document.Document

class DomainDocument(document: Document) : AbstractDocument(document) {
    val label: String
        get() = getFieldString("label")!!
    val normalizedLabel: String
        get() = getFieldString("normalizedLabel")!!
    val normalizedParentDomainName: String
        get() = getFieldString("normalizedParentDomainName")!!
    val dashAliasIdentityId: String?
        get() {
            val records = getFieldMap("records")
            return if (records != null && records.containsKey("dashAliasIdentityId")) {
                records["dashAliasIdentityId"] as String
            } else {
                null
            }
        }
    val dashUniqueIdentityId: String?
        get() {
            val records = getFieldMap("records")
            return if (records != null && records.containsKey("dashUniqueIdentityId")) {
                records["dashUniqueIdentityId"] as String
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