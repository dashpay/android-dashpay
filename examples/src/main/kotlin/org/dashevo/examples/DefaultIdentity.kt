package org.dashevo.examples

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Utils

/*

    DeterministicKey{
        pub HEX=02af0cfc16fa8778ce6fd64694bfb9f9853cb2e51217fe77af487d1c1a44d50da7,
        priv HEX=7c8922c88449e483fe95901216d8116e3c2098e7cff8fa2cc75eaa8031a7a405,
        priv WIF=cRknMQ5W2bPaFo4X9PYAb7HzhJthukunWBy6MDzGmWbhBeCVKAC3,
        isEncrypted=false,
        isPubKeyOnly=false
    }
    addr:yasppWBy7doW4amAGmNsr9JkZviqFNXRSZ
    hash160:9fb169770f24e7e1173f6b194a96feed17665eae  (M/9H/1H/12H/1, internal)

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

object DefaultIdentity {
    const val publicKeyHex = "02af0cfc16fa8778ce6fd64694bfb9f9853cb2e51217fe77af487d1c1a44d50da7"
    const val privateKeyHex = "7c8922c88449e483fe95901216d8116e3c2098e7cff8fa2cc75eaa8031a7a405"
    val publicKey = Utils.HEX.decode(publicKeyHex)
    val privateKey = Utils.HEX.decode(privateKeyHex)
    val creditBurnTx =
        Utils.HEX.decode("010000000145c496f93af16c09d3ec97f669527318b336261f24842693868d28029cd9650b000000006b483045022100e63f680c9341b89ab0bf2314dc1961b21e38a9f2b40a177b5e0e4dbb3b27f09402203c10c3ee1e894182f504effe835abcaea5ea61e7d091d65af3d88b711d23c7dc012103bd7faa0249fd3b5ed9a1060afd61cfd6a23f230a2fc97d2f8bdd7064c87f716dffffffff02409c000000000000166a14e1d6d0811d500e6ad194121ac4d927a04aef7227209ff703000000001976a914ecbf4da7dfc4142e845a8e9cb3ddb625cb2bd4f088ac00000000")
    val creditBurnTxId = Sha256Hash.wrap("b12b1064de1a8310c394528cbd26a4ef78876ef9d76669a40d77d78dda28e810")
    val creditBurnTxOutput = 0

    val identityPrivateKey = ECKey.fromPrivateAndPrecalculatedPublic(privateKey, publicKey)

    val seed = "regular soup scene torch enrich fitness carbon praise rebuild penalty hole citizen"
}