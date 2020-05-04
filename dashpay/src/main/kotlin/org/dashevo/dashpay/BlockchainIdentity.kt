/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.dashpay

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import org.bitcoinj.core.*
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.KeyCrypter
import org.bitcoinj.evolution.CreditFundingTransaction
import org.bitcoinj.wallet.DerivationPathFactory
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.dashevo.dashpay.callback.RegisterIdentityCallback
import org.dashevo.dashpay.callback.RegisterNameCallback
import org.dashevo.dashpay.callback.RegisterPreorderCallback
import org.dashevo.platform.Platform
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.document.DocumentsStateTransition
import org.dashevo.dpp.identity.Identity
import org.dashevo.dpp.identity.IdentityPublicKey
import org.dashevo.dpp.statetransition.StateTransition
import org.dashevo.dpp.toBase64
import org.dashevo.dpp.toHexString
import org.dashevo.dpp.util.Cbor
import org.dashevo.dpp.util.Entropy
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.timerTask

class BlockchainIdentity {

    var platform: Platform
    var params: NetworkParameters

    private constructor(params: NetworkParameters) {
        this.params = params
        platform = Platform(params)
    }

    companion object {
        const val BLOCKCHAIN_USERNAME_SALT = "BLOCKCHAIN_USERNAME_SALT"
        const val BLOCKCHAIN_USERNAME_STATUS = "BLOCKCHAIN_USERNAME_STATUS"

        private val log = LoggerFactory.getLogger(Peer::class.java)
    }

    enum class RegistrationStatus {
        UNKNOWN,
        REGISTERING,
        REGISTERED,
        NOT_REGISTERED
    }

    enum class UsernameStatus(val value: Int) {
        NOT_PRESENT(0),
        INITIAL(1),
        PREORDER_REGISTRATION_PENDING(2),
        PREORDERED(3),
        REGISTRATION_PENDING(4),
        CONFIRMED(5),
        TAKEN_ON_NETWORK(6);

        companion object {
            private val values = values()
            fun getByCode(code: Int): UsernameStatus {
                return values.filter { it.value == code }[0]
            }
        }
    }

    enum class IdentityKeyStatus {
        UNKNOWN,
        REGISTERED,
        REGISTERING,
        NOT_REGISTERED,
        REVOKED
    }

    lateinit var usernameStatuses: MutableMap<String, Any?>

    /** This is the unique identifier representing the blockchain identity. It is derived from the credit funding transaction credit burn UTXO */
    lateinit var uniqueId: Sha256Hash
    val uniqueIdString: String
        get() = uniqueId.toStringBase58()
    val uniqueIdData: ByteArray
        get() = uniqueId.bytes

    var identity: Identity? = null

    /** This is if the blockchain identity is present in wallets or not. If this is false then the blockchain identity is known for example from being a dashpay friend. */
    var isLocal: Boolean = false

    var wallet: Wallet? = null

    lateinit var lockedOutpoint: TransactionOutPoint
    val lockedOutpointData: ByteArray?
        get() = lockedOutpoint.bitcoinSerialize()

    var index: Int = 0

    //lateinit var usernames: List<String>

    var currentUsername: String? = null

    val registrationFundingAddress: Address
        get() = Address.fromKey(wallet!!.params, registrationFundingPrivateKey)

    //var dashpayBioString: String

    lateinit var registrationStatus: RegistrationStatus

    lateinit var usernameSalts: MutableMap<String, ByteArray>

    var registered: Boolean = false

    var creditBalance: Coin = Coin.ZERO

    var activeKeyCount: Int = 0

    var totalKeyCount: Int = 0

    lateinit var type: Identity.IdentityType

    var keysCreated: Long = 0

    lateinit var keyInfo: MutableMap<Long, MutableMap<String, Any>>
    var currentMainKeyIndex: Int = 0
    var currentMainKeyType: IdentityPublicKey.TYPES = IdentityPublicKey.TYPES.ECDSA_SECP256K1
    lateinit var creditFundingTransaction: CreditFundingTransaction
    lateinit var registrationFundingPrivateKey: ECKey


