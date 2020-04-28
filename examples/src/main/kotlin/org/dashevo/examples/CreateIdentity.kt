/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.examples

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Utils
import org.bitcoinj.evolution.CreditFundingTransaction
import org.dashevo.Client
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identity.Identity
import org.json.JSONObject
import java.lang.Thread.sleep

/*

    DeterministicKey{
        pub HEX=0249f14d4997b555bd3d602a0410c0de78e8df60cf13952ea202ff725080e7c06c,
        priv HEX=b0f11761b975ff53d9c4345da1b436dcbad7b689aa6af8526753f920b3f6f106,
        priv WIF=cTWeqyVmbZz8CnNcShQwLyHFLr1Fr9UcKUR3sy5Sz5ApLcJon4wB,
        isEncrypted=false, isPubKeyOnly=false
        }
        addr:yguaDwEnR8aJHW7PX8EbQvbKcYieqWxjnG  hash160:e1d6d0811d500e6ad194121ac4d927a04aef7227  (M/9H/1H/12H/0, external)

    b12b1064de1a8310c394528cbd26a4ef78876ef9d76669a40d77d78dda28e810
      updated: 2020-04-06T16:12:25Z
      type TRANSACTION_NORMAL(0)
      purpose: USER_PAYMENT
         in   PUSHDATA(72)[3045022100e63f680c9341b89ab0bf2314dc1961b21e38a9f2b40a177b5e0e4dbb3b27f09402203c10c3ee1e894182f504effe835abcaea5ea61e7d091d65af3d88b711d23c7dc01] PUSHDATA(33)[03bd7faa0249fd3b5ed9a1060afd61cfd6a23f230a2fc97d2f8bdd7064c87f716d]  0.666 DASH
              P2PKH addr:yX6aNt3d5MTZ1MhxQwstr6hnefk6PLp8ea  outpoint:0b65d99c02288d86932684241f2636b318735269f697ecd3096cf13af996c445:0
         out  RETURN PUSHDATA(20)[e1d6d0811d500e6ad194121ac4d927a04aef7227]  0.0004 DASH
              CREDITBURN addr:yguaDwEnR8aJHW7PX8EbQvbKcYieqWxjnG
         out  DUP HASH160 PUSHDATA(20)[ecbf4da7dfc4142e845a8e9cb3ddb625cb2bd4f0] EQUALVERIFY CHECKSIG  0.66559776 DASH
              P2PKH addr:yhuFVTtDdVaSVwV4RQ2n9ZFJUQ2tekv2r8  spent by:c1deb627524affe1169fa965ba2ff1ed47e70074ed71e96319a153885d84296f:0
         fee  0.00001004 DASH/kB, 0.00000224 DASH for 223 bytes
    0.666 DASH total value (sends 0.00 DASH and receives 0.666 DASH)
      confidence: Appeared in best chain at height 2464, depth 14183.
      InstantSendLock: Unknown status
     Source:
 */

class CreateIdentity {
    companion object {
        val sdk = Client("mobile")

        const val publicKeyHex = "0249f14d4997b555bd3d602a0410c0de78e8df60cf13952ea202ff725080e7c06c"
        const val privateKeyHex = "b0f11761b975ff53d9c4345da1b436dcbad7b689aa6af8526753f920b3f6f106"
        val publicKey = Utils.HEX.decode(publicKeyHex)
        val privateKey = Utils.HEX.decode(privateKeyHex)
        val creditBurnTx =
            Utils.HEX.decode("010000000145c496f93af16c09d3ec97f669527318b336261f24842693868d28029cd9650b000000006b483045022100e63f680c9341b89ab0bf2314dc1961b21e38a9f2b40a177b5e0e4dbb3b27f09402203c10c3ee1e894182f504effe835abcaea5ea61e7d091d65af3d88b711d23c7dc012103bd7faa0249fd3b5ed9a1060afd61cfd6a23f230a2fc97d2f8bdd7064c87f716dffffffff02409c000000000000166a14e1d6d0811d500e6ad194121ac4d927a04aef7227209ff703000000001976a914ecbf4da7dfc4142e845a8e9cb3ddb625cb2bd4f088ac00000000")
        val creditBurnTxId = Sha256Hash.wrap("b12b1064de1a8310c394528cbd26a4ef78876ef9d76669a40d77d78dda28e810")
        val creditBurnTxOutput = 0

        @JvmStatic
        fun main(args: Array<String>) {
            createIdentity()
        }

        fun createIdentity() {
            val platform = sdk.platform
            sdk.isReady();

            val cftx = CreditFundingTransaction(platform.params, creditBurnTx)
            cftx.setCreditBurnPublicKeyAndIndex(ECKey.fromPrivateAndPrecalculatedPublic(privateKey, publicKey), 0)

            try {
                var identity = platform.identities.get(cftx.creditBurnIdentityIdentifier.toStringBase58())

                if (identity == null) {
                    // only create the identity if it does not exist
                    platform.identities.register(Identity.IdentityType.USER, cftx)
                    sleep(10000)
                    identity = platform.identities.get(cftx.creditBurnIdentityIdentifier.toString())
                }

                // display information
                println("Identity Created: ${cftx.creditBurnIdentityIdentifier.toStringBase58()}")
                println(JSONObject(identity!!.toJSON()).toString(2))
                println("Private Key: $privateKeyHex (hex)")

            } catch (e: Exception) {
                println(e.localizedMessage)
            }
        }
    }
}