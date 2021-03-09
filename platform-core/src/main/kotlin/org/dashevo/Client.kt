/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo

import org.bitcoinj.params.EvoNetParams
import org.bitcoinj.params.MobileDevNetParams
import org.bitcoinj.params.PalinkaDevNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.wallet.*
import org.dashevo.client.ClientAppDefinition
import org.dashevo.client.ClientApps
import org.dashevo.client.ClientOptions
import org.dashevo.dapiclient.DapiClient
import org.dashevo.platform.Platform

class Client(private val clientOptions: ClientOptions) {
    val params = when (clientOptions.network) {
        "evonet" -> EvoNetParams.get()
        "palinka" -> PalinkaDevNetParams.get()
        "mobile" -> MobileDevNetParams.get()
        "testnet" -> TestNet3Params.get()
        else -> throw IllegalArgumentException("network ${clientOptions.network} is not valid")
    }
    val apps: ClientApps
    var dapiClient: DapiClient
    val platform = Platform(params)

    var wallet : Wallet? = null

    init {
        val needWallet = clientOptions.walletOptions != null
        val seed = if (clientOptions.walletOptions != null) {
            if (clientOptions.walletOptions.mnemonic != null) {
                DeterministicSeed(clientOptions.walletOptions.mnemonic.split(' '), null, "", clientOptions.walletOptions.creationTime)
            } else {
                null
            }
        } else {
            null
        }
        if (needWallet) {
            val chainBuilder = DeterministicKeyChain.builder()
                .accountPath(DerivationPathFactory.get(platform.params).bip44DerivationPath(clientOptions.walletAccountIndex))

            if (seed != null)
                chainBuilder.seed(seed)

            wallet = Wallet(
                platform.params,
                KeyChainGroup.builder(platform.params)
                    .addChain(chainBuilder.build())
                    .build()
            ).apply {
                initializeAuthenticationKeyChains(keyChainSeed, null)
            }
        }

        // Create the DapiClient with parameters
        dapiClient = when {
            clientOptions.dapiAddressListProvider != null -> {
                DapiClient(clientOptions.dapiAddressListProvider, clientOptions.timeout, clientOptions.retries, clientOptions.banBaseTime)
            }
            clientOptions.dapiAddresses.isNotEmpty() -> {
                DapiClient(clientOptions.dapiAddresses, clientOptions.timeout, clientOptions.retries, clientOptions.banBaseTime)
            }
            else -> {
                DapiClient(params.defaultMasternodeList.toList(), clientOptions.timeout, clientOptions.retries, clientOptions.banBaseTime)
            }
        }
        platform.client = dapiClient

        // Client Apps
        val defaultApps = hashMapOf<String, ClientAppDefinition>()
        when {
            params.id.contains("test") -> {
                defaultApps["dpns"] = ClientAppDefinition("36ez8VqoDbR8NkdXwFaf9Tp8ukBdQxN8eYs8JNMnUyKz")
                // matk8g1YRpzZskecRfpG5GCAgRmWCGJfjUemrsLkFDg - contract with coreHeightCreatedAt required field
                defaultApps["dashpay"] = ClientAppDefinition("2DAncD4YTjfhSQZYrsQ659xbM7M5dNEkyfBEAg9SsS3W")
            }
            params.id.contains("evonet") -> {
                defaultApps["dpns"] = ClientAppDefinition("3VvS19qomuGSbEYWbTsRzeuRgawU3yK4fPMzLrbV62u8")
                defaultApps["dashpay"] = ClientAppDefinition("5kML7KqerxF2wU7acywVhpVRHtJGrNGh9swcmqNmFg2s")
            }
            params.id.contains("mobile") -> {
                defaultApps["dpns"] = ClientAppDefinition("CVZzFCbz4Rcf2Lmu9mvtC1CmvPukHy5kS2LNtNaBFM2N")
                defaultApps["dashpay"] = ClientAppDefinition("FqAxLy1KMzGTb2Xj4A2tpxw89srN5VK8D6yuMnkzzk5P")
            }
            params.id.contains("palinka") -> {
                defaultApps["dpns"] = ClientAppDefinition("FZ2MkyR8YigXX7K7m9sq3PikzubV8i4rwUMheAQTLLCw")
                defaultApps["dashpay"] = ClientAppDefinition("GmCL5grcMBHumKVXvWpRZU4BaGzGC7p6mbsJSR4K6yhd")
                //apps["thumbnail"] = ContractInfo("3GV8H5ha68pchFyJF46dzdpfgPDhSr6iLht3EcYgqFKw")
            }
        }
        apps = ClientApps(defaultApps)
        apps.addAll(clientOptions.apps)
    }
}