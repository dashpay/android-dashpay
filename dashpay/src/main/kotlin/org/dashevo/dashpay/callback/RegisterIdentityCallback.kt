package org.dashevo.dashpay.callback

interface RegisterIdentityCallback {
    fun onComplete(uniqueId: String)
    fun onTimeout()
}