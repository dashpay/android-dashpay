package org.dashevo.platform

import org.bitcoinj.evolution.CreditFundingTransaction
import org.dashevo.dpp.identity.Identity
import org.dashevo.dpp.identity.IdentityCreateTransition
import org.dashevo.dpp.identity.IdentityPublicKey
import org.dashevo.dpp.toBase64
import org.dashevo.dpp.toHexString

class Identities(val platform: Platform) {

    fun register(identityType: Identity.IdentityType = Identity.IdentityType.USER, signedLockTransaction: CreditFundingTransaction): String
    {
        val identityHDPrivateKey = signedLockTransaction.creditBurnPublicKey

        try {
            val outPoint = signedLockTransaction.lockedOutpoint.toStringBase64()
            // FIXME (this will be fixed later, for now add one to the actual index)
            val publicKeyId = signedLockTransaction.usedDerivationPathIndex + 1

            val identityPublicKeyModel = IdentityPublicKey(publicKeyId,
                IdentityPublicKey.TYPES.ECDSA_SECP256K1,
                identityHDPrivateKey.pubKey.toBase64(),
                true)

            val identityCreateTransition = IdentityCreateTransition(identityType, outPoint, listOf(identityPublicKeyModel))

            identityCreateTransition.sign(identityPublicKeyModel, identityHDPrivateKey.privateKeyAsHex)

            platform.client.applyStateTransition(identityCreateTransition);
            return identityCreateTransition.identityId
        } catch (e: Exception) {
            throw e
        }
    }

    fun get(id: String): Identity? {
        val identityBuffer = platform.client.getIdentity(id) ?: return null
        return platform.dpp.identity.createFromSerialized(identityBuffer.toByteArray());
    }
}