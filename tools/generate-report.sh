#!/bin/bash
export WALLET_TOOLS_OPTS="-Djava.library.path=."
export WALLET_NETWORK="MOBILE"
mkdir -p reports
./bin/network-activity mobile > reports/network.txt
echo Recovery Phrase, Username, Display Name, Username Created Date, Balance, First Outbound Tx Date, Outbound Username Tx, Inbound Username Tx, Outbound Contact Requests, Inbound Contact Requests, Contacts > reports/users.csv
while read w1 w2 w3 w4 w5 w6 w7 w8 w9 w10 w11 w12
do
  line="$w1 $w2 $w3 $w4 $w5 $w6 $w7 $w8 $w9 $w10 $w11 $w12"
  echo processing recovery phrase: "$line"
  rm -f mobile-devnet.spvchain
  rm -f wallettool.wallet
  rm -f simplifiedmasternodelistmanager.dat

  ./bin/wallet-tool create --seed="${line}" --wallet=wallettool.wallet --net=MOBILE --force
  ./bin/wallet-tool sync --wallet=wallettool.wallet --net=MOBILE
  ./bin/wallet-tool dump-dashpay --wallet=wallettool.wallet --net=MOBILE --outfile=reports/users.csv --csv
done < list.txt