    constructor(uniqueId: Sha256Hash, params: NetworkParameters) : this(params) {
        Preconditions.checkArgument(uniqueId != Sha256Hash.ZERO_HASH, "uniqueId must not be zero");
        this.uniqueId = uniqueId
        this.isLocal = false
        this.keysCreated = 0
        this.currentMainKeyIndex = 0
        this.currentMainKeyType = IdentityPublicKey.TYPES.ECDSA_SECP256K1
        this.usernameStatuses = HashMap()
        this.keyInfo = HashMap()
        this.registrationStatus = RegistrationStatus.REGISTERED
        this.type = Identity.IdentityType.UNKNOWN //we don't yet know the type
    }

    constructor(type: Identity.IdentityType, index: Int, wallet: Wallet) : this(wallet.getParams()) {
        Preconditions.checkArgument(index != Int.MAX_VALUE && index != Int.MIN_VALUE, "index must be found");

        this.wallet = wallet
        this.isLocal = true
        this.keysCreated = 0
        this.currentMainKeyIndex = 0
        this.currentMainKeyType = IdentityPublicKey.TYPES.ECDSA_SECP256K1
        this.index = index;
        this.usernameStatuses = HashMap()
        this.keyInfo = HashMap()
        this.registrationStatus = RegistrationStatus.UNKNOWN
        this.usernameSalts = HashMap()
        this.type = type
    }

    constructor(type: Identity.IdentityType, index: Int, lockedOutpoint: TransactionOutPoint, wallet: Wallet) :
            this(type, index, wallet) {
        Preconditions.checkArgument(lockedOutpoint.hash != Sha256Hash.ZERO_HASH, "utxo must not be null");
        this.lockedOutpoint = lockedOutpoint;
        this.uniqueId = Sha256Hash.twiceOf(lockedOutpoint.bitcoinSerialize())
    }

    constructor(type: Identity.IdentityType, transaction: CreditFundingTransaction, wallet: Wallet) :
            this(type, transaction.usedDerivationPathIndex, transaction.lockedOutpoint, wallet) {
        Preconditions.checkArgument(!transaction.creditBurnPublicKey.isPubKeyOnly)
        creditFundingTransaction = transaction
        registrationFundingPrivateKey = transaction.creditBurnPublicKey

        //see if the identity is registered.
        try {
            if (platform.identities.get(uniqueIdString) != null)
                registrationStatus = RegistrationStatus.REGISTERED
            else registrationStatus = RegistrationStatus.NOT_REGISTERED
        } catch (x: Exception) {
            //swallow and leave the status as unknown
        }
    }

    constructor(
        type: Identity.IdentityType,
        transaction: CreditFundingTransaction,
        usernameStatus: MutableMap<String, Any>,
        wallet: Wallet
    ) :
            this(type, transaction, wallet) {
        if (getUsernames().isNotEmpty()) {
            val usernameSalts = HashMap<String, ByteArray>()
            for (username in usernameStatus.keys) {
                val data = usernameStatus[username] as MutableMap<String, Any?>
                var salt = data[BLOCKCHAIN_USERNAME_SALT]
                if (salt != null) {
                    usernameSalts[username] = salt as ByteArray
                }
            }
            this.usernameStatuses = copyMap(usernameStatus)
            this.usernameSalts = usernameSalts;
        }
    }

    private fun copyMap(map: MutableMap<String, Any>): MutableMap<String, Any?> {
        return Cbor.decode(Cbor.encode(map))
    }

    constructor(
        type: Identity.IdentityType,
        index: Int,
        transaction: CreditFundingTransaction,
        usernameStatus: MutableMap<String, Any>,
        credits: Coin,
        registrationStatus: RegistrationStatus,
        wallet: Wallet
    ) :
            this(type, transaction, usernameStatus, wallet) {
        creditBalance = credits;
        this.registrationStatus = registrationStatus;
    }


    // MARK: - Full Registration agglomerate

