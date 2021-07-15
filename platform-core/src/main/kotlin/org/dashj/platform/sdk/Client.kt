/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.sdk

import org.bitcoinj.params.EvoNetParams
import org.bitcoinj.params.MobileDevNetParams
import org.bitcoinj.params.PalinkaDevNetParams
import org.bitcoinj.params.SchnappsDevNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.wallet.DerivationPathFactory
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.Wallet
import org.dashj.platform.dapiclient.DapiClient
import org.dashj.platform.sdk.client.ClientApps
import org.dashj.platform.sdk.client.ClientOptions
import org.dashj.platform.sdk.platform.Platform

class Client(private val clientOptions: ClientOptions) {
    val params = when (clientOptions.network) {
        "evonet" -> EvoNetParams.get()
        "palinka" -> PalinkaDevNetParams.get()
        "mobile" -> MobileDevNetParams.get()
        "testnet" -> TestNet3Params.get()
        "schnapps" -> SchnappsDevNetParams.get()
        else -> throw IllegalArgumentException("network ${clientOptions.network} is not valid")
    }
    val platform = Platform(params)

    val dapiClient: DapiClient
        get() = platform.client

    val apps: ClientApps
        get() = ClientApps(platform.apps)

    var wallet: Wallet? = null

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

            if (seed != null) {
                chainBuilder.seed(seed)
            }
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
        platform.client = when {
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

        // Client Apps
        platform.apps.putAll(clientOptions.apps)
    }
}
