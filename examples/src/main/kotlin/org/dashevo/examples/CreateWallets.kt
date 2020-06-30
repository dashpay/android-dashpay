/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.bitcoinj.core.Address
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.utils.BriefLogFormatter
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChain
import org.dashevo.Client
import java.security.SecureRandom
import java.util.*

class CreateWallets {
    companion object {
        val sdk = Client("mobile")

        @JvmStatic
        fun main(args: Array<String>) {
            BriefLogFormatter.initWithSilentBitcoinJ()
            createWallets()
        }

        fun createWallets() {
            val platform = sdk.platform
            sdk.isReady()

            val secureRandom = SecureRandom()
            val addresses = arrayListOf<Address>()
            val total = 20

            println("Wallet, Recovery Phrase, First Address")
            for (i in 0 until total) {
                var entropy = ByteArray(16)
                secureRandom.nextBytes(entropy)
                entropy = entropy.copyOfRange(0, DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS / 8)
                val chain = DeterministicKeyChain.builder().entropy(entropy, Date().time/1000)
                    .accountPath(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH_TESTNET).build()
                val receiveKey = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS)
                val address = Address.fromKey(TestNet3Params.get(), receiveKey)
                addresses.add(address)
                println(
                    "${i+1}, ${chain.seed!!.mnemonicCode!!.joinToString(" ")}, $address"
                )
            }

            val sendCommand = StringBuilder("sendmany \"Faucet\" \"{")
            for (i in 0 until total) {
                sendCommand.append("\\\"${addresses[i]}\\\": 0.05")
                if(i < (total - 1))
                    sendCommand.append(", ")
            }
            sendCommand.append("}\"")
            println(sendCommand)
        }
    }
}