    /*
        not sure if this method works.  The app may create its own tx, broadcast it, then start the process at registerIdentity()
     */
    fun sendCreditFundingTransaction(credits: Coin) {
        Preconditions.checkState(type != Identity.IdentityType.UNKNOWN, "The identity type must be USER or APPLICATION")
        Preconditions.checkState(creditFundingTransaction == null, "The credit funding transaction must not exist")
        Preconditions.checkState(
            registrationStatus == RegistrationStatus.NOT_REGISTERED,
            "The identity must not be registered"
        )
        val privateKey = wallet!!.blockchainIdentityFundingKeyChain.currentAuthenticationKey()
        val request = SendRequest.creditFundingTransaction(wallet!!.params, privateKey, credits)

        val sendResult = wallet!!.sendCoins(request)
        sendResult.broadcastComplete.addListener(Runnable {
            creditFundingTransaction = request.tx as CreditFundingTransaction
        }, MoreExecutors.directExecutor())
    }

    fun registerIdentity() {
        Preconditions.checkState(type != Identity.IdentityType.UNKNOWN, "The identity type must be USER or APPLICATION")
        Preconditions.checkState(
            registrationStatus == RegistrationStatus.NOT_REGISTERED,
            "The identity must not be registered"
        )
        Preconditions.checkNotNull(creditFundingTransaction, "The credit funding transaction must exist")

        platform.identities.register(type, creditFundingTransaction)

        registrationStatus = RegistrationStatus.REGISTERED

        finalizeIdentityRegistration(creditFundingTransaction.creditBurnIdentityIdentifier)
    }

    fun finalizeIdentityRegistration(fundingTransaction: CreditFundingTransaction) {
        this.creditFundingTransaction = fundingTransaction;
        this.lockedOutpoint = fundingTransaction.lockedOutpoint;
        finalizeIdentityRegistration(fundingTransaction.creditBurnIdentityIdentifier);
    }

    fun finalizeIdentityRegistration(uniqueId: Sha256Hash) {
        if (isLocal) {
            this.uniqueId = uniqueId
            finalizeIdentityRegistration()
        }
    }

    fun finalizeIdentityRegistration() {
        if (isLocal) {
            saveInitial()
        }
    }

    fun unregisterLocally(): Boolean {
        return if (isLocal && !registered) {
            //[self.wallet unregisterBlockchainIdentity : self];
            //deletePersistentObjectAndSave(true)
            true
        } else false
    }


    // MARK: Registering
    //Preorder stage
    fun registerPreorderedSaltedDomainHashesForUsernames(usernames: List<String>) {
        val transition = preorderTransitionForUnregisteredUsernames(usernames)
        if (transition == null) {
            return;
        }
        signStateTransition(transition)

        platform.client.applyStateTransition(transition)

        for (string in usernames) {
            var usernameStatusDictionary = usernameStatuses[string] as MutableMap<String, Any>
            if (usernameStatusDictionary == null) {
                usernameStatusDictionary = HashMap<String, Any>()
            }
            usernameStatusDictionary[BLOCKCHAIN_USERNAME_STATUS] = UsernameStatus.PREORDER_REGISTRATION_PENDING
            usernameStatuses[string] = usernameStatusDictionary
        }
        saveUsernames(usernames, UsernameStatus.PREORDER_REGISTRATION_PENDING)
    }

    fun registerUsernameDomainsForUsernames(usernames: List<String>) {
        val transition = domainTransitionForUnregisteredUsernames(usernames)
        if (transition == null) {
            return;
        }
        signStateTransition(transition!!)

        platform.client.applyStateTransition(transition)

        for (string in usernames) {
            var usernameStatusDictionary = usernameStatuses[string] as MutableMap<String, Any>
            if (usernameStatusDictionary == null) {
                usernameStatusDictionary = HashMap<String, Any>()
            }
            usernameStatusDictionary[BLOCKCHAIN_USERNAME_STATUS] = UsernameStatus.REGISTRATION_PENDING
            usernameStatuses[string] = usernameStatusDictionary
        }
        saveUsernames(usernames, BlockchainIdentity.UsernameStatus.REGISTRATION_PENDING)

    }
    //

