/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.platform

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Transaction
import org.bitcoinj.evolution.CreditFundingTransaction
import org.bitcoinj.quorums.InstantSendLock
import org.dashevo.dpp.identifier.Identifier
import org.dashevo.dpp.identity.AssetLock
import org.dashevo.dpp.identity.Identity
import org.dashevo.dpp.identity.IdentityPublicKey
import org.dashevo.dpp.identity.InstantAssetLockProof

class Identities(val platform: Platform) {

    fun register(
        signedLockTransaction: CreditFundingTransaction,
        instantLock: InstantSendLock,
        identityPublicKeys: List<IdentityPublicKey>
    ): String {
        return register(
            signedLockTransaction.outputIndex,
            signedLockTransaction,
            instantLock,
            signedLockTransaction.creditBurnPublicKey,
            identityPublicKeys
        )
    }

    fun register(
        outputIndex: Long,
        transaction: Transaction,
        instantLock: InstantSendLock,
        assetLockPrivateKey: ECKey,
        identityPublicKeys: List<IdentityPublicKey>
    ): String {

        try {
            val instantAssetLockProof = InstantAssetLockProof(instantLock)
            val assetLock = AssetLock(outputIndex, transaction, instantAssetLockProof)

            val identityCreateTransition = platform.dpp.identity.createIdentityCreateTransition(assetLock, identityPublicKeys)

            identityCreateTransition.signByPrivateKey(assetLockPrivateKey)

            platform.broadcastStateTransition(identityCreateTransition);
            return identityCreateTransition.identityId.toString()
        } catch (e: Exception) {
            throw e
        }
    }

    fun get(id: String): Identity? {
        return get(Identifier.from(id))
    }

    fun get(id: Identifier): Identity? {
        val identityBuffer = platform.client.getIdentity(id.toBuffer(), platform.identitiesRetryCallback) ?: return null
        return platform.dpp.identity.createFromBuffer(identityBuffer.toByteArray());
    }

    fun getByPublicKeyHash(pubKeyHash: ByteArray): Identity? {
        val identityBuffer = platform.client.getIdentityByFirstPublicKey(pubKeyHash) ?: return null
        return platform.dpp.identity.createFromBuffer(identityBuffer.toByteArray());
    }

    fun topUp(
        identityId: Identifier,
        signedLockTransaction: CreditFundingTransaction,
        instantLock: InstantSendLock
    ): Boolean {
        return topUp(
            identityId,
            signedLockTransaction.outputIndex,
            signedLockTransaction,
            instantLock,
            signedLockTransaction.creditBurnPublicKey
        )
    }

    fun topUp(
        identityId: Identifier,
        outputIndex: Long,
        transaction: Transaction,
        instantLock: InstantSendLock,
        assetLockPrivateKey: ECKey
    ): Boolean {

        try {
            val instantAssetLockProof = InstantAssetLockProof(instantLock)
            val assetLock = AssetLock(outputIndex, transaction, instantAssetLockProof)

            val identityTopupTransition = platform.dpp.identity.createIdentityTopupTransition(identityId, assetLock)

            identityTopupTransition.signByPrivateKey(assetLockPrivateKey)

            platform.broadcastStateTransition(identityTopupTransition)

            return true
        } catch (e: Exception) {
            throw e
        }
    }
}