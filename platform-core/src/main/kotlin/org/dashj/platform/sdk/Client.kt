/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.sdk

import java.util.EnumSet
import org.bitcoinj.params.BinTangDevNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.wallet.AuthenticationKeyChain
import org.bitcoinj.wallet.DerivationPathFactory
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.authentication.AuthenticationGroupExtension
import org.dashj.platform.dapiclient.DapiClient
import org.dashj.platform.sdk.client.ClientApps
import org.dashj.platform.sdk.client.ClientOptions
import org.dashj.platform.sdk.platform.Platform

class Client(private val clientOptions: ClientOptions) {
    val params = when (clientOptions.network) {
        "testnet" -> TestNet3Params.get()
        "bintang" -> BinTangDevNetParams.get()
        else -> throw IllegalArgumentException("network ${clientOptions.network} is not valid")
    }
    val platform = Platform(params)

    val dapiClient: DapiClient
        get() = platform.client

    val apps: ClientApps
        get() = ClientApps(platform.apps)

    var wallet: Wallet? = null
    val authenticationExtension = AuthenticationGroupExtension(params)

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
                authenticationExtension.addKeyChains(
                    params,
                    keyChainSeed,
                    EnumSet.of(
                        AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING,
                        AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_TOPUP,
                        AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY,
                        AuthenticationKeyChain.KeyChainType.INVITATION_FUNDING
                    )
                )
                addExtension(authenticationExtension)
            }
        }

        // Create the DapiClient with parameters
        platform.client = when {
            clientOptions.dapiAddressListProvider != null -> {
                DapiClient(clientOptions.dapiAddressListProvider, platform.dpp, clientOptions.timeout, clientOptions.retries, clientOptions.banBaseTime)
            }
            clientOptions.dapiAddresses.isNotEmpty() -> {
                DapiClient(clientOptions.dapiAddresses, platform.dpp, clientOptions.timeout, clientOptions.retries, clientOptions.banBaseTime)
            }
            else -> {
                DapiClient(params.defaultHPMasternodeList.toList(), platform.dpp, clientOptions.timeout, clientOptions.retries, clientOptions.banBaseTime)
            }
        }

        // Client Apps
        platform.apps.putAll(clientOptions.apps)
    }
}
