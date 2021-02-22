package org.dashevo.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NamesTest : PlatformNetwork() {

    @Test
    fun getTest() {
        val label = "x-hash-eng"
        val ownerId = "CtbiVfTcKFpt5dhmWYHwK9yp1CKR5dci9WdHokYGqyjG";

        val byLabel = DomainDocument(platform.names.get(label)!!)
        val byResolve = DomainDocument(platform.names.resolve("$label.${Names.DEFAULT_PARENT_DOMAIN}")!!)
        val byLabelAndDomain = DomainDocument(platform.names.get(label, Names.DEFAULT_PARENT_DOMAIN)!!)
        val byRecord = DomainDocument(platform.names.resolveByRecord("dashUniqueIdentityId", ownerId).first())
        val bySearch = DomainDocument(platform.names.search(label, Names.DEFAULT_PARENT_DOMAIN, retrieveAll = false, limit = 1).first())

        assertEquals(label, byLabel.label)
        assertEquals(label, byResolve.label)
        assertEquals(label, byLabelAndDomain.label)
        assertEquals(label, byRecord.label)
        assertEquals(label, bySearch.label)
    }

    @Test
    fun getListTest() {
        val bySearch = platform.names.search("x-hash", Names.DEFAULT_PARENT_DOMAIN, retrieveAll = false, limit = 10)

        val ids = bySearch.map {
            DomainDocument(it).dashUniqueIdentityId!!
        }

        val byGetList = platform.names.getList(ids, true, 0)

        assertEquals(bySearch.size, byGetList.size)
        for (i in bySearch.indices) {
            assertTrue(bySearch[i] == byGetList[i])
        }
    }

    @Test
    fun domainTest() {
        val fullName = "username.dash"

        val (domain, label) = platform.names.normalizedNames(fullName)
        assertEquals("username", label)
        assertEquals(Names.DEFAULT_PARENT_DOMAIN, domain)
    }
}