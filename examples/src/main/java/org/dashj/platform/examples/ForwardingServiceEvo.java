/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.dashj.platform.examples;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.evolution.CreditFundingTransaction;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.MasternodePeerDiscovery;
import org.bitcoinj.params.DevNetParams;
import org.bitcoinj.params.EvoNetParams;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.MobileDevNetParams;
import org.bitcoinj.params.PalinkaDevNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.quorums.InstantSendLock;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.AuthenticationKeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.dashj.platform.dashpay.BlockchainIdentity;
import org.dashj.platform.dashpay.RetryDelayType;
import org.dashj.platform.dashpay.callback.RegisterIdentityCallback;
import org.dashj.platform.dashpay.callback.RegisterNameCallback;
import org.dashj.platform.dashpay.callback.RegisterPreorderCallback;
import org.dashj.platform.dpp.StateRepository;
import org.dashj.platform.dpp.identifier.Identifier;
import org.dashevo.platform.Platform;
import org.dashj.platform.dapiclient.model.DocumentQuery;
import org.dashj.platform.dpp.StateRepository;
import org.dashj.platform.dpp.contract.DataContract;
import org.dashj.platform.dpp.document.Document;
import org.dashj.platform.dpp.identity.Identity;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Thread.sleep;

/**
 * ForwardingService demonstrates basic usage of the library. It sits on the network and when it receives coins, simply
 * sends them onwards to an address given on the command line.
 */
public class ForwardingServiceEvo {
    private static Address forwardingAddress;
    private static WalletAppKit kit;
    private static Platform platform;
    private static BlockchainIdentity blockchainIdentity;

