package org.dashj.platform.sdk.platform.multicall

class MulticallException(val exceptionList: List<Exception>) : Exception("Multicall exception: $exceptionList")
