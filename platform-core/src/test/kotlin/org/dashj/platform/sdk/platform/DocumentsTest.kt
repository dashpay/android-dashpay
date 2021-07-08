package org.dashj.platform.sdk.platform

import org.bitcoinj.core.Sha256Hash
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.dpp.util.Entropy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class DocumentsTest : PlatformNetwork() {

    @Test
    fun createTest() {
        try {
            val data = hashMapOf(
                "username" to "dash83937",
                "password" to "7yHg4F84"
            )
            val document = platform.documents.create(
                "security.login",
                Identifier.from("46ez8VqoDbR8NkdXwFaf9Tp8ukBdQxN8eYs8JNMnUyK9"),
                data.toMutableMap()
            )
            fail<Nothing>("The contract should not be found for security")
        } catch (e: Exception) {
            // good
        }

        val saltedDomainHash = Sha256Hash.wrap(Entropy.generate())
        val data = hashMapOf(
            "saltedDomainHash" to saltedDomainHash
        )
        val document = platform.documents.create(
            "dpns.preorder",
            Identifier.from("46ez8VqoDbR8NkdXwFaf9Tp8ukBdQxN8eYs8JNMnUyK9"),
            data.toMutableMap()
        )

        assertEquals(saltedDomainHash, document.data["saltedDomainHash"])
    }

    @Test
    fun getTest() {
        println("Make a query for domain documents")
        val results = platform.documents.get(Names.DPNS_DOMAIN_DOCUMENT, DocumentQuery.builder().build())

        assertTrue(results.isNotEmpty())

        println(results.map { it.toJSON() })
        println(DomainDocument(results.first()).dashUniqueIdentityId)

        val firstDoc = DomainDocument(results.first())

        // query by owner id
        val secondDoc = DomainDocument(platform.documents.create(Names.DPNS_DOMAIN_DOCUMENT, firstDoc.ownerId, firstDoc.document.data.toMutableMap()))

        assertEquals(firstDoc.label, secondDoc.label)
    }

    @Test
    fun getAllTest() {
        println("Make a query for domain documents")
        val results = platform.documents.getAll(Names.DPNS_DOMAIN_DOCUMENT, DocumentQuery.builder().build())

        val limitedResults = platform.documents.getAll(Names.DPNS_DOMAIN_DOCUMENT, DocumentQuery.builder().limit(301).startAt(2).build())

        assertTrue(results.isNotEmpty())
        assertEquals(301, limitedResults.size)

        assertEquals(results[1].id, limitedResults[0].id)
    }

    @Test
    fun getNoResultsTest() {
        val results = platform.documents.get(
            Names.DPNS_DOMAIN_DOCUMENT,
            DocumentQuery.builder()
                .where("normalizedLabel", "==", "39383838")
                .where("normalizedParentDomainName", "==", "dash")
                .build()
        )
        assertEquals(results, listOf<ByteArray>())
    }
}
