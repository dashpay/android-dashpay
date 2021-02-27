/**
 * Copyright (c) 2021-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform.multicall

import org.slf4j.LoggerFactory

/**
 *
 * @param T
 * @property method MulticallMethod<T>
 * @property callsToMake Int
 * @property requiredSuccessRate Double
 * @property calls Int
 * @property results HashSet<T?>
 * @property successRate Double
 * @constructor
 */
open class MulticallQuery<T> (val method: MulticallMethod<T>,
                              val callType: CallType,
                              val callsToMake: Int = 3,
                              val requiredSuccessRate:Double = 0.51) {

    companion object {
        private val log = LoggerFactory.getLogger(MulticallQuery::class.java)

        enum class Status {
            FOUND,
            NOT_FOUND,
            ERRORS,
            AGREE,
            DISAGREE
        }

        enum class CallType {
            MAJORITY_FOUND,
            FIRST,
            UNTIL_FOUND,
            MAJORITY, // default
            UNANIMOUS
        }
    }

    protected var calls: Int = 0
    protected var failures: Int = 0
    protected val exceptions = arrayListOf<Exception>()
    protected var notFound: Int = 0
    protected var results: HashSet<T?> = hashSetOf()

    val foundRate: Double
        get() = (calls - notFound).toDouble() / calls.toDouble()
    val notFoundRate: Double
        get() = (notFound).toDouble() / calls.toDouble()
    val successRate: Double
        get() = (calls - failures).toDouble() / calls.toDouble()

    /**
     * Returns true if some results were found or not found, but false if all queries returned errors
     * @return Boolean
     */
    fun success() : Boolean {
        if (failures == calls)
            return false
        return true
    }

    fun exception() : MulticallException {
        return MulticallException(exceptions)
    }

    fun status() : Status {
        return when {
            failures == calls -> Status.ERRORS
            callType == CallType.UNANIMOUS -> {
                if (failures == callsToMake || notFound == callsToMake || (notFound == 0 && failures == 0 && results.size == 1)) {
                    Status.AGREE
                } else {
                    Status.DISAGREE
                }
            }
            foundSuccess() -> Status.FOUND
            else -> Status.NOT_FOUND
        }
    }

    /**
     * Returns true if some results were found, from more than requiredSuccessRate% nodes
     * @return Boolean
     */
    fun foundSuccess() : Boolean {
        return foundRate > requiredSuccessRate && results.size == 1
    }

    /**
     * Returns true if no results were found
     * @return Boolean
     */
    fun notFoundSuccess() : Boolean {
        return notFound == calls
    }

    open fun query(): Status {
        for (i in 0 until callsToMake) {
            log.info("making query ${i + 1} of $callsToMake")
            calls = i
            try {
                val result = method.execute()

                if (result == null) {
                    notFound++
                    if (callType == CallType.FIRST) {
                        calls = 1
                        notFound = 1
                        failures = 0
                        return Status.NOT_FOUND
                    }
                } else {
                    results.add(result)
                    when(callType) {
                        CallType.FIRST -> {
                            calls = 1
                            notFound = 0
                            failures = 0
                            return Status.FOUND
                        }
                        CallType.UNTIL_FOUND -> {
                            return Status.FOUND
                        }
                    }
                }
            } catch (e: Exception) {
                failures += 1
                exceptions.add(e)
            }
        }
        log.info("multicall query results: ${successRate * 100}% with ${results.size} unique items")
        return status()
    }

    fun queryFirstResult() : Boolean {
        return query() == Status.FOUND
    }

    fun queryFound() : Boolean {
        query()
        return foundSuccess()
    }

    fun queryNotFound() : Boolean {
        query()
        return notFoundSuccess()
    }

    open fun getResult(): T? {
        return when(callType) {
            CallType.UNTIL_FOUND -> results.first()
            CallType.UNANIMOUS -> {
                if (results.isEmpty()) {
                    null
                } else {
                    results.first()
                }
            }
            CallType.FIRST -> results.first()
            CallType.MAJORITY -> {
                if (foundSuccess()) {
                    results.first()
                } else if (notFoundSuccess()) {
                    null
                } else {
                    throw exception()
                }
            }
            CallType.MAJORITY_FOUND -> {
                if (foundSuccess()) {
                    results.first()
                } else {
                    null
                }
            }
        }
    }

    override fun toString(): String {
        return "MulticallQuery(type=$callType, calls=$calls, found unique=${results.size}, notFound=$notFound, errors=$failures)" +
                "--> $results; exceptions=$exceptions"
    }
}
