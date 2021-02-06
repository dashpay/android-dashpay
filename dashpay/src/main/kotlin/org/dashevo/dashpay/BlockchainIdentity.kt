/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.dashevo.dashpay

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.delay
import org.bitcoinj.core.*
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.crypto.EncryptedData
import org.bitcoinj.crypto.HDUtils
import org.bitcoinj.crypto.KeyCrypterECDH
import org.bitcoinj.evolution.CreditFundingTransaction
import org.bitcoinj.evolution.EvolutionContact
import org.bitcoinj.quorums.InstantSendLock
import org.bitcoinj.wallet.AuthenticationKeyChain
import org.bitcoinj.wallet.DerivationPathFactory
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.FriendKeyChain
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.ZeroConfCoinSelector
import org.bouncycastle.crypto.params.KeyParameter
import org.dashevo.dapiclient.grpc.DefaultBroadcastRetryCallback
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dashpay.callback.RegisterIdentityCallback
import org.dashevo.dashpay.callback.RegisterNameCallback
import org.dashevo.dashpay.callback.RegisterPreorderCallback
import org.dashevo.dashpay.callback.UpdateProfileCallback
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.document.DocumentsBatchTransition
import org.dashevo.dpp.errors.InvalidIdentityAssetLockProofError
import org.dashevo.dpp.identifier.Identifier
import org.dashevo.dpp.identity.Identity
import org.dashevo.dpp.identity.IdentityPublicKey
import org.dashevo.dpp.statetransition.StateTransitionIdentitySigned
import org.dashevo.dpp.toHexString
import org.dashevo.dpp.util.Cbor
import org.dashevo.dpp.util.HashUtils
import org.dashevo.platform.Names
import org.dashevo.platform.Platform
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.timerTask

class BlockchainIdentity {

    var platform: Platform
    var profiles: Profiles
    var params: NetworkParameters

    private constructor(platform: Platform) {
        this.params = platform.params
        this.platform = platform
        profiles = Profiles(platform)
    }

