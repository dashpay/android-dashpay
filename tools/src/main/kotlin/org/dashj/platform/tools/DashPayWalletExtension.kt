package org.dashj.platform.tools

import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.WalletExtension
import org.dashj.platform.dashpay.BlockchainIdentity

class DashPayWalletExtension : WalletExtension {
    companion object {
        const val NAME = "org.dashevo.dashpay.DashPayWalletExtension"
    }
    //val blockchainIdentity: BlockchainIdentity
    override fun deserializeWalletExtension(containingWallet: Wallet?, data: ByteArray?) {
        TODO("Not yet implemented")
    }

    override fun getWalletExtensionID(): String {
        return NAME
    }

    override fun isWalletExtensionMandatory(): Boolean {
        return false
    }

    override fun serializeWalletExtension(): ByteArray {
        TODO("Not yet implemented")
    }
}