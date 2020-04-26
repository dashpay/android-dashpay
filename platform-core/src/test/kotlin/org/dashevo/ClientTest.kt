/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
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