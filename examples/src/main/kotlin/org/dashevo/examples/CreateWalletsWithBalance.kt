/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.bitcoinj.core.Address
import org.bitcoinj.params.EvoNetParams
import org.bitcoinj.utils.BriefLogFormatter
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChain
import org.dashevo.Client
import org.dashevo.client.ClientOptions
import java.io.File
import java.security.SecureRandom
import java.util.*


class CreateWalletsWithBalance {
    companion object {
        lateinit var sdk: Client
        private val PARAMS = EvoNetParams.get()
        var configurationFile: String = ""
        var network: String = ""
        var balance: Double = 1.0

        /**
         * The first argument must be the location of the configuration file that must include
         * rpcuser, rpcuserpassword and server=1
         */
        @JvmStatic
        fun main(args: Array<String>) {
            BriefLogFormatter.initWithSilentBitcoinJ()
            if (args.size >= 2) {
                network = args[0]
                configurationFile = args[1]
                balance = args[2].toDouble()
                sdk = Client(ClientOptions(network = args[0]))
                println("------------------------------------------------")
                println("CreateWallets($network: $configurationFile)")
                println()
                createWallets()
            } else {
                println("CreateWalletsWithBalance.kt")
                println("The first argument is the network and the second must be the path to the .conf file for the devnet\ndash-cli must be in your path")
                println("The third argument is the balance with which to load each wallet")
            }
        }

        private fun runRpc(command: String): String? {
            val command = "dash-cli -conf=$configurationFile $command"
            return command.runCommand(File("."))
        }

        private fun createWallets() {
            val scanner = Scanner(System.`in`)

            val secureRandom = SecureRandom()
            val recoveryPhraseMap = HashMap<String, String>()

            println("Enter the number of wallets to create:")
            val total = scanner.nextInt()

            println("Wallet, Recovery Phrase, First Address")
            for (i in 0 until total) {
                var entropy = ByteArray(16)
                secureRandom.nextBytes(entropy)
                entropy = entropy.copyOfRange(0, DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS / 8)
                val chain = DeterministicKeyChain.builder().entropy(entropy, Date().time / 1000)
                    .accountPath(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH_TESTNET).build()
                val receiveKey = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS)
                val address = Address.fromKey(PARAMS, receiveKey)
                val recoveryPhrase = chain.seed!!.mnemonicCode!!.joinToString(" ")
                recoveryPhraseMap[recoveryPhrase] = address.toBase58()

                println(
                    "processing ${i + 1} $recoveryPhrase, $address"
                )

                // fund the wallet
                val txid = runRpc("sendtoaddress $address $balance")!!.trim()
                if (txid.isEmpty()) {
                    println("sendtoaddress failed: Is the palinka daemon running?")
                    return
                } else {
                    println("Wallet funding transaction: $txid")

                }

                // get tx information
                val txBytes = runRpc("getrawtransaction $txid")!!.trim()
                println(txBytes)
            }


            // print usernames and associated recovery phrases
            println("\nRecovery Phrase, First Address")
            for (phrase in recoveryPhraseMap) {
                println("${phrase.key}, ${phrase.value}")
            }

        }
    }
}