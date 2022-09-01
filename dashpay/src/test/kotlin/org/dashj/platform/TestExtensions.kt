package org.dashj.platform.dashpay

import org.dashj.platform.dpp.deepCompare
import org.opentest4j.AssertionFailedError

fun assertMapEquals(expected: Map<String, Any?>, actual: Map<String, Any?>) {
    val result = expected.deepCompare(actual)
    if (!result) {
        throw AssertionFailedError(null, expected, actual)
    }
}

fun assertMapEquals(expected: Map<String, Any?>, actual: Map<String, Any?>, message: String) {
    val result = expected.deepCompare(actual)
    if (!result) {
        throw AssertionFailedError(message, expected, actual)
    }
}

fun assertMapNotEquals(expected: Map<String, Any?>, actual: Map<String, Any?>) {
    val result = expected.deepCompare(actual)
    if (result) {
        throw AssertionFailedError(null, expected, actual)
    }
}

fun assertListEquals(expected: List<Any>, actual: List<Any>) {
    val result = expected.deepCompare(actual)
    if (!result) {
        throw AssertionFailedError(null, expected, actual)
    }
}
