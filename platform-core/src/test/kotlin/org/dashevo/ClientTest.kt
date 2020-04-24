package org.dashevo
import org.junit.jupiter.api.Test


class ClientTest {

    @Test
    fun evonetClientTest() {
        val evonetClient = Client("testnet")
    }

    @Test
    fun mobileClientTest() {
        val evonetClient = Client("mobile")
    }
}