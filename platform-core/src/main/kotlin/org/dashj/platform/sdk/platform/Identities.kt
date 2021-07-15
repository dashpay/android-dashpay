/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashj.platform.sdk.platform

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Transaction
import org.bitcoinj.evolution.CreditFundingTransaction
import org.bitcoinj.quorums.InstantSendLock
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.dpp.identity.ChainAssetLockProof
import org.dashj.platform.dpp.identity.Identity
import org.dashj.platform.dpp.identity.IdentityPublicKey
import org.dashj.platform.dpp.identity.InstantAssetLockProof
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Identities(val platform: Platform) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(Identities::class.java)
    }

    fun register(
        signedLockTransaction: CreditFundingTransaction,
        instantLock: InstantSendLock,
        identityPublicKeys: List<IdentityPublicKey>
    ): Identity {
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
        transaction: CreditFundingTransaction,
        instantLock: InstantSendLock,
        assetLockPrivateKey: ECKey,
        identityPublicKeys: List<IdentityPublicKey>
    ): Identity {
        try {
            val assetLock = InstantAssetLockProof(outputIndex, transaction, instantLock)

            val identityCreateTransition = platform.dpp.identity.createIdentityCreateTransition(assetLock, identityPublicKeys)

            identityCreateTransition.signByPrivateKey(assetLockPrivateKey)

            platform.broadcastStateTransition(identityCreateTransition)

            // get the identity from Platform since it cannot be recreated from the transition with the balance, etc
            platform.stateRepository.addValidIdentity(identityCreateTransition.identityId)

            return Identity(identityCreateTransition.identityId, identityPublicKeys, 0, identityCreateTransition.protocolVersion)
        } catch (e: Exception) {
            log.info("registerIdentity failure: $e")
            throw e
        }
    }

    fun register(
        outputIndex: Long,
        transaction: CreditFundingTransaction,
        coreHeight: Long,
        assetLockPrivateKey: ECKey,
        identityPublicKeys: List<IdentityPublicKey>
    ): Identity {
        try {
            val assetLock = ChainAssetLockProof(coreHeight, transaction.getOutput(outputIndex).outPointFor)

            val identityCreateTransition = platform.dpp.identity.createIdentityCreateTransition(assetLock, identityPublicKeys)

            identityCreateTransition.signByPrivateKey(assetLockPrivateKey)

            platform.broadcastStateTransition(identityCreateTransition)

            // get the identity from Platform since it cannot be recreated from the transition with the balance, etc
            platform.stateRepository.addValidIdentity(identityCreateTransition.identityId)

            return Identity(identityCreateTransition.identityId, identityPublicKeys, 0, identityCreateTransition.protocolVersion)
        } catch (e: Exception) {
            log.info("registerIdentity failure: $e")
            throw e
        }
    }

    fun get(id: String): Identity? {
        return get(Identifier.from(id))
    }

    fun get(id: Identifier): Identity? {
        val identityBuffer = platform.client.getIdentity(id.toBuffer(), true, platform.identitiesRetryCallback) ?: return null
        return platform.dpp.identity.createFromBuffer(identityBuffer.toByteArray())
    }

    fun getByPublicKeyHash(pubKeyHash: ByteArray): Identity? {
        val identityBuffer = platform.client.getIdentityByFirstPublicKey(pubKeyHash) ?: return null
        return platform.dpp.identity.createFromBuffer(identityBuffer.toByteArray())
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
            val assetLock = InstantAssetLockProof(outputIndex, transaction, instantLock)

            val identityTopupTransition = platform.dpp.identity.createIdentityTopupTransition(identityId, assetLock)

            identityTopupTransition.signByPrivateKey(assetLockPrivateKey)

            platform.broadcastStateTransition(identityTopupTransition)

            return true
        } catch (e: Exception) {
            log.info("topup failure: $e")
            throw e
        }
    }
}