    // MARK: Username Helpers

    fun saltForUsername(username: String, saveSalt: Boolean): ByteArray {
        var salt: ByteArray
        if (statusOfUsername(username) == UsernameStatus.INITIAL || !(usernameSalts.containsKey(username))) {
            salt = Entropy.generateBytes()
            usernameSalts[username] = salt
            if (saveSalt) {
                saveUsername(username, statusOfUsername(username), salt, true)
            }
        } else {
            salt = usernameSalts[username]!!
        }
        return salt;
    }

    fun saltedDomainHashesForUsernames(usernames: List<String>): MutableMap<String, ByteArray> {
        val mSaltedDomainHashes = HashMap<String, ByteArray>()
        for (unregisteredUsername in usernames) {
            val salt = saltForUsername(unregisteredUsername, true)
            val saltedDomainHashData = platform.names.getSaltedDomainHashBytes(salt, unregisteredUsername)
            mSaltedDomainHashes[unregisteredUsername] = saltedDomainHashData
            usernameSalts[unregisteredUsername] = salt //is this required?
        }
        return mSaltedDomainHashes
    }

    // MARK: Documents

    fun preorderDocumentsForUnregisteredUsernames(unregisteredUsernames: List<String>): List<Document> {
        val usernamePreorderDocuments = ArrayList<Document>()
        for (saltedDomainHash in saltedDomainHashesForUsernames(unregisteredUsernames).values) {
            val document = platform.names.createPreorderDocument(Sha256Hash.wrap(saltedDomainHash), identity!!)
            usernamePreorderDocuments.add(document)
        }
        return usernamePreorderDocuments;
    }

    fun domainDocumentsForUnregisteredUsernames(unregisteredUsernames: List<String>): List<Document> {
        val usernameDomainDocuments = ArrayList<Document>()
        for (username in saltedDomainHashesForUsernames(unregisteredUsernames).keys) {
            val document =
                platform.names.createDomainDocument(identity!!, username, usernameSalts[username]!!.toHexString())
            usernameDomainDocuments.add(document)
        }
        return usernameDomainDocuments
    }

    // MARK: Transitions

    fun preorderTransitionForUnregisteredUsernames(unregisteredUsernames: List<String>): DocumentsStateTransition? {
        val usernamePreorderDocuments = preorderDocumentsForUnregisteredUsernames(unregisteredUsernames)
        if (usernamePreorderDocuments.isEmpty()) return null
        return platform.dpp.document.createStateTransition(usernamePreorderDocuments);
    }

    fun domainTransitionForUnregisteredUsernames(unregisteredUsernames: List<String>): DocumentsStateTransition? {
        val usernamePreorderDocuments = domainDocumentsForUnregisteredUsernames(unregisteredUsernames)
        if (usernamePreorderDocuments.isEmpty()) return null
        return platform.dpp.document.createStateTransition(usernamePreorderDocuments);
    }

    // MARK: Usernames


    fun addUsername(username: String, status: UsernameStatus, save: Boolean) {
        val map = HashMap<String, UsernameStatus>()
        map[BLOCKCHAIN_USERNAME_STATUS] = UsernameStatus.INITIAL
        usernameStatuses[username] = map

        if (save) {
            saveNewUsername(username, UsernameStatus.INITIAL)
        }
        if (registered && status != UsernameStatus.CONFIRMED) {
            //do we trigger a listener here?
        }
    }

    fun addUsername(username: String, save: Boolean = true) {
        addUsername(username, UsernameStatus.INITIAL, save)
    }

    fun statusOfUsername(username: String): UsernameStatus {
        return if (usernameStatuses.containsKey(username)) {
            (usernameStatuses[username] as MutableMap<String, Any>)[BLOCKCHAIN_USERNAME_STATUS] as UsernameStatus
        } else UsernameStatus.NOT_PRESENT
    }

    fun getUsernames(): List<String> {
        return usernameStatuses.keys.toList()
    }

