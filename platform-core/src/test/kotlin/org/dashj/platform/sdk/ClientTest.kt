/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.sdk
import org.bitcoinj.params.BinTangDevNetParams
import org.bitcoinj.params.TestNet3Params
import org.dashj.platform.sdk.client.ClientOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClientTest {

    @Test
    fun testnetClientTest() {
        val options = ClientOptions(network = "testnet")
        val client = Client(options)
        assertEquals(client.platform.params, TestNet3Params.get())
        client.platform.useValidNodes()
        assertTrue(client.platform.check())
    }

    @Test
    fun schnappsClientTest() {
        val client = Client(ClientOptions(network = "bintang"))
        assertEquals(client.platform.params, BinTangDevNetParams.get())
    }
}