    public static void main(String[] args) throws Exception {
        // This line makes the log output more compact and easily read, especially when using the JDK log adapter.
        BriefLogFormatter.initWithSilentBitcoinJ();
        if (args.length < 1) {
            System.err.println("Usage: address-to-send-back-to [regtest|testnet|evonet|palinka|devnet] [devnet-name] [devnet-sporkaddress] [devnet-port] [devnet-dnsseed...]");
            return;
        }

        // Figure out which network we should connect to. Each one gets its own set of files.
        NetworkParameters params;
        String filePrefix;
        String checkpoints = null;
        if (args.length > 1 && args[1].equals("testnet")) {
            params = TestNet3Params.get();
            filePrefix = "forwarding-service-testnet";
            checkpoints = "checkpoints-testnet.txt";
        } else if (args.length > 1 && args[1].equals("regtest")) {
            params = RegTestParams.get();
            filePrefix = "forwarding-service-regtest";
        } else if (args.length > 1 && args[1].equals("palinka")) {
            params = PalinkaDevNetParams.get();
            filePrefix = "forwarding-service-palinka";
        } else if (args.length > 1 && args[1].equals("mobile")) {
            params = MobileDevNetParams.get();
            filePrefix = "forwarding-service-mobile";
            platform = new Platform(params);
        } else if (args.length > 1 && args[1].equals("evonet")) {
            params = EvoNetParams.get();
            filePrefix = "forwarding-service-evonet";
            platform = new Platform(params);
        } else if( args.length > 6 && args[1].equals("devnet")) {
            String [] dnsSeeds = new String[args.length - 5];
            System.arraycopy(args, 5, dnsSeeds, 0, args.length - 5);
            params = DevNetParams.get(args[2], args[3], Integer.parseInt(args[4]), dnsSeeds);
            filePrefix = "forwarding-service-devnet";
        }else {
            params = MainNetParams.get();
            filePrefix = "forwarding-service";
            checkpoints = "checkpoints.txt";
        }
        // Parse the address given as the first parameter.
        forwardingAddress = Address.fromBase58(params, args[0]);

        System.out.println("Network: " + params.getId());
        System.out.println("Forwarding address: " + forwardingAddress);

        // Start up a basic app using a class that automates some boilerplate.
        kit = new WalletAppKit(params, new File("."), filePrefix) {
            @Override
            protected void onSetupCompleted() {
                if(!kit.wallet().hasAuthenticationKeyChains())
                    kit.wallet().initializeAuthenticationKeyChains(kit.wallet().getKeyChainSeed(), null);
                kit.setDiscovery(new MasternodePeerDiscovery(kit.wallet().getContext().masternodeListManager.getListAtChainTip()));
            }
        };

        if (params == RegTestParams.get()) {
            // Regression test mode is designed for testing and development only, so there's no public network for it.
            // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
            kit.connectToLocalHost();
        }

        if(checkpoints != null) {
            try {
                FileInputStream checkpointStream = new FileInputStream("./" + checkpoints);
                kit.setCheckpoints(checkpointStream);
            } catch (FileNotFoundException x) {
                //swallow
            }
        }

        // Download the block chain and wait until it's done.
        kit.startAsync();
        kit.awaitRunning();

        // We want to know when we receive money.
        kit.wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                // Runs in the dedicated "user thread" (see bitcoinj docs for more info on this).
                //
                // The transaction "tx" can either be pending, or included into a block (we didn't see the broadcast).
                Coin value = tx.getValueSentToMe(w);
                System.out.println("Received tx for " + value.toFriendlyString() + ": " + tx);
                System.out.println("Transaction will be forwarded after it confirms.");
                // Wait until it's made it into the block chain (may run immediately if it's already there).
                //
                // For this dummy app of course, we could just forward the unconfirmed transaction. If it were
                // to be double spent, no harm done. Wallet.allowSpendingUnconfirmedTransactions() would have to
                // be called in onSetupCompleted() above. But we don't do that here to demonstrate the more common
                // case of waiting for a block.
                Context.propagate(w.getContext());
                Futures.addCallback(tx.getConfidence().getDepthFuture(2), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        System.out.println("Confirmation received.");
                        forwardCoins(tx);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // This kind of future can't fail, just rethrow in case something weird happens.
                        throw new RuntimeException(t);
                    }
                }, MoreExecutors.directExecutor());

                Futures.addCallback(tx.getConfidence().getDepthFuture(1), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        System.out.println("Confirmation received.");
                        blockchainIdentity(tx);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // This kind of future can't fail, just rethrow in case something weird happens.
                        throw new RuntimeException(t);
                    }
                }, MoreExecutors.directExecutor());

                /*Futures.addCallback(tx.getConfidence().getDepthFuture(3), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        System.out.println("3 confirmations received. -- create user");
                        blockchainUser(tx);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // This kind of future can't fail, just rethrow in case something weird happens.
                        throw new RuntimeException(t);
                    }
                }, MoreExecutors.directExecutor());*/
            }
        });

        Address sendToAddress = Address.fromKey(params, kit.wallet().currentReceiveKey());
        System.out.println("Send coins to: " + sendToAddress);
        System.out.println("Waiting for coins to arrive. Press Ctrl-C to quit.");

        //get this:
        String id = "J2jWLKNWogVf1B8fdo6rxMeQgDWWi9aGd9JPcTHxNj7H";
        //apiClient client = new DapiClient(EvoNetParams.MASTERNODES[1], true);
        //client.getIdentity(id);
        //client.shutdown();

        //System.out.println(kit.wallet().toString(true, true, null, true, true, null)/*.getBlockchainIdentityKeyChain()*/);
        //System.out.println("devnet block:" + kit.wallet().getParams().getDevNetGenesisBlock().toString());
        List<CreditFundingTransaction> list = kit.wallet().getCreditFundingTransactions();
        for(CreditFundingTransaction tx : list) {
            System.out.println(tx.getTxId());
            String identityId = tx.getCreditBurnIdentityIdentifier().toStringBase58();
            System.out.println("  id: " + identityId);
            Identity identity = platform.getIdentities().get(identityId);
            if(identity != null) {
                System.out.println("  id json: " + identity.toJSON().toString());
                try {
                    DocumentQuery options = new DocumentQuery.Builder().where(Arrays.asList("$userId", "==", identityId)).build();
                    List<Document> documents = platform.getDocuments().get("dpns.domain", options);
                    if (documents != null & documents.size() > 0) {
                        System.out.println("  name: " + documents.get(0).getData().get("normalizedName"));
                    } else {
                        System.out.println("  no names found");
                    }
                } catch (Exception x) {
                    System.out.println("  no names found");
                }
            }
            BlockchainIdentity blockchainIdentity = new BlockchainIdentity(platform, tx, kit.wallet(), null);

            List<String> names = ImmutableList.of("test1", "test2");

            blockchainIdentity.watchUsernames(names, 10, 1000, RetryDelayType.LINEAR,
                    new RegisterNameCallback() {
                        @Override
                        public void onComplete(@NotNull List<String> uniqueId) {
                            System.out.println("names created and found");
                        }

                        @Override
                        public void onTimeout(@NotNull List<String> uniqueId) {
                            System.out.println("names were not created:");
                        }
                    }
            );

            System.out.println("blockchainIdentity: " + blockchainIdentity.getUniqueIdString());
        }

        System.out.println("------------------------------------\nNames found starting with hashengineering");
        DocumentQuery options = new DocumentQuery.Builder()
                .where(Arrays.asList("normalizedLabel", "startsWith", "hashengineering"))
                .where(Arrays.asList("normalizedParentDomainName", "==", "dash"))
                .build();
        try {
            List<Document> documents = platform.getDocuments().get("dpns.domain", options);
            for(Document document : documents) {
                System.out.println(document.getData().get("$userId") + "->  name: " + document.getData().get("normalizedLabel"));
                platform.getNames().get((String)document.getData().get("normalizedLabel"));
            }
            System.out.println(documents.size() + " names found");
        } catch(Exception e) {
            System.out.println(e);
        }



        try {
            sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {}
    }

    static CreditFundingTransaction lastTx = null;
    static BlockchainIdentity lastBlockchainIdentity = null;

    private static void forwardCoins(Transaction tx) {
        try {
            if(CreditFundingTransaction.isCreditFundingTransaction(tx))
                return;
            // Now send the coins onwards.
            SendRequest sendRequest = SendRequest.emptyWallet(forwardingAddress);
            Wallet.SendResult sendResult = kit.wallet().sendCoins(sendRequest);
            checkNotNull(sendResult);  // We should never try to send more coins than we have!
            System.out.println("Sending ...");
            // Register a callback that is invoked when the transaction has propagated across the network.
            // This shows a second style of registering ListenableFuture callbacks, it works when you don't
            // need access to the object the future returns.
            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                    System.out.println("Sent coins onwards! Transaction hash is " + sendResult.tx.getTxId());
                }
            }, MoreExecutors.directExecutor());

        } catch (KeyCrypterException | InsufficientMoneyException /*| InterruptedException*/ e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

    private static void registerIdentity() {
        //this is a base64 id, which is not used by dapi-client
        //lastIdentityId = platform.getIdentities().register(Identity.IdentityType.USER, lastTx);
        lastBlockchainIdentity.registerIdentity(null);
        //System.out.println("Identity created: " + lastIdentityId);
        //this is the base58 id
        lastIdentityId = lastTx.getCreditBurnIdentityIdentifier().toStringBase58();

        System.out.println("Identity created: " + lastTx.getCreditBurnIdentityIdentifier().toStringBase58());
            /*DashPlatformProtocol dpp = new DashPlatformProtocol(dataProvider);

            kit.wallet().getBlockchainIdentityFundingKeyChain().getKeyByPubKeyHash(lastTx.getCreditBurnPublicKeyId().getBytes());
            IdentityPublicKey identityPublicKey = new IdentityPublicKey(lastTx.getUsedDerivationPathIndex()+1,
                    IdentityPublicKey.TYPES.ECDSA_SECP256K1, Base64.toBase64String(lastTx.getCreditBurnPublicKey().getPubKey()), true);
            List<IdentityPublicKey> keyList = new ArrayList<>();
            keyList.add(identityPublicKey);
            Identity identity = dpp.identity.create(Base58.encode(lastTx.getCreditBurnIdentityIdentifier().getBytes()), Identity.IdentityType.USER,
                    keyList);
            IdentityCreateTransition st = new IdentityCreateTransition(Identity.IdentityType.USER,
                    lastTx.getLockedOutpoint().toStringBase64(), keyList, 0);

            st.sign(identityPublicKey, Utils.HEX.encode(lastTx.getCreditBurnPublicKey().getPrivKeyBytes()));

            DapiClient client = new DapiClient(EvoNetParams.MASTERNODES[1], true);
            client.applyStateTransition(st);
            client.shutdown();
            lastIdentityId = lastTx.getCreditBurnIdentityIdentifier().toStringBase58();
            System.out.println("Identity created: " + lastIdentityId);
*/
        //sleep(30*1000);
        lastBlockchainIdentity.watchIdentity(1, 1000, RetryDelayType.LINEAR,
                new RegisterIdentityCallback() {
                    @Override
                    public void onComplete(@NotNull String uniqueId) {
                        System.out.println("Identity created and found");
                        blockchainUser(lastTx);
                    }

                    @Override
                    public void onTimeout() {
                        System.out.println("Identity was not created or found.");
                    }
                }
        );
        //lastBlockchainIdentity.monitorForBlockchainIdentityWithRetryCount(30, 5000, BlockchainIdentity.RetryDelayType.LINEAR);
    }

    static Identity lastIdentity = null;
    static String lastIdentityId = null;

    private static void blockchainIdentity(Transaction tx) {
        try {
            // Now send the coins onwards.

            if(CreditFundingTransaction.isCreditFundingTransaction(tx))
                return;

            AuthenticationKeyChain blockchainIdentityFunding = kit.wallet().getBlockchainIdentityFundingKeyChain();
            DeterministicKey publicKey = blockchainIdentityFunding.freshAuthenticationKey();
            Coin fundingAmount = Coin.valueOf(40000);
            SendRequest sendRequest = SendRequest.creditFundingTransaction(kit.params(), publicKey, fundingAmount);
            ((CreditFundingTransaction)(sendRequest.tx)).setCreditBurnPublicKeyAndIndex(publicKey, publicKey.getChildNumber().num());
            Wallet.SendResult sendResult = kit.wallet().sendCoins(sendRequest);
            System.out.println("Sending Credit Funding Transaction...");
            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                    System.out.println("Blockchain Identity Funding Transaction hash is " + sendResult.tx.getTxId());
                    System.out.println(sendResult.tx.toString());
                    System.out.println("Blockchain Identity object Initialization" + sendResult.tx.getTxId());
                    blockchainIdentity = new BlockchainIdentity(platform, (CreditFundingTransaction)sendRequest.tx, kit.wallet(), null);
                }
            }, MoreExecutors.directExecutor());

            lastTx = (CreditFundingTransaction)sendResult.tx;
            lastBlockchainIdentity = new BlockchainIdentity(platform, lastTx, kit.wallet(), null);

            System.out.println("Creating identity");

            lastTx.getConfidence().addEventListener(new TransactionConfidence.Listener() {
                @Override
                public void onConfidenceChanged(TransactionConfidence confidence, ChangeReason reason) {
                    if (reason == ChangeReason.IX_TYPE && confidence.getIXType() != TransactionConfidence.IXType.IX_NONE) {
                        registerIdentity();
                    }
                }
            });

        } catch (KeyCrypterException | InsufficientMoneyException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

    private static void blockchainUser(Transaction tx) {
        try {
            // Now send the coins onwards.

            //if(!CreditFundingTransaction.isCreditFundingTransaction(tx))
            //    return;

            AuthenticationKeyChain blockchainIdentityFunding = kit.wallet().getBlockchainIdentityFundingKeyChain();
            ECKey publicKey = blockchainIdentityFunding.currentAuthenticationKey();
            //if(!Base64.decode(lastIdentity.getPublicKeys().get(0).getData()).equals(publicKey.getPubKey()))
            //    return;

            Identity identity = platform.getIdentities().get(lastIdentityId);
            if(identity != null)
                System.out.println("identity requested: " + identity.toJSON());
            else System.out.println("failed to get identity:" + lastIdentityId);

            String name = "hashengineering"+ new Random().nextInt(100);
            System.out.println("Registering name:" + name + " for identity: " + identity.getId());
            //platform.getNames().register2(name, identity,
            //        kit.wallet().getBlockchainIdentityFundingKeyChain().currentAuthenticationKey());

            lastBlockchainIdentity.addUsername(name + 1, true);
            lastBlockchainIdentity.addUsername(name + 2, true);
            lastBlockchainIdentity.addUsername(name + 3, true);

            List<String> set = lastBlockchainIdentity.getUnregisteredUsernames();
            lastBlockchainIdentity.registerPreorderedSaltedDomainHashesForUsernames(set, null);

            Map<String, byte[]> saltedDomainHashes = lastBlockchainIdentity.saltedDomainHashesForUsernames(set);
            lastBlockchainIdentity.watchPreorder(saltedDomainHashes, 10, 1000, RetryDelayType.LINEAR, new RegisterPreorderCallback() {
                @Override
                public void onComplete(@NotNull List<String> names) {
                    lastBlockchainIdentity.registerUsernameDomainsForUsernames(set, null);
                    lastBlockchainIdentity.watchUsernames(set, 10, 1000, RetryDelayType.LINEAR, new RegisterNameCallback() {
                        @Override
                        public void onComplete(@NotNull List<String> names) {
                            System.out.println("Name Register Complete: " + names);
                        }

                        @Override
                        public void onTimeout(@NotNull List<String> incompleteNames) {
                            System.out.println("Name Register Timeout: " + names);
                        }
                    });
                }

                @Override
                public void onTimeout(@NotNull List<String> incompleteNames) {

                }
            });
            //try {Thread.sleep(10000); } catch (InterruptedException x) {}

            for(String s : set) {
                Document nameDocument = platform.getNames().get(s);
                System.out.println("name: " + nameDocument.getData().get("normalizedLabel") +"->" + nameDocument.toJSON());
            }

            //Document nameDocument = platform.getNames().get(name);
            //System.out.println("name: " + nameDocument.getData().get("normalizedLabel") +"->" + nameDocument.toJSON());

        } catch (KeyCrypterException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

    static StateRepository dataProvider = new StateRepository() {


        @Override
        public boolean verifyInstantLock(@NotNull InstantSendLock instantSendLock) {
            return false;
        }

        @Override
        public void markAssetLockTransactionOutPointAsUsed(@NotNull byte[] bytes) {

        }

        @Override
        public boolean isAssetLockTransactionOutPointAlreadyUsed(@NotNull byte[] bytes) {
            return false;
        }

        @Override
        public void storeIdentityPublicKeyHashes(@NotNull Identifier identifier, @NotNull List<byte[]> list) {

        }

        @Override
        public void storeIdentity(@NotNull Identity identity) {

        }

        @Override
        public void storeDocument(@NotNull Document document) {

        }

        @Override
        public void storeDataContract(@NotNull DataContract dataContract) {

        }

        @Override
        public void removeDocument(@NotNull Identifier identifier, @NotNull String s, @NotNull Identifier identifier1) {

        }

        @NotNull
        @Override
        public Block fetchLatestPlatformBlockHeader() {
            return null;
        }

        @NotNull
        @Override
        public DataContract fetchDataContract(@NotNull Identifier s) {
            return null;
        }

        @NotNull
        @Override
        public List<Document> fetchDocuments(@NotNull Identifier s, @NotNull String s1, @NotNull Object o) {
            return new ArrayList<>();
        }

        @Override
        public Transaction fetchTransaction(@NotNull String s) {
            return null;
        }

        @Override
        public Identity fetchIdentity(@NotNull Identifier s) {
            return null;
        }
    };


}