    companion object {
        const val BLOCKCHAIN_USERNAME_SALT = "BLOCKCHAIN_USERNAME_SALT"
        const val BLOCKCHAIN_USERNAME_STATUS = "BLOCKCHAIN_USERNAME_STATUS"
        const val BLOCKCHAIN_USERNAME_UNIQUE = "BLOCKCHAIN_USERNAME_UNIQUE"

        private val log = LoggerFactory.getLogger(BlockchainIdentity::class.java)
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
    var uniqueIdentifier: Identifier = Identifier.from(Sha256Hash.ZERO_HASH)
        get() {
            if (field == Identifier.from(Sha256Hash.ZERO_HASH)) {
                field = Identifier.from(uniqueId)
            }
            return field
        }

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

    var accountLabel: String = "Default Account"
    var account: Int = 0

    val registrationFundingAddress: Address
        get() = Address.fromKey(wallet!!.params, registrationFundingPrivateKey)

    //var dashpayBioString: String

    lateinit var registrationStatus: RegistrationStatus

    lateinit var usernameSalts: MutableMap<String, ByteArray>

    var registered: Boolean = false

    var creditBalance: Coin = Coin.ZERO

    var activeKeyCount: Int = 0

    var totalKeyCount: Int = 0

    var keysCreated: Long = 0

    lateinit var keyInfo: MutableMap<Long, MutableMap<String, Any>>
    var currentMainKeyIndex: Int = 0
    var currentMainKeyType: IdentityPublicKey.TYPES = IdentityPublicKey.TYPES.ECDSA_SECP256K1
    var creditFundingTransaction: CreditFundingTransaction? = null
    lateinit var registrationFundingPrivateKey: ECKey

    // profile
    var lastProfileDocument: Document? = null



    constructor(platform: Platform, uniqueId: Sha256Hash) : this(platform) {
        Preconditions.checkArgument(uniqueId != Sha256Hash.ZERO_HASH, "uniqueId must not be zero");
        this.uniqueId = uniqueId
        this.isLocal = false
        this.keysCreated = 0
        this.currentMainKeyIndex = 0
        this.currentMainKeyType = IdentityPublicKey.TYPES.ECDSA_SECP256K1
        this.usernameStatuses = HashMap()
        this.keyInfo = HashMap()
        this.registrationStatus = RegistrationStatus.REGISTERED
    }

    constructor(platform: Platform, index: Int, wallet: Wallet) : this(
        platform
    ) {
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
    }

    constructor(
        platform: Platform,
        index: Int,
        lockedOutpoint: TransactionOutPoint,
        wallet: Wallet
    ) :
            this(platform, index, wallet) {
        Preconditions.checkArgument(lockedOutpoint.hash != Sha256Hash.ZERO_HASH, "utxo must not be null");
        this.lockedOutpoint = lockedOutpoint;
        this.uniqueId = Sha256Hash.twiceOf(lockedOutpoint.bitcoinSerialize())
    }

    constructor(
        platform: Platform,
        transaction: CreditFundingTransaction,
        wallet: Wallet,
        registeredIdentity: Identity? = null
    ) :
            this(platform, transaction.usedDerivationPathIndex, transaction.lockedOutpoint, wallet) {
        Preconditions.checkArgument(!transaction.creditBurnPublicKey.isPubKeyOnly || transaction.creditBurnPublicKey.isEncrypted)
        creditFundingTransaction = transaction
        registrationFundingPrivateKey = transaction.creditBurnPublicKey

        //see if the identity is registered.
        try {
            identity = if (registeredIdentity != null) {
                registeredIdentity
            } else {
                platform.identities.get(uniqueIdString)
            }
            registrationStatus = if (identity != null)
                RegistrationStatus.REGISTERED
            else RegistrationStatus.UNKNOWN
        } catch (x: Exception) {
            // swallow and leave the status as unknown
            registrationStatus = RegistrationStatus.UNKNOWN
        }
    }

    constructor(
        platform: Platform,
        transaction: CreditFundingTransaction,
        usernameStatus: MutableMap<String, Any>,
        wallet: Wallet
    ) :
            this(platform, transaction, wallet) {
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
        platform: Platform,
        index: Int,
        transaction: CreditFundingTransaction,
        usernameStatus: MutableMap<String, Any>,
        credits: Coin,
        registrationStatus: RegistrationStatus,
        wallet: Wallet
    ) :
            this(platform, transaction, usernameStatus, wallet) {
        creditBalance = credits;
        this.registrationStatus = registrationStatus;
    }


    // MARK: - Full Registration agglomerate

    fun createCreditFundingTransaction(credits: Coin, keyParameter: KeyParameter?): CreditFundingTransaction {
        Preconditions.checkState(creditFundingTransaction == null, "The credit funding transaction must not exist")
        Preconditions.checkState(
            registrationStatus == RegistrationStatus.UNKNOWN,
            "The identity must not be registered"
        )
        return createFundingTransaction(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING, credits, keyParameter)
    }

    fun createTopupFundingTransaction(credits: Coin, keyParameter: KeyParameter?): CreditFundingTransaction {
        return createFundingTransaction(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_TOPUP, credits, keyParameter)
    }

    fun createInviteFundingTransaction(credits: Coin, keyParameter: KeyParameter?): CreditFundingTransaction {
        return createFundingTransaction(AuthenticationKeyChain.KeyChainType.INVITATION_FUNDING, credits, keyParameter)
    }

    private fun createFundingTransaction(type: AuthenticationKeyChain.KeyChainType,
                                         credits: Coin, keyParameter: KeyParameter?): CreditFundingTransaction {
        Preconditions.checkArgument(if (wallet!!.isEncrypted) keyParameter != null else true)
        val privateKey = wallet!!.currentAuthenticationKey(type)
        val request = SendRequest.creditFundingTransaction(wallet!!.params, privateKey, credits)
        request.aesKey = keyParameter
        request.coinSelector = ZeroConfCoinSelector.get()
        return wallet!!.sendCoinsOffline(request) as CreditFundingTransaction
    }

    fun initializeCreditFundingTransaction(creditFundingTransaction: CreditFundingTransaction) {
        this.creditFundingTransaction = creditFundingTransaction
        registrationStatus = RegistrationStatus.NOT_REGISTERED
    }

    fun registerIdentity(keyParameter: KeyParameter?) {
        Preconditions.checkState(
            registrationStatus != RegistrationStatus.REGISTERED,
            "The identity must not be registered"
        )
        Preconditions.checkState(creditFundingTransaction != null, "The credit funding transaction must exist")

        var identityPrivateKey = privateKeyAtIndex(0, IdentityPublicKey.TYPES.ECDSA_SECP256K1)
        val identityPublicKey =
            IdentityPublicKey(0, IdentityPublicKey.TYPES.ECDSA_SECP256K1, identityPrivateKey!!.pubKey)
        val identityPublicKeys = listOf(identityPublicKey)

        val signingKey = maybeDecryptKey(creditFundingTransaction!!.creditBurnPublicKey, keyParameter)

        var instantLock: InstantSendLock? =
            wallet!!.context.instantSendManager.getInstantSendLockByTxId(creditFundingTransaction!!.txId)

        if (instantLock == null) {
            instantLock = creditFundingTransaction!!.confidence?.instantSendlock
                ?: throw InvalidIdentityAssetLockProofError("instantLock == null")
        }
        platform.identities.register(
            creditFundingTransaction!!.outputIndex,
            creditFundingTransaction!!,
            instantLock!!,
            signingKey!!,
            identityPublicKeys
        )

        registrationStatus = RegistrationStatus.REGISTERED

        finalizeIdentityRegistration(creditFundingTransaction!!)
    }

    fun recoverIdentity(creditFundingTransaction: CreditFundingTransaction): Boolean {
        Preconditions.checkState(
            registrationStatus == RegistrationStatus.UNKNOWN,
            "The identity must not be registered"
        )
        Preconditions.checkState(creditFundingTransaction != null, "The credit funding transaction must exist")

        identity =
            platform.identities.get(creditFundingTransaction.creditBurnIdentityIdentifier.toStringBase58())
                ?: return false

        registrationStatus = RegistrationStatus.REGISTERED

        finalizeIdentityRegistration(creditFundingTransaction)

        return true
    }

    fun recoverIdentity(pubKeyId: ByteArray): Boolean {
        Preconditions.checkState(
            registrationStatus == RegistrationStatus.UNKNOWN,
            "The identity must not be registered"
        )

        identity = platform.identities.getByPublicKeyHash(pubKeyId) ?: return false

        registrationStatus = RegistrationStatus.REGISTERED

        finalizeIdentityRegistration(identity!!.id)

        return true
    }

    private fun finalizeIdentityRegistration(identityId: Identifier) {
        this.registrationFundingPrivateKey =
            wallet!!.currentAuthenticationKey(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING)
        val creditBurnIdentifier = Sha256Hash.wrap(identityId.toBuffer())
        finalizeIdentityRegistration(creditBurnIdentifier)
    }

    private fun finalizeIdentityRegistration(fundingTransaction: CreditFundingTransaction) {
        this.creditFundingTransaction = fundingTransaction;
        this.registrationFundingPrivateKey = fundingTransaction.creditBurnPublicKey
        this.lockedOutpoint = fundingTransaction.lockedOutpoint;
        finalizeIdentityRegistration(fundingTransaction.creditBurnIdentityIdentifier);
    }

    private fun finalizeIdentityRegistration(uniqueId: Sha256Hash) {
        this.uniqueId = uniqueId
        finalizeIdentityRegistration()
    }

    private fun finalizeIdentityRegistration() {
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
    fun registerPreorderedSaltedDomainHashesForUsernames(usernames: List<String>, keyParameter: KeyParameter?) {
        val transition = createPreorderTransition(usernames)
        if (transition == null) {
            return;
        }
        signStateTransition(transition, keyParameter)

        platform.client.broadcastStateTransition(transition,
            retryCallback = DefaultBroadcastRetryCallback(platform.stateRepository, retryContractIds = platform.apps.map { it.value.contractId })
        )

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

    fun registerUsernameDomainsForUsernames(usernames: List<String>, keyParameter: KeyParameter?) {
        val transition = createDomainTransition(usernames)
        if (transition == null) {
            return
        }
        signStateTransition(transition!!, keyParameter)

        platform.client.broadcastStateTransition(transition,
            retryCallback = platform.broadcastRetryCallback
        )

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

    /**
     * Recover all usernames and preorder data associated with the identity
     */
    fun recoverUsernames() {
        Preconditions.checkState(
            registrationStatus == RegistrationStatus.REGISTERED,
            "Identity must be registered before recovering usernames"
        )

        val nameDocuments = arrayListOf<Document>()
        nameDocuments.addAll(platform.names.getByOwnerId(uniqueIdentifier))
        nameDocuments.addAll(platform.names.getByUserIdAlias(uniqueIdentifier))
        val usernames = ArrayList<String>()

        for (nameDocument in nameDocuments) {
            val username = nameDocument.data["normalizedLabel"] as String
            var usernameStatusDictionary = HashMap<String, Any>()
            usernameStatusDictionary[BLOCKCHAIN_USERNAME_STATUS] = UsernameStatus.CONFIRMED
            usernameStatusDictionary[BLOCKCHAIN_USERNAME_UNIQUE] = Names.isUniqueIdentity(nameDocument)
            usernameStatuses[username] = usernameStatusDictionary
            usernameSalts[username] = nameDocument.data["preorderSalt"] as ByteArray
            usernameStatusDictionary[BLOCKCHAIN_USERNAME_SALT] = usernameSalts[username] as ByteArray
            usernames.add(username)
        }
        currentUsername = usernames.firstOrNull()
        saveUsernames(usernames, UsernameStatus.CONFIRMED)
    }

    //

    // MARK: Username Helpers

    fun saltForUsername(username: String, saveSalt: Boolean): ByteArray {
        var salt: ByteArray
        if (statusOfUsername(username) == UsernameStatus.INITIAL || !(usernameSalts.containsKey(username))) {
            salt = ECKey().privKeyBytes
            usernameSalts[username] = salt
            if (saveSalt) {
                saveUsername(username, statusOfUsername(username), salt, true)
            }
        } else {
            salt = usernameSalts[username]!!
        }
        return salt
    }

    fun saltedDomainHashesForUsernames(usernames: List<String>): MutableMap<String, ByteArray> {
        val mSaltedDomainHashes = HashMap<String, ByteArray>()
        for (unregisteredUsername in usernames) {
            val salt = saltForUsername(unregisteredUsername, true)
            val fullUsername = if (unregisteredUsername.contains(".")) {
                unregisteredUsername
            } else {
                unregisteredUsername + "." + Names.DEFAULT_PARENT_DOMAIN
            }
            val saltedDomainHashData = platform.names.getSaltedDomainHashBytes(salt, fullUsername)
            mSaltedDomainHashes[unregisteredUsername] = saltedDomainHashData
            usernameSalts[unregisteredUsername] = salt //is this required?
        }
        return mSaltedDomainHashes
    }

    // MARK: Documents

    fun createPreorderDocuments(unregisteredUsernames: List<String>): List<Document> {
        val usernamePreorderDocuments = ArrayList<Document>()
        for (saltedDomainHash in saltedDomainHashesForUsernames(unregisteredUsernames).values) {
            val document = platform.names.createPreorderDocument(Sha256Hash.wrap(saltedDomainHash), identity!!)
            usernamePreorderDocuments.add(document)
        }
        return usernamePreorderDocuments;
    }

    fun createDomainDocuments(unregisteredUsernames: List<String>): List<Document> {
        val usernameDomainDocuments = ArrayList<Document>()
        for (username in saltedDomainHashesForUsernames(unregisteredUsernames).keys) {
            val isUniqueIdentity =
                usernameDomainDocuments.isEmpty() && getUsernamesWithStatus(UsernameStatus.CONFIRMED).isEmpty()
            val document =
                platform.names.createDomainDocument(identity!!, username, usernameSalts[username]!!, isUniqueIdentity)
            usernameDomainDocuments.add(document)
        }
        return usernameDomainDocuments
    }

    // MARK: Transitions

    fun createPreorderTransition(unregisteredUsernames: List<String>): DocumentsBatchTransition? {
        val usernamePreorderDocuments = createPreorderDocuments(unregisteredUsernames)
        if (usernamePreorderDocuments.isEmpty()) return null
        val transitionMap = hashMapOf<String, List<Document>?>(
            "create" to usernamePreorderDocuments
        )
        return platform.dpp.document.createStateTransition(transitionMap);
    }

    fun createDomainTransition(unregisteredUsernames: List<String>): DocumentsBatchTransition? {
        val usernameDomainDocuments = createDomainDocuments(unregisteredUsernames)
        if (usernameDomainDocuments.isEmpty()) return null
        val transitionMap = hashMapOf<String, List<Document>?>(
            "create" to usernameDomainDocuments
        )
        return platform.dpp.document.createStateTransition(transitionMap);
    }

    // MARK: Usernames


    fun addUsername(username: String, status: UsernameStatus, save: Boolean) {
        val map = HashMap<String, UsernameStatus>()
        map[BLOCKCHAIN_USERNAME_STATUS] = UsernameStatus.INITIAL
        usernameStatuses[username] = map
        currentUsername = username

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

    fun getUniqueUsername(): String {
        for (username in usernameStatuses.keys) {
            val usernameInfo = usernameStatuses[username] as MutableMap<String, Any?>
            val isUnique = usernameInfo[BLOCKCHAIN_USERNAME_UNIQUE] as Boolean
            if (isUnique) {
                return username
            }
        }
        throw IllegalStateException("There is no unique username")
    }

    fun getAliasList(): List<String> {
        val usernames = arrayListOf<String>()
        for (username in usernameStatuses.keys) {
            val usernameInfo = usernameStatuses[username] as MutableMap<String, Any?>
            val isUnique = usernameInfo[BLOCKCHAIN_USERNAME_UNIQUE] as Boolean

            if (!isUnique) {
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
        transition: StateTransitionIdentitySigned,
        keyIndex: Int,
        signingAlgorithm: IdentityPublicKey.TYPES,
        keyParameter: KeyParameter? = null
    ) {

        var privateKey = maybeDecryptKey(keyIndex, signingAlgorithm, keyParameter)
        Preconditions.checkState(privateKey != null, "The private key should exist");

        val identityPublicKey = IdentityPublicKey(keyIndex, signingAlgorithm, privateKey!!.pubKey)
        transition.sign(identityPublicKey, privateKey.privateKeyAsHex)
    }

    /**
     * Decrypts the key at the keyIndex if necessary using the keyParameter
     * @param keyIndex Int
     * @param signingAlgorithm TYPES
     * @param keyParameter KeyParameter?
     * @return ECKey?
     */
    private fun maybeDecryptKey(
        keyIndex: Int,
        signingAlgorithm: IdentityPublicKey.TYPES,
        keyParameter: KeyParameter?
    ): ECKey? {
        var privateKey = privateKeyAtIndex(keyIndex, signingAlgorithm)
        if (privateKey!!.isEncrypted)
            privateKey = privateKey.decrypt(wallet!!.keyCrypter, keyParameter)
        return privateKey
    }

    private fun maybeDecryptKey(
        encryptedPrivateKey: ECKey,
        keyParameter: KeyParameter?
    ): ECKey? {
        var privateKey = encryptedPrivateKey
        if (encryptedPrivateKey!!.isEncrypted)
            privateKey = encryptedPrivateKey.decrypt(wallet!!.keyCrypter, keyParameter)
        return privateKey
    }

    fun signStateTransition(transition: StateTransitionIdentitySigned, keyParameter: KeyParameter?) {
        /*if (keysCreated == 0) {
            uint32_t index
            [self createNewKeyOfType:DEFAULT_SIGNING_ALGORITHM returnIndex:&index];
        }*/
        return signStateTransition(
            transition,
            identity!!.publicKeys[0].id/* currentMainKeyIndex*/,
            currentMainKeyType,
            keyParameter
        )
    }

    fun derivationPathForType(type: IdentityPublicKey.TYPES): ImmutableList<ChildNumber>? {
        if (isLocal) {
            if (type == IdentityPublicKey.TYPES.ECDSA_SECP256K1) {
                return DerivationPathFactory(wallet!!.params).blockchainIdentityECDSADerivationPath()
            } else if (type == IdentityPublicKey.TYPES.BLS12_381) {
                return DerivationPathFactory(wallet!!.params).blockchainIdentityBLSDerivationPath()
            }
        }
        return null
    }

    private fun privateKeyAtIndex(index: Int, type: IdentityPublicKey.TYPES): ECKey? {
        Preconditions.checkState(isLocal, "this must own a wallet")

        when (type) {

            IdentityPublicKey.TYPES.ECDSA_SECP256K1 -> {
                val authenticationChain = wallet!!.blockchainIdentityKeyChain
                val key = authenticationChain.watchingKey
                return key
            }
            else -> throw IllegalArgumentException("$type is not supported")
        }
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
        // TODO: Do we actually need this method
    }

    fun saveNewUsername(username: String, status: UsernameStatus) {
        // TODO: Do we actually need this method
        saveUsername(username, status, null, true)
    }

    /**
     * This method will determine if the associated identity exists by making a platform query
     * the specified number of times with the specified delay between attempts.  If the identity
     * exists, then the onSuccess method of the callback is invoked.  Otherwise the onTimeout method
     * is invoked.
     * @param retryCount Int The number of times to try to determine if an identity exists
     * @param delayMillis Long The delay between attempts to determine if an identity exists
     * @param retryDelayType RetryDelayType
     * @param callback RegisterIdentityCallback
     */
    fun watchIdentity(
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
                    watchIdentity(retryCount - 1, nextDelay, retryDelayType, callback)
                }, delayMillis)
            } else callback.onTimeout()
        }
        //throw exception or return false
    }

    suspend fun watchIdentity(
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
                delay(nextDelay)
                return watchIdentity(retryCount - 1, nextDelay, retryDelayType)
            }
        }
        return null
    }

    /**
     * This method will determine if the given preordered names exist by making a platform query
     * the specified number of times with the specified delay between attempts.  If the preorders
     * exist, then the onSuccess method of the callback is invoked.  Otherwise the onTimeout method
     * is invoked.
     * @param saltedDomainHashes Map<String, ByteArray> Map of usernames and salted domain hashes
     * @param retryCount Int
     * @param delayMillis Long
     * @param retryDelayType RetryDelayType
     * @param callback RegisterPreorderCallback
     */
    fun watchPreorder(
        saltedDomainHashes: Map<String, ByteArray>,
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType,
        callback: RegisterPreorderCallback
    ) {

        val query = DocumentQuery.Builder()
            .where(
                listOf("saltedDomainHash",
                    "in",
                    saltedDomainHashes.map {
                        it.value
                    }
                )
            ).build()
        val preorderDocuments = platform.documents.get(Names.DPNS_PREORDER_DOCUMENT, query)

        if (preorderDocuments != null && preorderDocuments.isNotEmpty()) {
            val usernamesLeft = HashMap(saltedDomainHashes)
            for (username in saltedDomainHashes.keys) {
                val saltedDomainHashData = saltedDomainHashes[username] as ByteArray
                for (preorderDocument in preorderDocuments) {
                    if ((preorderDocument.data["saltedDomainHash"] as ByteArray).contentEquals(saltedDomainHashData)) {
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
                    watchPreorder(
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
                    watchPreorder(
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
    }

    suspend fun watchPreorder(
        saltedDomainHashes: Map<String, ByteArray>,
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType
    ): Pair<Boolean, List<String>> {

        val query = DocumentQuery.Builder()
            .where(
                listOf(
                    "saltedDomainHash",
                    "in",
                    saltedDomainHashes.map { it.value }
                )
            ).build()

        val preorderDocuments = platform.documents.get(Names.DPNS_PREORDER_DOCUMENT, query)

        if (preorderDocuments != null && preorderDocuments.isNotEmpty()) {
            val usernamesLeft = HashMap(saltedDomainHashes)
            for (username in saltedDomainHashes.keys) {
                val saltedDomainHashData = saltedDomainHashes[username] as ByteArray
                for (preorderDocument in preorderDocuments) {
                    if ((preorderDocument.data["saltedDomainHash"] as ByteArray).contentEquals(saltedDomainHashData)) {
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

                val nextDelay = delayMillis * when (retryDelayType) {
                    RetryDelayType.SLOW20 -> 5 / 4
                    RetryDelayType.SLOW50 -> 3 / 2
                    else -> 1
                }
                delay(nextDelay)
                return watchPreorder(
                    saltedDomainHashesLeft,
                    retryCount - 1,
                    nextDelay,
                    retryDelayType
                )

            } else if (usernamesLeft.size > 0) {
                val saltedDomainHashesLeft = saltedDomainHashes.filter { usernamesLeft.containsKey(it.key) }
                return Pair(false, saltedDomainHashesLeft.keys.toList())
            } else {
                return Pair(true, saltedDomainHashes.keys.toList())
            }
        } else {
            if (retryCount > 0) {
                val nextDelay = delayMillis * when (retryDelayType) {
                    RetryDelayType.SLOW20 -> 5 / 4
                    RetryDelayType.SLOW50 -> 3 / 2
                    else -> 1
                }
                delay(nextDelay)
                return watchPreorder(
                    saltedDomainHashes,
                    retryCount - 1,
                    nextDelay,
                    retryDelayType
                )
            } else {
                return Pair(false, saltedDomainHashes.keys.toList())
            }
        }
        //throw exception or return false
        return Pair(false, saltedDomainHashes.keys.toList())
    }

    /**
     * This method will determine if the given usernames exist by making a platform query
     * the specified number of times with the specified delay between attempts.  If the usernames
     * exist, then the onSuccess method of the callback is invoked.  Otherwise the onTimeout method
     * is invoked.
     * @param usernames List<String>
     * @param retryCount Int
     * @param delayMillis Long
     * @param retryDelayType RetryDelayType
     * @param callback RegisterNameCallback
     */
    fun watchUsernames(
        usernames: List<String>,
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType,
        callback: RegisterNameCallback
    ) {

        val query = DocumentQuery.Builder()
            .where("normalizedParentDomainName", "==", Names.DEFAULT_PARENT_DOMAIN)
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
                        usernameStatus[BLOCKCHAIN_USERNAME_UNIQUE] = Names.isUniqueIdentity(nameDocument)
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
                    watchUsernames(usernamesLeft, retryCount - 1, nextDelay, retryDelayType, callback)
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
                    watchUsernames(usernames, retryCount - 1, nextDelay, retryDelayType, callback)
                }, delayMillis)
            } else {
                callback.onTimeout(usernames)
            }
        }
    }

    suspend fun watchUsernames(
        usernames: List<String>,
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType
    ): Pair<Boolean, List<String>> {

        val query = DocumentQuery.Builder()
            .where("normalizedParentDomainName", "==", Names.DEFAULT_PARENT_DOMAIN)
            .where(listOf("normalizedLabel", "in", usernames.map { "${it.toLowerCase()}" })).build()
        val nameDocuments = platform.documents.get(Names.DPNS_DOMAIN_DOCUMENT, query)

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
                        usernameStatus[BLOCKCHAIN_USERNAME_UNIQUE] = Names.isUniqueIdentity(nameDocument)
                        usernameStatuses[username] = usernameStatus
                        saveUsername(username, UsernameStatus.CONFIRMED, null, true)
                        usernamesLeft.remove(username)
                    }
                }
            }
            if (usernamesLeft.size > 0 && retryCount > 0) {
                val nextDelay = delayMillis * when (retryDelayType) {
                    RetryDelayType.SLOW20 -> 5 / 4
                    RetryDelayType.SLOW50 -> 3 / 2
                    else -> 1
                }
                delay(nextDelay)
                return watchUsernames(usernamesLeft, retryCount - 1, nextDelay, retryDelayType)
            } else if (usernamesLeft.size > 0) {
                return Pair(false, usernamesLeft)
            } else {
                return Pair(true, usernames)
            }
        } else {
            if (retryCount > 0) {
                val nextDelay = delayMillis * when (retryDelayType) {
                    RetryDelayType.SLOW20 -> 5 / 4
                    RetryDelayType.SLOW50 -> 3 / 2
                    else -> 1
                }
                delay(nextDelay)
                return watchUsernames(usernames, retryCount - 1, nextDelay, retryDelayType)
            } else {
                return Pair(false, usernames)
            }
        }
        //throw exception or return false
        return Pair(false, usernames)
    }

    // DashPay Profile methods
    private fun createProfileTransition(
        displayName: String?,
        publicMessage: String?,
        avatarUrl: String? = null,
        avatarHash: ByteArray? = null,
        avatarFingerprint: ByteArray?
    ): DocumentsBatchTransition {
        val profileDocument = profiles.createProfileDocument(displayName, publicMessage, avatarUrl, avatarHash, avatarFingerprint, identity!!)
        lastProfileDocument = profileDocument
        val transitionMap = hashMapOf<String, List<Document>?>(
            "create" to listOf(profileDocument)
        )
        return platform.dpp.document.createStateTransition(transitionMap)
    }

    fun registerProfile(displayName: String?, publicMessage: String?, avatarUrl: String?, avatarHash: ByteArray? = null, avatarFingerprint: ByteArray?, keyParameter: KeyParameter?) {
        val transition = createProfileTransition(displayName, publicMessage, avatarUrl, avatarHash, avatarFingerprint)

        signStateTransition(transition!!, keyParameter)

        platform.client.broadcastStateTransition(transition,
            retryCallback = platform.broadcastRetryCallback
        )
    }

    private fun replaceProfileTransition(
        displayName: String?,
        publicMessage: String?,
        avatarUrl: String? = null,
        avatarHash: ByteArray? = null,
        avatarFingerprint: ByteArray?
    ): DocumentsBatchTransition {

        // first obtain the current document
        val currentProfile = getProfileFromPlatform()

        // change all of the document fields
        val profileData = hashMapOf<String, Any?>()
        profileData.putAll(currentProfile!!.toJSON())
        profileData["displayName"] = displayName
        profileData["publicMessage"] = publicMessage
        profileData["avatarUrl"] = avatarUrl
        profileData["avatarHash"] = avatarHash
        profileData["avatarFingerprint"] = avatarFingerprint

        val profileDocument = Document(profileData, platform.apps["dashpay"]!!.dataContract!!)
        // a replace operation must set updatedAt
        profileDocument.updatedAt = Date().time
        lastProfileDocument = profileDocument

        val transitionMap = hashMapOf<String, List<Document>?>(
            "replace" to listOf(profileDocument)
        )
        return platform.dpp.document.createStateTransition(transitionMap)
    }

    fun updateProfile(displayName: String?, publicMessage: String?, avatarUrl: String?, avatarHash: ByteArray? = null, avatarFingerprint: ByteArray?, keyParameter: KeyParameter?) {
        val transition = replaceProfileTransition(displayName, publicMessage, avatarUrl, avatarHash, avatarFingerprint)

        signStateTransition(transition!!, keyParameter)

        platform.client.broadcastStateTransition(transition,
            retryCallback = platform.broadcastRetryCallback
        )
    }

    fun getProfile(): Document? {
        return profiles.get(uniqueIdString)
    }

    /**
     * Obtains the most recent profile from the network
     */
    fun getProfileFromPlatform(): Document? {
        return profiles.get(uniqueIdString)
    }

    suspend fun watchProfile(
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType
    ): Document? {

        val updatedAt = if (lastProfileDocument?.updatedAt != null) {
            lastProfileDocument!!.updatedAt!!
        } else {
            -1
        }

        val profileResult = profiles.get(uniqueIdentifier, updatedAt)

        if (profileResult != null) {
            save()
            return profileResult
        } else {
            if (retryCount > 0) {
                val nextDelay = delayMillis * when (retryDelayType) {
                    RetryDelayType.SLOW20 -> 5 / 4
                    RetryDelayType.SLOW50 -> 3 / 2
                    else -> 1
                }
                delay(nextDelay)
                return watchProfile(retryCount - 1, nextDelay, retryDelayType)
            }
        }
        return null
    }

    fun watchProfile(
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType,
        callback: UpdateProfileCallback
    ) {
        val updatedAt = if (lastProfileDocument?.updatedAt != null) {
            lastProfileDocument!!.updatedAt!!
        } else {
            -1
        }

        val profileResult = profiles.get(uniqueIdentifier, updatedAt)

        if (profileResult != null) {
            save()
            callback.onComplete(uniqueIdString, profileResult)
        } else {
            if (retryCount > 0) {
                Timer("monitorUpdateProfileStatus", false).schedule(timerTask {
                    val nextDelay = delayMillis * when (retryDelayType) {
                        RetryDelayType.SLOW20 -> 5 / 4
                        RetryDelayType.SLOW50 -> 3 / 2
                        else -> 1
                    }
                    watchProfile(retryCount - 1, nextDelay, retryDelayType, callback)
                }, delayMillis)
            } else callback.onTimeout()
        }
    }

    // Contact Requests
    fun addContactToWallet(contactKeyChain: FriendKeyChain, encryptionKey: KeyParameter? = null) {
        when (contactKeyChain.type) {
            FriendKeyChain.KeyChainType.RECEIVING_CHAIN -> {
                wallet!!.run {
                    Preconditions.checkArgument(isEncrypted == (encryptionKey != null))
                    if (isEncrypted) {
                        val encryptedContactKeyChain = contactKeyChain.toEncrypted(keyCrypter, encryptionKey)
                        addReceivingFromFriendKeyChain(encryptedContactKeyChain)
                    } else {
                        addReceivingFromFriendKeyChain(contactKeyChain)
                    }
                }
            }
            FriendKeyChain.KeyChainType.SENDING_CHAIN -> wallet!!.addSendingToFriendKeyChain(contactKeyChain)
        }
    }

    fun maybeDecryptSeed(aesKey: KeyParameter?): DeterministicSeed {
        return wallet!!.run {
            if (isEncrypted) {
                keyChainSeed.decrypt(wallet!!.keyCrypter, "", aesKey)
            } else {
                keyChainSeed
            }
        }
    }

    fun getReceiveFromContactChain(contactIdentity: Identity, aesKey: KeyParameter?): FriendKeyChain {
        val seed = maybeDecryptSeed(aesKey)

        return FriendKeyChain(
            seed,
            null,
            FriendKeyChain.getRootPath(params),
            0,
            uniqueId,
            contactIdentity.id.toSha256Hash()
        )
    }

    fun getReceiveFromContactChain(contactIdentity: Identity, encryptedXpub: ByteArray, aesKey: KeyParameter?): FriendKeyChain {
        val seed = maybeDecryptSeed(aesKey)

        return FriendKeyChain(
            seed,
            null,
            FriendKeyChain.getRootPath(params),
            account,
            uniqueId,
            contactIdentity.id.toSha256Hash()
        )
    }

    fun getSendToContactChain(contactIdentity: Identity, xpub: ByteArray, accountReference: Int): FriendKeyChain {
        val contactIdentityPublicKey = contactIdentity.getPublicKeyById(index)
        return FriendKeyChain(
            params,
            xpub.toHexString(),
            EvolutionContact(uniqueId, account, contactIdentity.id.toSha256Hash(), accountReference)
        )
    }

    private fun padAccountLabel() : String {
        return if (accountLabel.length < 16) {
            accountLabel + " ".repeat(16 - accountLabel.length)
        } else {
            accountLabel
        }
    }

    fun encryptExtendedPublicKey(
        xpub: ByteArray,
        contactIdentity: Identity,
        index: Int,
        aesKey: KeyParameter?
    ): Pair<ByteArray, ByteArray> {
        val contactIdentityPublicKey = contactIdentity.getPublicKeyById(index)
            ?: throw IllegalArgumentException("index $index does not exist for $contactIdentity")

        val contactPublicKey = contactIdentityPublicKey!!.getKey()

        return encryptExtendedPublicKey(xpub, contactPublicKey, contactIdentityPublicKey.type, aesKey)
    }

    /**
     *
     * @param xpub ByteArray The serialized extended public key obtained from [DeterministicKeyChain.getWatchingKey().serialize]
     * @param contactPublicKey ECKey The public key of the identity
     * @param signingAlgorithm TYPES
     * @param aesKey KeyParameter? The decryption key to the encrypted wallet
     * @return ByteArray The encrypted extended public key
     */
    fun encryptExtendedPublicKey(
        xpub: ByteArray,
        contactPublicKey: ECKey,
        signingAlgorithm: IdentityPublicKey.TYPES,
        aesKey: KeyParameter?
    ): Pair<ByteArray, ByteArray> {
        val keyCrypter = KeyCrypterECDH()

        // first decrypt our identity key if necessary (currently uses the first key [0])
        val decryptedIdentityKey = maybeDecryptKey(identity!!.publicKeys[0].id, signingAlgorithm, aesKey)

        // derived the shared key (our private key + their public key)
        val encryptionKey = keyCrypter.deriveKey(decryptedIdentityKey, contactPublicKey)

        // encrypt
        val encryptedData = keyCrypter.encrypt(xpub, encryptionKey)

        // format as a single byte array
        val boas = ByteArrayOutputStream(encryptedData.initialisationVector.size + encryptedData.encryptedBytes.size)
        boas.write(encryptedData.initialisationVector)
        boas.write(encryptedData.encryptedBytes)

        // encrypt
        val encryptedAccountLabel = keyCrypter.encrypt(padAccountLabel().toByteArray(), encryptionKey)

        // format as a single byte array
        val accountLabelBoas =
            ByteArrayOutputStream(encryptedAccountLabel.initialisationVector.size + encryptedAccountLabel.encryptedBytes.size)
        accountLabelBoas.write(encryptedAccountLabel.initialisationVector)
        accountLabelBoas.write(encryptedAccountLabel.encryptedBytes)

        return Pair(boas.toByteArray(), accountLabelBoas.toByteArray())
    }

    fun decryptExtendedPublicKey(
        encryptedXpub: ByteArray,
        contactIdentity: Identity,
        contactKeyIndex: Int,
        keyIndex: Int,
        aesKey: KeyParameter?
    ): String {
        val contactIdentityPublicKey = contactIdentity.getPublicKeyById(contactKeyIndex)
            ?: throw IllegalArgumentException("index $contactKeyIndex does not exist for $contactIdentity")
        val contactPublicKey = contactIdentityPublicKey.getKey()

        return decryptExtendedPublicKey(
            encryptedXpub,
            contactPublicKey,
            contactIdentityPublicKey.type,
            keyIndex,
            aesKey
        )
    }

    /**
     *
     * @param encryptedXpub ByteArray
     * @param contactPublicKey ECKey
     * @param signingAlgorithm TYPES
     * @param keyParameter KeyParameter The decryption key to the encrypted wallet
     * @return DeterministicKey The extended public key without the derivation path
     */
    fun decryptExtendedPublicKey(
        encryptedXpub: ByteArray,
        contactPublicKey: ECKey,
        signingAlgorithm: IdentityPublicKey.TYPES,
        keyIndex: Int,
        keyParameter: KeyParameter?
    ): String {

        val keyCrypter = KeyCrypterECDH()

        //first decrypt our identity key if necessary (currently uses the first key [0])
        val decryptedIdentityKey =
            maybeDecryptKey(keyIndex, signingAlgorithm, keyParameter)

        // derive the shared key (our private key + their public key)
        val encryptionKey = keyCrypter.deriveKey(decryptedIdentityKey, contactPublicKey)

        // separate the encrypted data (IV + ciphertext) and then decrypt the extended public key
        val encryptedData =
            EncryptedData(encryptedXpub.copyOfRange(0, 16), encryptedXpub.copyOfRange(16, encryptedXpub.size))
        val decryptedData = keyCrypter.decrypt(encryptedData, encryptionKey)

        return DeterministicKey.deserializeContactPub(params, decryptedData).serializePubB58(params)
    }

    fun addContactPaymentKeyChain(contactIdentity: Identity, contactRequest: Document, encryptionKey: KeyParameter?) {

        val accountReference = if (contactRequest.data.containsKey("accountReference")) {
            contactRequest.data["accountReference"] as Int
        } else {
            0 //default account reference
        }

        val contact = EvolutionContact(uniqueId, account, contactIdentity.id.toSha256Hash(), accountReference)

        if (!wallet!!.hasSendingKeyChain(contact)) {

            val xpub = decryptExtendedPublicKey(
                contactRequest.data["encryptedPublicKey"] as ByteArray,
                contactIdentity,
                contactRequest.data["recipientKeyIndex"] as Int,
                contactRequest.data["senderKeyIndex"] as Int,
                encryptionKey
            )
            val contactKeyChain = FriendKeyChain(wallet!!.params, xpub, contact)
            addContactToWallet(contactKeyChain)
        }
    }

    fun addPaymentKeyChainFromContact(
        contactIdentity: Identity,
        contactRequest: Document,
        encryptionKey: KeyParameter?
    ): Boolean {
        val contact = EvolutionContact(uniqueId, account, contactIdentity.id.toSha256Hash(), -1)
        if (!wallet!!.hasReceivingKeyChain(contact)) {
            val encryptedXpub = HashUtils.byteArrayfromBase64orByteArray(contactRequest.data["encryptedPublicKey"]!!)
            val senderKeyIndex = contactRequest.data["senderKeyIndex"] as Int
            val recipientKeyIndex = contactRequest.data["recipientKeyIndex"] as Int
            val contactKeyChain = getReceiveFromContactChain(contactIdentity, encryptionKey)

            val serializedContactXpub = decryptExtendedPublicKey(encryptedXpub, contactIdentity, recipientKeyIndex, senderKeyIndex, encryptionKey)

            val ourContactXpub = contactKeyChain.watchingKey.serializeContactPub()
            val ourSerializedXpub = DeterministicKey.deserializeContactPub(params, ourContactXpub).serializePubB58(params)

            // check that this contactRequest is for the default account
            if (serializedContactXpub.contentEquals(ourSerializedXpub)) {
                addContactToWallet(contactKeyChain, encryptionKey)
                return true
            } else {
                log.warn("contactRequest does not match account 0")
            }
        }
        return false
    }

    fun getContactNextPaymentAddress(contactId: Identifier, accountReference: Int): Address {
        return wallet!!.currentAddress(
            EvolutionContact(uniqueIdString, account, contactId.toString(), accountReference),
            FriendKeyChain.KeyChainType.SENDING_CHAIN
        )
    }

    fun getNextPaymentAddressFromContact(contactId: Identifier): Address {
        return wallet!!.currentAddress(
            EvolutionContact(uniqueIdString, account, contactId.toString(), -1),
            FriendKeyChain.KeyChainType.RECEIVING_CHAIN
        )
    }

    fun getContactTransactions(identityId: Identifier, accountReference: Int): List<Transaction> {
        val contact = EvolutionContact(uniqueIdString, account, identityId.toString(), accountReference)
        return wallet!!.getTransactionsWithFriend(contact)
    }

    fun getContactForTransaction(tx: Transaction): String? {
        val contact = wallet!!.getFriendFromTransaction(tx) ?: return null
        return if (uniqueId == contact.evolutionUserId)
            contact.friendUserId.toStringBase58()
        else
            contact.evolutionUserId.toStringBase58()
    }


    fun getAccountReference(encryptionKey: KeyParameter?, fromIdentity: Identity): Int {
        val privateKey = maybeDecryptKey(0, IdentityPublicKey.TYPES.ECDSA_SECP256K1, encryptionKey)

        val receiveChain = getReceiveFromContactChain(fromIdentity, encryptionKey)

        val extendedPublicKey = receiveChain.watchingKey.dropPrivateBytes();

        val accountSecretKey = HDUtils.hmacSha256(privateKey!!.privKeyBytes, extendedPublicKey.serializeContactPub())

        val accountSecretKey28 = Sha256Hash.wrapReversed(accountSecretKey).toBigInteger().toInt() ushr 4

        val shortenedAccountBits = account and 0x0FFFFFFF

        val version = 0

        val versionBits: Int = (version shl 28)

        return versionBits or (accountSecretKey28 xor shortenedAccountBits)
    }

    fun getInvitationHistory() : Map<Identifier, Identity?> {
        val inviteTxs = wallet!!.identityFundingTransactions
        val listIds = inviteTxs.map { Identifier.from(it.creditBurnIdentityIdentifier) }

        return listIds.associateBy({ it }, { platform.identities.get(it) })
    }

    fun getInvitationString(cftx: CreditFundingTransaction, encryptionKey: KeyParameter?): String {
        val txid = cftx.txId

        val privateKey = maybeDecryptKey(cftx.creditBurnPublicKey, encryptionKey)
        val wif = privateKey?.getPrivateKeyEncoded(wallet!!.params)
        return "ivt:$txid:$wif"
    }
}