/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashj.platform.sdk.client

class ClientApps(apps: Map<String, ClientAppDefinition>) {
    private val apps = hashMapOf<String, ClientAppDefinition>()

    val names: List<String>
        get() = apps.keys.toList()

    init {
        this.apps.putAll(apps)
    }

    fun addAll(apps: Map<String, ClientAppDefinition>) {
        this.apps.putAll(apps)
    }

    fun set(name: String, definition: ClientAppDefinition) {
        apps[name] = definition
    }

    fun get(name: String): ClientAppDefinition {
        if (!apps.containsKey(name)) {
            throw Exception("Application with name $name is not defined")
        }
        return this.apps[name]!!
    }

    fun has(name: String): Boolean {
        return apps.containsKey(name)
    }

    override fun toString(): String {
        return "ClientApps($apps)"
    }
}
