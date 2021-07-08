package org.dashj.platform.sdk.platform

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Transaction
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class IdentitiesTest : PlatformNetwork() {

    @Test
    fun getTest() {
        val results = platform.documents.get(Names.DPNS_DOMAIN_DOCUMENT, DocumentQuery.builder().limit(5).build())

        for (document in results) {
            val domainDoc = DomainDocument(document)
            val expectedIdentityId = domainDoc.dashUniqueIdentityId ?: domainDoc.dashAliasIdentityId!!
            val identity = platform.identities.get(expectedIdentityId.toString())

            assertEquals(expectedIdentityId, identity!!.id)
        }
    }

    @Test
    fun getIdentitiesTest() {
        val pubKeyHash = ECKey().pubKeyHash
        val results = platform.identities.getByPublicKeyHash(pubKeyHash)

        assertNull(results)
    }

    @Test
    fun getAssetLockTxTest() {
        val tx = platform.client.getTransaction(assetLockTxId)
        assertEquals(assetLockTxId, Transaction(platform.params, tx!!.toByteArray()).txId.toString())
    }
}
