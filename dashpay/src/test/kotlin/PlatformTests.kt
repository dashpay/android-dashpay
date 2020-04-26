/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
import com.google.common.io.BaseEncoding
import io.grpc.StatusRuntimeException
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Utils
import org.bitcoinj.params.EvoNetParams
import org.dashevo.platform.Platform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlatformTests {

    /*
CreditFundingTransaction{78b7f924ce99982d7bc95160984e38a2b17e88ba98b5583d4320116565c2763d
  type TRANSACTION_NORMAL(0)
purpose: UNKNOWN
   in   PUSHDATA(72)[3045022100fc7cab994fb62bce2e286124d696cdd09120ac8ae94e4598977f1a27a582f747022074da17c595b531ce81b70d4116425a6df9b2a71f958f399dcabab9f205b2ae9e01] PUSHDATA(33)[0326e680733eefbf271cd20fddf40e75a89923b1cf39a6162baf770de040efb718]
        unconnected  outpoint:4953fde90ca2b118755d144ed1fa86cef5a93106a1efd317c895f37506935e74:0
   out  DUP HASH160 PUSHDATA(20)[7b560e12927197cfc4267f752280910a09db8fdb] EQUALVERIFY CHECKSIG  0.19959776 DASH
        P2PKH addr:yXZb416KVDkaMG8AuqpPRnGkftPWyyJR4b
   out  RETURN PUSHDATA(20)[6d22ab738e8b321738b382e1a10f4d0c50c905e9]  0.0004 DASH
        CREDITBURN addr:yWGW31QSArqjncjmn74AFaay7SCrP1oYUa
}

  DeterministicKey{pub HEX=027874912e5d8e99cb129d4d0dc7c8285343c181c4c8272f89752c65b68ceb2c94,
    priv HEX=0c82adc6e085cfc27c6620b1fa8eb8ea630bccaf47b459fb128f7c1827994e8d,
    priv WIF=cN128hmiUpcpEX5AceYMyu9LDsnsqhqSoSufVU1HRBha1jebwhBx, isEncrypted=false, isPubKeyOnly=false}
    addr:yWGW31QSArqjncjmn74AFaay7SCrP1oYUa  hash160:6d22ab738e8b321738b382e1a10f4d0c50c905e9  (M/9H/1H/12H/0, external)

  identity:  J2jWLKNWogVf1B8fdo6rxMeQgDWWi9aGd9JPcTHxNj7H
 */
    @Test
    fun registerTest() {
        try {
            val platform = Platform(EvoNetParams.get())
            val identityBytes = platform.client.getIdentity("J2jWLKNWogVf1B8fdo6rxMeQgDWWi9aGd9JPcTHxNj7H")
            val identity = platform.dpp.identity.createFromSerialized(identityBytes!!.toByteArray())
            var result = platform.names.register(
                "HashEngineering1", identity,
                ECKey.fromPrivateAndPrecalculatedPublic(
                    Utils.HEX.decode("0c82adc6e085cfc27c6620b1fa8eb8ea630bccaf47b459fb128f7c1827994e8d"),
                    Utils.HEX.decode("027874912e5d8e99cb129d4d0dc7c8285343c181c4c8272f89752c65b68ceb2c94")
                )
            )

            println(result.toJSON())
        } catch (e: StatusRuntimeException) {
            println(e)
            println(e.trailers)
        }
    }

    fun bouncy64(ba: ByteArray): String {
        return org.bouncycastle.util.encoders.Base64.toBase64String(ba)
    }

    fun google64(ba: ByteArray): String {
        return BaseEncoding.base64().encode(ba)
    }

    fun google64op(ba: ByteArray): String {
        return BaseEncoding.base64().omitPadding().encode(ba)
    }

    @Test
    fun Base64Test() {
        val sig = "QR8sFMxpk4N+hjQHvNu1QnLhxDqTglVlGc18MaSxt4uIFFp7TArHA0j+JE7Yev0XNQpm1wIii1oV4XrXiW/bmgDl"

        val bouncy = org.bouncycastle.util.encoders.Base64.decode(sig)
        val google = BaseEncoding.base64().decode(sig)
        val googleop = BaseEncoding.base64().omitPadding().decode(sig)

        val b = bouncy
        //val bouncy64 =
            assertEquals(bouncy64(b), google64(b))
            assertEquals(bouncy64(b), google64op(b))

    }
}