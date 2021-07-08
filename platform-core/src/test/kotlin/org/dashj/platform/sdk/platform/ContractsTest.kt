package org.dashj.platform.sdk.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContractsTest : PlatformNetwork() {

    @Test
    fun getTest() {
        for (app in platform.apps.keys) {
            val expectedContractId = platform.apps[app]!!.contractId
            val contract = platform.contracts.get(expectedContractId)!!

            assertEquals(expectedContractId, contract.id)
        }
    }
}
