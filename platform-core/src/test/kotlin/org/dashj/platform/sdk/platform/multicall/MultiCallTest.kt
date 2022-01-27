package org.dashj.platform.sdk.platform.multicall

import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dpp.Factory
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.sdk.platform.PlatformNetwork
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MultiCallTest : PlatformNetwork() {
    val dataContractId = platform.apps["dpns"]!!.contractId
    val documentType = "domain"
    val defaultQuery = DocumentQuery.builder()
        .where("normalizedParentDomainName", "==", "dash")
        .where("normalizedLabel", "==", "hash")
        .build()
    val noResultsQuery = DocumentQuery.builder()
        .where("normalizedParentDomainName", "==", "dash")
        .where("normalizedLabel", "==", "39383838")
        .build()
    val invalidQuery = DocumentQuery.builder()
        .where("normalizedParentDomainName", "==", "dash")
        .where("normalizedLabel", "==", "")
        .build()
    fun multicall(callType: MulticallQuery.Companion.CallType, opts: DocumentQuery = defaultQuery):
    Pair<MulticallQuery.Companion.Status, List<Document>> {
        try {
            val domainQuery = MulticallListQuery(
                object : MulticallMethod<List<ByteArray>> {
                    override fun execute(): List<ByteArray> {
                        return platform.client.getDocuments(dataContractId.toBuffer(), documentType, opts, false, platform.documentsRetryCallback).documents
                    }
                },
                callType
            )
            return when (domainQuery.query()) {
                MulticallQuery.Companion.Status.FOUND -> {
                    Pair(
                        domainQuery.status(),
                        domainQuery.getResult()!!.map {
                            platform.dpp.document.createFromBuffer(it, Factory.Options(true))
                        }
                    )
                }
                MulticallQuery.Companion.Status.AGREE -> {
                    if (domainQuery.foundSuccess()) {
                        Pair(
                            domainQuery.status(),
                            domainQuery.getResult()!!.map {
                                platform.dpp.document.createFromBuffer(it, Factory.Options(true))
                            }
                        )
                    } else {
                        Pair(domainQuery.status(), listOf())
                    }
                }
                MulticallQuery.Companion.Status.NOT_FOUND -> {
                    Pair(domainQuery.status(), listOf())
                }
                MulticallQuery.Companion.Status.ERRORS -> {
                    throw domainQuery.exception()
                }
                MulticallQuery.Companion.Status.DISAGREE -> {
                    throw MulticallException(listOf())
                }
            }
        } catch (e: Exception) {
            println("Document query: unable to get documents of $dataContractId: $e")
            throw e
        }
    }
    @Test
    fun unanimousTest() {
        val results = multicall(MulticallQuery.Companion.CallType.UNANIMOUS)
        assertEquals(MulticallQuery.Companion.Status.AGREE, results.first)
        assertTrue(results.second.isNotEmpty())
    }

    @Test
    fun unanimousNotFoundTest() {
        val results = multicall(MulticallQuery.Companion.CallType.UNANIMOUS, noResultsQuery)
        assertEquals(MulticallQuery.Companion.Status.AGREE, results.first)
        assertTrue(results.second.isEmpty())
    }

    @Test
    fun firstFoundTest() {
        val results = multicall(MulticallQuery.Companion.CallType.FIRST)
        assertEquals(MulticallQuery.Companion.Status.FOUND, results.first)
        assertTrue(results.second.isNotEmpty())
    }

    @Test
    fun firstFoundInvalidTest() {
        val results = multicall(MulticallQuery.Companion.CallType.FIRST, invalidQuery)
        assertEquals(MulticallQuery.Companion.Status.NOT_FOUND, results.first)
        assertTrue(results.second.isEmpty())
    }

    @Test
    fun untilFoundTest() {
        val results = multicall(MulticallQuery.Companion.CallType.UNTIL_FOUND)
        assertEquals(MulticallQuery.Companion.Status.FOUND, results.first)
        assertTrue(results.second.isNotEmpty())
    }

    @Test
    fun untilFoundNotFoundTest() {
        val results = multicall(MulticallQuery.Companion.CallType.UNTIL_FOUND, noResultsQuery)
        assertEquals(MulticallQuery.Companion.Status.NOT_FOUND, results.first)
        assertTrue(results.second.isEmpty())
    }

    @Test
    fun untilFoundInvalidTest() {
        val results = multicall(MulticallQuery.Companion.CallType.UNTIL_FOUND, invalidQuery)
        assertEquals(MulticallQuery.Companion.Status.NOT_FOUND, results.first)
        assertTrue(results.second.isEmpty())
    }

    @Test
    fun majorityFoundTest() {
        val results = multicall(MulticallQuery.Companion.CallType.MAJORITY_FOUND)
        assertEquals(MulticallQuery.Companion.Status.FOUND, results.first)
        assertTrue(results.second.isNotEmpty())
    }

    @Test
    fun majorityTest() {
        val results = multicall(MulticallQuery.Companion.CallType.MAJORITY)
        assertEquals(MulticallQuery.Companion.Status.FOUND, results.first)
        assertTrue(results.second.isNotEmpty())
    }

    @Test
    fun majorityNotFoundTest() {
        val results = multicall(MulticallQuery.Companion.CallType.MAJORITY, noResultsQuery)
        assertEquals(MulticallQuery.Companion.Status.NOT_FOUND, results.first)
        assertTrue(results.second.isEmpty())
    }
}
