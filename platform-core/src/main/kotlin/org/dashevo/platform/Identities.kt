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
import org.dashevo.dpp.identifier.Identifier
import org.dashevo.dpp.identity.Identity
import org.dashevo.dpp.identity.IdentityPublicKey

class Identities(val platform: Platform) {

    fun register(
        signedLockTransaction: CreditFundingTransaction,
        identityPublicKeys: List<IdentityPublicKey>
    ): String {
        return register(
            signedLockTransaction.lockedOutpoint,
            signedLockTransaction.creditBurnPublicKey,
            identityPublicKeys
        )
    }

    fun register(
        lockedOutpoint: TransactionOutPoint,
        assetLockPrivateKey: ECKey,
        identityPublicKeys: List<IdentityPublicKey>
    ): String {

        try {
            val outPoint = lockedOutpoint.bitcoinSerialize()

            val identityCreateTransition = platform.dpp.identity.createIdentityCreateTransition(outPoint, identityPublicKeys)

            identityCreateTransition.signByPrivateKey(assetLockPrivateKey)

            platform.client.broadcastStateTransition(identityCreateTransition, retryCallback = platform.broadcastRetryCallback);
            return identityCreateTransition.identityId.toString()
        } catch (e: Exception) {
            throw e
        }
    }

    fun get(id: String): Identity? {
        return get(Identifier.from(id))
    }

    fun get(id: Identifier): Identity? {
        val identityBuffer = platform.client.getIdentity(id.toBuffer()) ?: return null
        return platform.dpp.identity.createFromBuffer(identityBuffer.toByteArray());
    }

    fun getByPublicKeyHash(pubKeyHash: ByteArray): Identity? {
        val identityBuffer = platform.client.getIdentityByFirstPublicKey(pubKeyHash) ?: return null
        return platform.dpp.identity.createFromBuffer(identityBuffer.toByteArray());
    }

    fun topUp(
        identityId: Identifier,
        signedLockTransaction: CreditFundingTransaction
    ): Boolean {
        return topUp(
            identityId,
            signedLockTransaction.lockedOutpoint,
            signedLockTransaction.creditBurnPublicKey
        )
    }

    fun topUp(
        identityId: Identifier,
        lockedOutpoint: TransactionOutPoint,
        assetLockPrivateKey: ECKey
    ): Boolean {

        try {
            val identityTopupTransition = platform.dpp.identity.createIdentityTopupTransition(identityId, lockedOutpoint)

            identityTopupTransition.signByPrivateKey(assetLockPrivateKey)

            platform.client.broadcastStateTransition(identityTopupTransition)

            return true
        } catch (e: Exception) {
            throw e
        }
    }
}