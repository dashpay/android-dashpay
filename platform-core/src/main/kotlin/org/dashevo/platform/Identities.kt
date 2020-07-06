/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.crypto.KeyCrypter
import org.bitcoinj.evolution.CreditFundingTransaction
import org.bouncycastle.crypto.params.KeyParameter
import org.dashevo.dpp.identity.Identity
import org.dashevo.dpp.identity.IdentityCreateTransition
import org.dashevo.dpp.identity.IdentityPublicKey
import org.dashevo.dpp.toBase64

class Identities(val platform: Platform) {

    fun register(
        signedLockTransaction: CreditFundingTransaction,
        keyCrypter: KeyCrypter?,
        keyParameter: KeyParameter?
    ): String {
        val identityPrivateKey = signedLockTransaction.creditBurnPublicKey.decrypt(keyCrypter, keyParameter)
        return register(
            signedLockTransaction.lockedOutpoint,
            identityPrivateKey,
            signedLockTransaction.usedDerivationPathIndex
        )
    }

    fun register(
        signedLockTransaction: CreditFundingTransaction
    ): String {
        return register(
            signedLockTransaction.lockedOutpoint,
            signedLockTransaction.creditBurnPublicKey,
            signedLockTransaction.usedDerivationPathIndex
        )
    }

    fun register(
        lockedOutpoint: TransactionOutPoint,
        creditBurnKey: ECKey,
        usedDerivationPathIndex: Int
    ): String {

        try {
            val outPoint = lockedOutpoint.toStringBase64()
            // FIXME (this will be fixed later, for now add one to the actual index)
            // This will be fixed in DPP 0.12
            val publicKeyId = usedDerivationPathIndex

            val identityPublicKeyModel = IdentityPublicKey(
                publicKeyId,
                IdentityPublicKey.TYPES.ECDSA_SECP256K1,
                creditBurnKey.pubKey.toBase64(),
                true
            )

            val identityCreateTransition = platform.dpp.identity.createIdentityCreateTransition(outPoint, listOf(identityPublicKeyModel))
                //IdentityCreateTransition(outPoint, listOf(identityPublicKeyModel))

            identityCreateTransition.signByPrivateKey(creditBurnKey)

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