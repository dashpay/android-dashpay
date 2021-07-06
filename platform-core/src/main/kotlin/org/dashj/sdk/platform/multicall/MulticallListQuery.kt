package org.dashevo.platform.multicall

import org.slf4j.LoggerFactory

class MulticallListQuery<T>(method: MulticallMethod<List<T>>,
                            callType: MulticallQuery.Companion.CallType,
                            callsToMake: Int = 3,
                            requiredSuccessRate:Double = 0.51):
    MulticallQuery<List<T>>(method, callType, callsToMake, requiredSuccessRate) {

    companion object {
        private val log = LoggerFactory.getLogger(MulticallListQuery::class.java)
    }

    override fun query(): MulticallQuery.Companion.Status {
        for (i in 0 until callsToMake) {
            calls = i + 1
            log.debug("making query ${i + 1} of $callsToMake")
            try {
                val result = method.execute()

                if (result.isEmpty()) {
                    notFound++
                    if (callType == MulticallQuery.Companion.CallType.FIRST) {
                        calls = 1
                        notFound = 1
                        failures = 0
                        return MulticallQuery.Companion.Status.NOT_FOUND
                    }
                } else {
                    if (results.isEmpty()) {
                        results.add(result)
                        when(callType) {
                            MulticallQuery.Companion.CallType.FIRST -> {
                                log.debug(toString())
                                return MulticallQuery.Companion.Status.FOUND
                            }
                            MulticallQuery.Companion.CallType.UNTIL_FOUND -> {
                                log.debug(toString())
                                return MulticallQuery.Companion.Status.FOUND
                            }
                        }
                    } else {
                        var equal = true
                        for (r in results as Set<List<T>>) {
                            if (equal && result.size == r.size) {
                                for (j in result.indices) {
                                    if (result[j] is ByteArray) {
                                        if (!(result[j] as ByteArray).contentEquals(r[j] as ByteArray)) {
                                            equal = false
                                        }
                                    } else {
                                        if (result[j]!! != r[j]) {
                                            equal = false
                                        }
                                    }
                                }
                            } else {
                                equal = false
                            }
                        }
                        if (!equal) {
                            results.add(result)
                        }
                    }
                }
            } catch (e: Exception) {
                failures += 1
                exceptions.add(e)
                if (callType == MulticallQuery.Companion.CallType.FIRST) {
                    break
                }
            }
        }
        log.info("multicall query results: ${successRate * 100}% with ${results.size} unique items")
        log.debug(toString())
        return status()
    }

    override fun getResult(): List<T>? {
        return when(callType) {
            MulticallQuery.Companion.CallType.UNTIL_FOUND -> results.first()
            MulticallQuery.Companion.CallType.UNANIMOUS -> {
                if (results.isEmpty()) {
                    listOf()
                } else {
                    results.first()
                }
            }
            MulticallQuery.Companion.CallType.FIRST -> results.first()
            MulticallQuery.Companion.CallType.MAJORITY -> {
                if (foundSuccess()) {
                    results.first()
                } else if (notFoundSuccess()) {
                    listOf()
                } else {
                    throw exception()
                }
            }
            MulticallQuery.Companion.CallType.MAJORITY_FOUND -> {
                if (foundSuccess()) {
                    results.first()
                } else {
                    listOf()
                }
            }
        }
    }

}