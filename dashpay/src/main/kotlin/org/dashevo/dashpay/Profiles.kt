/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashevo.dashpay

import org.bouncycastle.crypto.params.KeyParameter
import org.dashevo.platform.Platform

class Profiles(
    val platform: Platform, private val blockchainIdentity: BlockchainIdentity,
    private val keyParameter: KeyParameter
) {

    private val typeLocator: String = "dashpay.profile"

    fun create(
        displayName: String,
        publicMessage: String
    ) {
        val profileDocument = platform.documents.create(
            typeLocator, blockchainIdentity.identity!!,
            mutableMapOf<String, Any?>(
                "publicMessage" to publicMessage,
                "displayName" to displayName,
                "publicMessage" to publicMessage,
                "avatarUrl" to "https://api.adorable.io/avatars/120/${displayName}"
            )
        )
        val profileStateTransition =
            platform.dpp.document.createStateTransition(listOf(profileDocument))
        blockchainIdentity.signStateTransition(profileStateTransition, keyParameter)
        platform.client.applyStateTransition(profileStateTransition)
    }

}