    fun getUsernamesWithStatus(usernameStatus: UsernameStatus): MutableList<String> {
        val usernames = ArrayList<String>()
        for (username in usernameStatuses.keys) {
            val usernameInfo = usernameStatuses[username] as MutableMap<String, Any?>
            val status = usernameInfo[BLOCKCHAIN_USERNAME_STATUS] as UsernameStatus
            if (status == usernameStatus) {
                usernames.add(username)
            }
        }
        return usernames
    }

    fun getUnregisteredUsernames(): MutableList<String> {
        return getUsernamesWithStatus(UsernameStatus.INITIAL)
    }

    fun preorderedUsernames(): MutableList<String> {
        return getUsernamesWithStatus(UsernameStatus.PREORDERED)
    }

    // MARK: - Signing and Encryption

    fun signStateTransition(
        transition: StateTransition,
        keyIndex: Int,
        signingAlgorithm: IdentityPublicKey.TYPES,
        keyCrypter: KeyCrypter? = null
    ) {

        val privateKey = privateKeyAtIndex(keyIndex, signingAlgorithm)
        Preconditions.checkState(privateKey != null, "The private key should exist");

        val identityPublicKey = IdentityPublicKey(keyIndex + 1, signingAlgorithm, privateKey!!.pubKey.toBase64(), true)
        transition.sign(identityPublicKey, privateKey!!.privateKeyAsHex)
    }

    fun signStateTransition(transition: StateTransition) {
        /*if (keysCreated == 0) {
            uint32_t index
            [self createNewKeyOfType:DEFAULT_SIGNING_ALGORITH returnIndex:&index];
        }*/
        return signStateTransition(
            transition,
            identity!!.publicKeys[0].id - 1/* currentMainKeyIndex*/,
            currentMainKeyType
        )
    }

    fun derivationPathForType(type: IdentityPublicKey.TYPES): ImmutableList<ChildNumber>? {
        if (isLocal) {
            if (type == IdentityPublicKey.TYPES.ECDSA_SECP256K1) {
                return DerivationPathFactory(wallet!!.params).blockchainIdentityECDSADerivationPath()
            } else if (type == IdentityPublicKey.TYPES.BLS) {
                return DerivationPathFactory(wallet!!.params).blockchainIdentityBLSDerivationPath()
            }
        }
        return null
    }

    // multiple keys are not yet supported

    private fun privateKeyAtIndex(index: Int, type: IdentityPublicKey.TYPES): ECKey? {
        if (isLocal) {

            //val derivationPath = ImmutableList.of(derivationPathForType(type), ChildNumber(index, false))

            //val authenticationChain = wallet!!.blockchainIdentityKeyChain
            //val authenticationChain = wallet!!.blockchainIdentityFundingKeyChain

            //val key = authenticationChain.getKey(index - 1)

            return registrationFundingPrivateKey //key
        } else return null
    }
/*
    fun privateKeyAtIndex(index: Int, type: IdentityPublicKey.TYPES, orSeed:(NSData*)seed): ECKey {
        if (!_isLocal) return nil;
        const NSUInteger indexes[] = {_index,index};
        NSIndexPath * indexPath = [NSIndexPath indexPathWithIndexes:indexes length:2];

        DSAuthenticationKeysDerivationPath * derivationPath = [self derivationPathForType:type];

        return [derivationPath privateKeyAtIndexPath:indexPath fromSeed:seed];
    }*/

    // MARK: Saving

    fun saveInitial() {
        /*
            save the following to the Room Database
            - uniqueId
            - isLocal
            - if (isLocal) {
                creditFundingTransaction

                }
             - network id (wallet.params.id)
             - usernameStatuses
             - DashPay stuff...
         */
        //TODO()
    }


    fun save() {
        // save updates to creditBalance, registrationStatus, type
        // send notifications for the items that were updated
    }

    fun saveUsernames(usernames: List<String>, status: UsernameStatus) {
        for (username in usernames) {
            saveUsername(username, status, null, false)
        }
    }

