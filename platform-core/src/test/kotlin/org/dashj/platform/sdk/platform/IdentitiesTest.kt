package org.dashj.platform.sdk.platform

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Transaction
import org.bitcoinj.wallet.DerivationPathFactory
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.Wallet
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dpp.statetransition.StateTransitionFactory
import org.dashj.platform.dpp.toHex
import org.dashj.platform.dpp.util.Converters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.*

class IdentitiesTest : PlatformNetwork() {

    @Test
    fun getTest() {
        val results = platform.documents.get(Names.DPNS_DOMAIN_DOCUMENT, DocumentQuery.builder().limit(5).build())

        for (document in results) {
            val domainDoc = DomainDocument(document)
            val expectedIdentityId = domainDoc.dashUniqueIdentityId ?: domainDoc.dashAliasIdentityId!!
            val identity = platform.identities.get(expectedIdentityId.toString())

            assertEquals(expectedIdentityId, identity!!.id)
        }
    }

    @Test
    fun getIdentitiesTest() {
        val pubKeyHash = ECKey().pubKeyHash
        val results = platform.identities.getByPublicKeyHash(pubKeyHash)

        assertNull(results)
    }

    @Test
    fun getAssetLockTxTest() {
        val tx = platform.client.getTransaction(assetLockTxId)!!.transaction
        assertEquals(assetLockTxId, Transaction(platform.params, tx).txId.toString())
    }

    @Test
    fun identityCreationTest() {
        val seed = "wave neck reduce unusual sick online suspect angry parade vintage valid magnet"


        val wallet = Wallet(
            platform.params,
            KeyChainGroup.builder(platform.params)
                .addChain(
                    DeterministicKeyChain.builder()
                        .accountPath(DerivationPathFactory.get(platform.params).bip44DerivationPath(0))
                        .seed(DeterministicSeed(seed, null, "", Date().time))
                        .build()
                )
                .build()
        )
        wallet.initializeAuthenticationKeyChains(wallet.keyChainSeed, null)

        val stateTransactionMap =
            Converters.fromHex("01000000a4647479706502697369676e6174757265584120c487a717e287581b498e6ce57a3fb8aaa243a890a971aef629b49607175a81317375cff370c285372e671713dbd508f6971cc65fea9be52b39ca1fb2ce5f837d6a7075626c69634b65797381a56269640064646174615821027ab03b55f24c3b5b66c2cc51f7efa0b97514b18eedb8dd3eccccfb276de5e2d564747970650067707572706f7365006d73656375726974794c6576656c006e61737365744c6f636b50726f6f66a46474797065006b696e7374616e744c6f636b58a501ed8bf70f95d1597fd3c911316c7719cded75bdce56870734ae0523065c633a4a00000000041d6353b4703d808ea1ac60fcc29b8b482a95829d84da39d909936cf14c470390bbcb7159e4a33e34d5e4e1e3f1ad6aaeb88d9b9fd2e406505b6d3a3b2197ab1bebe88d37964f55527581ee2193be0a085a12a5a2d6c94299594203ad2988bf5efd1b8e34f18a98580796668828117b27900dab84b48beaeac978ab4cf06f776b6f7574707574496e646578006b7472616e73616374696f6e58de0100000001ed8bf70f95d1597fd3c911316c7719cded75bdce56870734ae0523065c633a4a000000006a47304402206a57dda1bf6c66aca4d25959ff2a4450759496bfb0ce01d141a6ef09e4b8e5e90220322cd9ea02c9df5020e57f4224fb9fe856e266fe69576cb447bd75412201b98a0121032499905a363913995b704b4739e1e5a56944e9f6eb9a1ae2edc20daced90f6f4ffffffff0240420f0000000000166a1402b2cc99befd641784030f761e956cf290978af9fc858b3b000000001976a91478ce5f54af0929691c48b59bf526aa625d10b59988ac00000000")

        val stateTransition = StateTransitionFactory(platform.dpp, platform.stateRepository).createFromBuffer(stateTransactionMap)

        println(stateTransition)

        val verified = stateTransition.verifySignatureByPublicKey(wallet.blockchainIdentityKeyChain.watchingKey)
        val key = wallet.blockchainIdentityFundingKeyChain.freshAuthenticationKey();
        val verified2 = stateTransition.verifySignatureByPublicKey(key)

        println("verification: $verified, $verified2")
        val oldSignature = stateTransition.signature
        stateTransition.signByPrivateKey(key)

        println ("new sig - funding: ${stateTransition.signature!!.toHex()}")
        println ("old sig - original: ${oldSignature!!.toHex()}")

        stateTransition.signByPrivateKey(wallet.blockchainIdentityKeyChain.watchingKey)
        println ("new sig - identity: ${stateTransition.signature!!.toHex()}")

    }
}
