/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.sdk.client

import org.dashj.platform.dapiclient.DapiClient
import org.dashj.platform.dapiclient.provider.DAPIAddressListProvider

class ClientOptions(
    val apps: Map<String, ClientAppDefinition> = mapOf(),
    val walletOptions: WalletOptions? = null,
    val walletAccountIndex: Int = 0,
    val network: String = "testnet",
    val seeds: List<String> = listOf(),
    val dapiAddressListProvider: DAPIAddressListProvider? = null,
    val dapiAddresses: List<String> = listOf(),
    val timeout: Long = DapiClient.DEFAULT_TIMEOUT,
    val retries: Int = DapiClient.DEFAULT_RETRY_COUNT,
    val banBaseTime: Int = DapiClient.DEFAULT_BASE_BAN_TIME
) {
    constructor(network: String) : this(mapOf(), null, 0, network = network)

    data class Builder(
        private val apps: HashMap<String, ClientAppDefinition> = hashMapOf(),
        private var walletOptions: WalletOptions? = null,
        private var walletAccountIndex: Int = 0,
        private var network: String = "testnet",
        private val seeds: ArrayList<String> = arrayListOf(),
        private var dapiAddressListProvider: DAPIAddressListProvider? = null,
        private val dapiAddresses: ArrayList<String> = arrayListOf(),
        private var timeout: Long = DapiClient.DEFAULT_TIMEOUT,
        private var retries: Int = DapiClient.DEFAULT_RETRY_COUNT,
        private var banBaseTime: Int = DapiClient.DEFAULT_BASE_BAN_TIME
    ) {
        fun app(name: String, definition: ClientAppDefinition) = apply {
            apps[name] = definition
        }
        fun app(name: String, contractId: String) = apply {
            apps[name] = ClientAppDefinition(contractId)
        }
        fun apps(apps: Map<String, ClientAppDefinition>) = apply {
            this.apps.putAll(apps)
        }
        fun walletOptions(walletOptions: WalletOptions) = apply {
            this.walletOptions = walletOptions
        }
        fun walletAccountIndex(walletAccountIndex: Int) = apply {
            this.walletAccountIndex = walletAccountIndex
        }
        fun network(network: String) = apply {
            this.network = network
        }
        fun seed(seed: String) = apply {
            this.seeds.add(seed)
        }
        fun seeds(seeds: List<String>) = apply {
            this.seeds.addAll(seeds)
        }
        fun dapiAddressListProvider(dapiAddressListProvider: DAPIAddressListProvider) = apply {
            this.dapiAddressListProvider = dapiAddressListProvider
        }
        fun dapiAddress(dapiAddress: String) = apply {
            this.dapiAddresses.add(dapiAddress)
        }
        fun dapiAddress(dapiAddresses: List<String>) = apply {
            this.dapiAddresses.addAll(dapiAddresses)
        }
        fun timeout(timeout: Long) = apply {
            this.timeout = timeout
        }
        fun retries(retries: Int) = apply {
            this.retries = retries
        }
        fun baseBanTime(banBaseTime: Int) = apply {
            this.banBaseTime = banBaseTime
        }
        fun build(): ClientOptions {
            return ClientOptions(apps, walletOptions, walletAccountIndex, network, seeds, dapiAddressListProvider, dapiAddresses, timeout, retries, banBaseTime)
        }
    }
    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }
}