    fun saveUsernamesToStatuses(dictionary: MutableMap<String, UsernameStatus>) {
        for (username in dictionary.keys) {
            val status = dictionary[username]
            saveUsername(username, status!!, null, false)
        }
    }

    fun saveUsername(username: String, status: UsernameStatus, salt: ByteArray?, commitSave: Boolean) {
        // save the username information to the Room database
        //TODO()
    }

    fun saveNewUsername(username: String, status: UsernameStatus) {
        val salt = saltForUsername(username, false)
        saveUsername(username, status, salt, true)
    }

    enum class RetryDelayType {
        LINEAR,
        SLOW20,
        SLOW50
    }


    //should this have a callback or let the client handle the end
    fun monitorForBlockchainIdentityWithRetryCount(
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType,
        callback: RegisterIdentityCallback
    ) {

        val identityResult = platform.identities.get(uniqueIdString)

        if (identityResult != null) {
            identity = identityResult
            registrationStatus = RegistrationStatus.REGISTERED
            save()
            callback.onComplete(uniqueIdString)
        } else {
            if (retryCount > 0) {
                Timer("monitorBlockchainIdentityStatus", false).schedule(timerTask {
                    val nextDelay = delayMillis * when (retryDelayType) {
                        RetryDelayType.SLOW20 -> 5 / 4
                        RetryDelayType.SLOW50 -> 3 / 2
                        else -> 1
                    }
                    monitorForBlockchainIdentityWithRetryCount(retryCount - 1, nextDelay, retryDelayType, callback)
                }, delayMillis)
            } else callback.onTimeout()
        }
        //throw exception or return false
    }

    suspend fun monitorForBlockchainIdentityWithRetryCount(
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType
    ): String? {

        val identityResult = platform.identities.get(uniqueIdString)

        if (identityResult != null) {
            identity = identityResult
            registrationStatus = RegistrationStatus.REGISTERED
            save()
            return uniqueIdString
        } else {
            if (retryCount > 0) {
                val nextDelay = delayMillis * when (retryDelayType) {
                    RetryDelayType.SLOW20 -> 5 / 4
                    RetryDelayType.SLOW50 -> 3 / 2
                    else -> 1
                }
                kotlinx.coroutines.delay(nextDelay)
                monitorForBlockchainIdentityWithRetryCount(retryCount - 1, nextDelay, retryDelayType)
            }
        }
        return null
    }

    //should this have a callback or let the client handle the end
    fun monitorForDPNSPreorderSaltedDomainHashes(
        saltedDomainHashes: Map<String, ByteArray>,
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType,
        callback: RegisterPreorderCallback
    ) {

        val query = DocumentQuery.Builder()
            .where(listOf("saltedDomainHash", "in", saltedDomainHashes.map { "5620${it.value.toHexString()}" })).build()
        val preorderDocuments = platform.documents.get("dpns.preorder", query)

        if (preorderDocuments != null && preorderDocuments.isNotEmpty()) {
            val usernamesLeft = HashMap(saltedDomainHashes)
            for (username in saltedDomainHashes.keys) {
                val saltedDomainHashData = saltedDomainHashes[username] as ByteArray
                val saltedDomainHashString = "5620${saltedDomainHashData.toHexString()}"
                for (preorderDocument in preorderDocuments) {
                    if (preorderDocument.data["saltedDomainHash"] == saltedDomainHashString) {
                        var usernameStatus = if (usernameStatuses.containsKey(username))
                            usernameStatuses[username] as MutableMap<String, Any>
                        else HashMap()
                        usernameStatus[BLOCKCHAIN_USERNAME_STATUS] = UsernameStatus.PREORDERED
                        usernameStatuses[username] = usernameStatus
                        saveUsername(username, UsernameStatus.PREORDERED, null, true)
                        usernamesLeft.remove(username)
                    }
                }
            }
            if (usernamesLeft.size > 0 && retryCount > 0) {
                val saltedDomainHashesLeft = saltedDomainHashes.filter { usernamesLeft.containsKey(it.key) }
                Timer("monitorBlockchainIdentityStatus", false).schedule(timerTask {
                    val nextDelay = delayMillis * when (retryDelayType) {
                        RetryDelayType.SLOW20 -> 5 / 4
                        RetryDelayType.SLOW50 -> 3 / 2
                        else -> 1
                    }
                    monitorForDPNSPreorderSaltedDomainHashes(
                        saltedDomainHashesLeft,
                        retryCount - 1,
                        nextDelay,
                        retryDelayType,
                        callback
                    )
                }, delayMillis)
            } else if (usernamesLeft.size > 0) {
                val saltedDomainHashesLeft = saltedDomainHashes.filter { usernamesLeft.containsKey(it.key) }
                callback.onTimeout(saltedDomainHashesLeft.keys.toList())
            } else {
                callback.onComplete(saltedDomainHashes.keys.toList())
            }
        } else {
            if (retryCount > 0) {
                Timer("monitorForDPNSPreorderSaltedDomainHashes", false).schedule(timerTask {
                    val nextDelay = delayMillis * when (retryDelayType) {
                        RetryDelayType.SLOW20 -> 5 / 4
                        RetryDelayType.SLOW50 -> 3 / 2
                        else -> 1
                    }
                    monitorForDPNSPreorderSaltedDomainHashes(
                        saltedDomainHashes,
                        retryCount - 1,
                        nextDelay,
                        retryDelayType,
                        callback
                    )
                }, delayMillis)
            } else {
                callback.onTimeout(saltedDomainHashes.keys.toList())
            }
        }
        //throw exception or return false
    }

