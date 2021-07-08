package org.dashevo.platform.multicall

interface MulticallMethod<T> {
    fun execute(): T
}
