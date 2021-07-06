package org.dashevo.examples

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.dashevo.Client
import org.dashevo.dashpay.ContactRequests
import org.dashevo.dashpay.RetryDelayType
import org.dashj.platform.dpp.identifier.Identifier
import org.dashevo.client.ClientOptions

class WatchContactRequest {
    companion object {
        val sdk = Client(ClientOptions(network = "mobile"))

        @JvmStatic
        fun main(args: Array<String>) {
            val from = Identifier.from(args[0])
            val to = Identifier.from(args[1])

            GlobalScope.launch {
                watchContactRequest(from, to)
            }
        }

        suspend fun watchContactRequest(from: Identifier, to: Identifier) {
            val platform = sdk.platform
            val contactRequest = ContactRequests(platform).watchContactRequest(from, to, 100, 333, RetryDelayType.LINEAR)
        }
    }
}