#! /bin/sh

rm *.wallet
rm *.dat
rm *.spvchain
./bin/wallet-tool create --wallet=mywallet.wallet --force  --net=TEST
addr=$(./bin/wallet-tool current-receive-addr --wallet=mywallet.wallet --net=TEST)
echo $addr is my address
dash-cli -rpcuser=x -rpcpassword=y -testnet sendtoaddress $addr 0.1
./bin/wallet-tool sync --wallet=mywallet.wallet --net=TEST --waitfor=BLOCK
./bin/wallet-tool create-username --wallet=mywallet.wallet --net=TEST --debuglog > mywallet.log 2> mywallet.log
