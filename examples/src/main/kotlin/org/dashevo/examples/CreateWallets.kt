/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionOutput
import org.bitcoinj.evolution.CreditFundingTransaction
import org.bitcoinj.params.MobileDevNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.utils.BriefLogFormatter
import org.bitcoinj.wallet.AuthenticationKeyChain
import org.bitcoinj.wallet.CoinSelection
import org.bitcoinj.wallet.CoinSelector
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChain
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.dashevo.Client
import org.dashevo.dashpay.BlockchainIdentity
import org.dashevo.dpp.identity.Identity
import org.dashevo.dpp.util.HashUtils
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit
import org.bitcoinj.wallet.WalletTransaction
import org.dashevo.dashpay.RetryDelayType
import org.dashevo.dashpay.callback.RegisterIdentityCallback
import org.dashevo.dashpay.callback.RegisterNameCallback
import org.dashevo.dashpay.callback.RegisterPreorderCallback
import org.dashevo.dpp.toHexString


fun String.runCommand(workingDir: File): String? {
    try {
        val parts = this.split("\\s".toRegex())
        val proc = ProcessBuilder(*parts.toTypedArray())
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        proc.waitFor(60, TimeUnit.MINUTES)
        return proc.inputStream.bufferedReader().readText()
    } catch(e: IOException) {
        e.printStackTrace()
        return null
    }
}

class CreateWallets {
    companion object {
        val sdk = Client("mobile")
        private val PARAMS = MobileDevNetParams.get()
        var configurationFile: String = ""

        /**
         * The first argument must be the location of the configuration file that must include
         * rpcuser, rpcuserpassword and server=1
         */
        @JvmStatic
        fun main(args: Array<String>) {
            BriefLogFormatter.initWithSilentBitcoinJ()
            if (args.isNotEmpty()) {
                configurationFile = args[0]
                createWallets()
            } else {
                println("CreateWallets.kt\nThe first argument must be the path to the .conf file for the devnet\ndash-cli must be in your path")
            }
        }

        fun runRpc(command: String): String? {
            val command = "dash-cli.exe -conf=$configurationFile $command"
            return command.runCommand(File("."))
        }

        fun createWallets() {
            val scanner = Scanner(System.`in`)
            val platform = sdk.platform
            sdk.isReady()

            val secureRandom = SecureRandom()
            val addresses = arrayListOf<Address>()
            val recoveryPhrases = arrayListOf<String>()
            val namesMap = HashMap<String, String>()
            var namesToCreate = arrayListOf<String>()

            println("Enter the usernames to create (one name per line, `quit` to end the list) ")
            var input = scanner.next()
            while (input != "quit") {
                if (input.length >= 3) {
                    namesToCreate.add(input)
                }
                input = scanner.next()
                if (input == "quit")
                    break
            }
            val total = namesToCreate.size
            println("Wallet, Recovery Phrase, First Address")
            for (i in 0 until total) {
                var entropy = ByteArray(16)
                secureRandom.nextBytes(entropy)
                entropy = entropy.copyOfRange(0, DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS / 8)
                val chain = DeterministicKeyChain.builder().entropy(entropy, Date().time/1000)
                    .accountPath(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH_TESTNET).build()
                val receiveKey = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS)
                val address = Address.fromKey(PARAMS, receiveKey)
                addresses.add(address)

                val recoveryPhrase = chain.seed!!.mnemonicCode!!.joinToString(" ")
                recoveryPhrases.add(recoveryPhrase)

                println(
                    "processing ${i+1} ${namesToCreate[i]}, $recoveryPhrase, $address"
                )

                // fund the wallet
                val txid = runRpc("sendtoaddress $address 0.05")!!.trim()
                println("Wallet funding transction: $txid")

                // get tx information
                val txBytes = runRpc("getrawtransaction $txid")!!.trim()
                println(txBytes)
                val walletTx = Transaction(PARAMS, HashUtils.fromHex(txBytes))

                //create the wallet
                val keyChainGroup = KeyChainGroup.builder(PARAMS).addChain(chain).build()
                val wallet = Wallet(PARAMS, keyChainGroup)
                wallet.initializeAuthenticationKeyChains(wallet.keyChainSeed, null)
                wallet.addWalletTransaction(WalletTransaction(WalletTransaction.Pool.UNSPENT, walletTx))

                // send the credit funding transaction
                val sendRequest = SendRequest.creditFundingTransaction(PARAMS, wallet.currentAuthenticationKey(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING), Coin.valueOf(1000000))
                sendRequest.coinSelector = CoinSelector { target, candidates ->
                    val selected = ArrayList<TransactionOutput>()
                    val sortedOutputs = ArrayList(candidates)
                    var total: Long = 0
                    for (output in sortedOutputs) {
                        if (total >= target.value) break
                        selected.add(output)
                        total += output.value.value
                    }
                    CoinSelection(Coin.valueOf(total), selected)
                }

                val cftx = wallet.sendCoinsOffline(sendRequest) as CreditFundingTransaction
                val cftxid = runRpc("sendrawtransaction ${cftx.bitcoinSerialize().toHexString()}")
                println("credit funding tx: ${cftx.txId}")
                println(cftxid)

                val blockchainIdentity = BlockchainIdentity(platform, Identity.IdentityType.USER, cftx, wallet)

                blockchainIdentity.registerIdentity(null)
                Thread.sleep(5000)
                blockchainIdentity.watchIdentity(10, 5000, RetryDelayType.SLOW20, object: RegisterIdentityCallback {
                    override fun onComplete(uniqueId: String) {
                        blockchainIdentity.addUsername(namesToCreate[i])

                        val names = blockchainIdentity.getUnregisteredUsernames()
                        blockchainIdentity.registerPreorderedSaltedDomainHashesForUsernames(names, null)
                        val set = blockchainIdentity.getUsernamesWithStatus(BlockchainIdentity.UsernameStatus.PREORDER_REGISTRATION_PENDING)
                        val saltedDomainHashes = blockchainIdentity.saltedDomainHashesForUsernames(set)
                        blockchainIdentity.watchPreorder(saltedDomainHashes, 10, 5000, RetryDelayType.SLOW20, object: RegisterPreorderCallback {
                            override fun onComplete(names: List<String>) {
                                val preorderedNames = blockchainIdentity.preorderedUsernames()
                                blockchainIdentity.registerUsernameDomainsForUsernames(preorderedNames, null)
                                blockchainIdentity.watchUsernames(preorderedNames, 10, 5000, RetryDelayType.SLOW20, object: RegisterNameCallback {
                                    override fun onComplete(names: List<String>) {
                                        println("name registration successful $names")
                                        namesMap[names[0]] = recoveryPhrase
                                    }

                                    override fun onTimeout(incompleteNames: List<String>) {
                                        println("name registration failed $incompleteNames")
                                    }
                                })
                            }

                            override fun onTimeout(incompleteNames: List<String>) {
                                println("preorder registration failed $incompleteNames")
                            }
                        })
                    }

                    override fun onTimeout() {
                        println("Identity registration failed")
                    }
                })
            }

            // print usernames and associated recovery phrases
            println("\nUsername, Recovery Phrase")
            for (name in namesMap) {
                println("${name.key}, ${name.value}")
            }
        }
    }
}