package org.dashevo.examples

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.dashevo.Client
import org.dashevo.dashpay.ContactRequests
import org.dashevo.dashpay.RetryDelayType
import org.dashevo.dpp.identifier.Identifier

class WatchContactRequest {
    companion object {
        val sdk = Client("mobile")

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
            sdk.isReady()
            val contactRequest = ContactRequests(platform).watchContactRequest(from, to, 100, 333, RetryDelayType.LINEAR)
        }
    }
}