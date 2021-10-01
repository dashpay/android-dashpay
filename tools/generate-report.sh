#!/bin/bash
export WALLET_TOOL_OPTS="-Djava.library.path=."
export WALLET_NETWORK="TEST"
mkdir -p reports
./bin/network-activity testnet > reports/network.txt
echo Recovery Phrase, Username, Display Name, Username Created Date, Balance, First Outbound Tx Date, Outbound Username Tx, Inbound Username Tx, Outbound Contact Requests, Inbound Contact Requests, Contacts, Contact Usernames, Invites Created, Invites Claimed > reports/users.csv
while read w1 w2 w3 w4 w5 w6 w7 w8 w9 w10 w11 w12
do
  line="$w1 $w2 $w3 $w4 $w5 $w6 $w7 $w8 $w9 $w10 $w11 $w12"
  echo processing recovery phrase: "$line"
  rm -f *.spvchain
  rm -f wallettool.wallet
  rm -f simplifiedmasternodelistmanager.dat

  echo "Create wallet with next recovery phrase"
  ./bin/wallet-tool create --seed="${line}" --wallet=wallettool.wallet --net=TEST --force --unixtime=1625107252
  echo "Sync wallet"
  ./bin/wallet-tool sync --wallet=wallettool.wallet --net=TEST
  echo "Obtain DashPay and wallet information"
  ./bin/wallet-tool dump-dashpay --wallet=wallettool.wallet --net=TEST --outfile=reports/users.csv --csv
done < list.txt