    fun monitorForDPNSUsernames(
        usernames: List<String>,
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType,
        callback: RegisterNameCallback
    ) {

        val query = DocumentQuery.Builder()
            .where("normalizedParentDomainName", "==", "dash")
            .where(listOf("normalizedLabel", "in", usernames.map { "${it.toLowerCase()}" })).build()
        val nameDocuments = platform.documents.get("dpns.domain", query)

        if (nameDocuments != null && nameDocuments.isNotEmpty()) {
            val usernamesLeft = ArrayList(usernames)
            for (username in usernames) {
                val normalizedName = username.toLowerCase()
                for (nameDocument in nameDocuments) {
                    if (nameDocument.data["normalizedLabel"] == normalizedName) {
                        var usernameStatus = if (usernameStatuses.containsKey(username))
                            usernameStatuses[username] as MutableMap<String, Any>
                        else HashMap()
                        usernameStatus[BLOCKCHAIN_USERNAME_STATUS] = UsernameStatus.CONFIRMED
                        usernameStatuses[username] = usernameStatus
                        saveUsername(username, UsernameStatus.CONFIRMED, null, true)
                        usernamesLeft.remove(username)
                    }
                }
            }
            if (usernamesLeft.size > 0 && retryCount > 0) {
                Timer("monitorForDPNSUsernames", false).schedule(timerTask {
                    val nextDelay = delayMillis * when (retryDelayType) {
                        RetryDelayType.SLOW20 -> 5 / 4
                        RetryDelayType.SLOW50 -> 3 / 2
                        else -> 1
                    }
                    monitorForDPNSUsernames(usernamesLeft, retryCount - 1, nextDelay, retryDelayType, callback)
                }, delayMillis)
            } else if (usernamesLeft.size > 0) {
                callback.onTimeout(usernamesLeft)
            } else {
                callback.onComplete(usernames)
            }
        } else {
            if (retryCount > 0) {
                Timer("monitorBlockchainIdentityStatus", false).schedule(timerTask {
                    val nextDelay = delayMillis * when (retryDelayType) {
                        RetryDelayType.SLOW20 -> 5 / 4
                        RetryDelayType.SLOW50 -> 3 / 2
                        else -> 1
                    }
                    monitorForDPNSUsernames(usernames, retryCount - 1, nextDelay, retryDelayType, callback)
                }, delayMillis)
            } else {
                callback.onTimeout(usernames)
            }
        }
        //throw exception or return false
    }

}