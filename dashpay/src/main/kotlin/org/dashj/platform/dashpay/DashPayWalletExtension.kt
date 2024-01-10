/**
 * Copyright (c) 2023-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashj.platform.dashpay

import com.google.protobuf.ByteString
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.WalletExtension
import org.bitcoinj.wallet.authentication.AuthenticationGroupExtension
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.dpp.identity.Identity
import org.dashj.platform.dpp.identity.IdentityPublicKey
import org.dashj.platform.sdk.platform.Platform
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DashPayWalletExtension(
    val platform: Platform,
    val authenticationGroupExtension: AuthenticationGroupExtension
) : WalletExtension {
    companion object {
        const val NAME = "org.dashj.dashpay.DashPayWalletExtension"

        val log: Logger = LoggerFactory.getLogger(DashPayWalletExtension::class.java)
    }
    var blockchainIdentity: BlockchainIdentity? = null

    override fun deserializeWalletExtension(containingWallet: Wallet?, data: ByteArray?) {
        val dashpay = Dashpay.DashPay.parseFrom(data)
        val identityProto = dashpay.identity

        if (dashpay.hasIdentity()) {
            if (blockchainIdentity == null) {
                blockchainIdentity = BlockchainIdentity(platform, 0, containingWallet!!, authenticationGroupExtension)
            }

            blockchainIdentity?.let {
                it.identity = Identity(
                    Identifier.from(identityProto.id.toByteArray()),
                    identityProto.balance,
                    identityProto.publicKeysList.map {
                        val ipk = IdentityPublicKey(
                            it.id,
                            IdentityPublicKey.Type.getByCode(it.type),
                            IdentityPublicKey.Purpose.getByCode(it.purpose),
                            IdentityPublicKey.SecurityLevel.getByCode(it.securityLevel),
                            it.data.toByteArray(),
                            it.readOnly,
                            if (it.disabledAt == -1L) {
                                null
                            } else {
                                it.disabledAt
                            },
                            if (it.signature.size() != 0) {
                                it.signature.toByteArray()
                            } else {
                                null
                            }
                        )
                        ipk
                    }.toMutableList(),
                    identityProto.revision,
                    identityProto.protocolVersion
                )

                it.registrationStatus = BlockchainIdentity.RegistrationStatus.REGISTERED
                it.uniqueId = it.identity!!.id.toSha256Hash()
            }
        }
        if (dashpay.username.isNotEmpty()) {
            blockchainIdentity?.currentUsername = dashpay.username
            if (!dashpay.salt.isEmpty) {
                blockchainIdentity?.usernameSalts?.put(dashpay.username, dashpay.salt.toByteArray())
            }
        }
    }

    fun validate(containingWallet: Wallet): Boolean {
        val authExtension = containingWallet.addOrGetExistingExtension(AuthenticationGroupExtension(containingWallet.params)) as AuthenticationGroupExtension
        // validate
        val list = authExtension.assetLockTransactions
        for (cftx in list) {
            val tx = authExtension.getAssetLockTransaction(cftx)
            if (tx.identityId.toString() != blockchainIdentity?.uniqueId.toString()) {
                log.error("Error: ${tx.identityId} != ${blockchainIdentity?.uniqueId}")
                return false
            }
        }

        // verify the identity

        val identity = platform.identities.get(blockchainIdentity!!.uniqueIdentifier)
        if (identity == null) {
            log.error("Error: ${blockchainIdentity!!.uniqueIdentifier} does not exist")
            return false
        }

        if (platform.names.get(blockchainIdentity!!.currentUsername!!) == null) {
            log.error("Error: DPNS domain ${blockchainIdentity!!.currentUsername} does not exist")
            return false
        }
        return true
    }

    override fun getWalletExtensionID(): String {
        return NAME
    }

    override fun isWalletExtensionMandatory(): Boolean {
        return false
    }

    override fun serializeWalletExtension(): ByteArray {
        val builder = Dashpay.DashPay.newBuilder()

        blockchainIdentity?.identity?.let { identity ->
            val identityBuilder = Dashpay.Identity.newBuilder().apply {
                protocolVersion = identity.protocolVersion
                addAllPublicKeys(
                    identity.publicKeys.map { identityPublicKey: IdentityPublicKey ->
                        Dashpay.IdentityPublicKey.newBuilder()
                            .setId(identityPublicKey.id)
                            .setData(ByteString.copyFrom(identityPublicKey.data))
                            .setPurpose(identityPublicKey.purpose.value)
                            .setReadOnly(identityPublicKey.readOnly)
                            .setSecurityLevel(identityPublicKey.securityLevel.value)
                            .setType(identityPublicKey.type.value)
                            .setDisabledAt(identityPublicKey.disabledAt ?: -1)
                            .build()
                    }
                )
                revision = identity.revision
                balance = identity.balance
                id = ByteString.copyFrom(identity.id.toBuffer())
            }
            builder.setIdentity(identityBuilder)
        }
        blockchainIdentity?.currentUsername?.let {
            builder.username = it
            blockchainIdentity?.usernameSalts?.get(it)?.let { salt ->
                builder.setSalt(ByteString.copyFrom(salt))
            }
        }
        return builder.build().toByteArray()
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("DASHPAY\n")
        if (blockchainIdentity != null && blockchainIdentity!!.identity != null) {
            builder.append("  ")
                .append("username:  ")
                .append(blockchainIdentity!!.currentUsername)
                .append("\n  identity: ")
                .append(blockchainIdentity!!.uniqueIdentifier)
        } else {
            builder.append("  No username for this wallet.")
        }
        return builder.toString()
    }
}
