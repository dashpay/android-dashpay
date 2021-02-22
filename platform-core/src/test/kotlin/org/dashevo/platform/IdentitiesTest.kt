package org.dashevo.platform

import org.bitcoinj.core.ECKey
import org.dashevo.dapiclient.model.DocumentQuery
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class IdentitiesTest : PlatformNetwork() {

    @Test
    fun getTest() {
        val results = platform.documents.get(Names.DPNS_DOMAIN_DOCUMENT, DocumentQuery.builder().limit(5).build())

        for (document in results) {
            val domainDoc = DomainDocument(document)
            val expectedContractId = domainDoc.dashUniqueIdentityId!!
            val identity = platform.identities.get(expectedContractId.toString())

            assertEquals(expectedContractId, identity!!.id)
        }
    }

    @Test
    fun getIdentitiesTest() {
        val pubKeyHash = ECKey().pubKeyHash
        val results = platform.identities.getByPublicKeyHash(pubKeyHash)

        assertNull(results)
    }


}