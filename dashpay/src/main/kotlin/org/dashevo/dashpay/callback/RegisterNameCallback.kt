package org.dashevo.dashpay.callback

interface RegisterNameCallback {
    fun onComplete(names: List<String>)
    fun onTimeout(incompleteNames: List<String>)
}