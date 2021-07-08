package org.dashj.platform.sdk.platform.multicall

interface MulticallMethod<T> {
    fun execute(): T
}
