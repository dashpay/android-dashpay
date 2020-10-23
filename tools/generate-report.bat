@echo off

set WALLET_TOOL_OPTS=-Djava.library.path=%CD%
set WALLET_NETWORK=MOBILE
if not exist reports mkdir reports
call ./bin/network-activity mobile > reports\network.txt
echo Recovery Phrase, Username, Display Name, Username Created Date, Balance, First Outbound Tx Date, Outbound Username Tx, Inbound Username Tx, Outbound Contact Requests, Inbound Contact Requests, Contacts > reports/users.csv

for /F "tokens=*" %%A in (list.txt) do (
	echo processing recovery phrase: %%A
	if exist *.spvchain del mobile-devnet.spvchain
	if exist *.wallet del wallettool.wallet
	if exist *.dat del simplifiedmasternodelistmanager.dat

	call bin\wallet-tool create --seed="%%A" --wallet=wallettool.wallet --net=MOBILE --force
	echo Syncing wallet...
	call bin\wallet-tool sync --wallet=wallettool.wallet --net=MOBILE
	echo Exporting data about username...
	call bin\wallet-tool dump-dashpay --wallet=wallettool.wallet --net=MOBILE --outfile=reports\users.csv --csv )
