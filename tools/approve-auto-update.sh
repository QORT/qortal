#!/usr/bin/env bash

port=12391
if [ $# -gt 0 -a "$1" = "-t" ]; then
	port=62391
fi

printf "Searching for auto-update transactions to approve...\n";

tx=$( curl --silent --url "http://localhost:${port}/arbitrary/search?txGroupId=1&service=AUTO_UPDATE&confirmationStatus=CONFIRMED&limit=1&reverse=true" );
if fgrep --silent '"approvalStatus":"PENDING"' <<< "${tx}"; then
	true
else
	echo "Can't find any pending transactions"
	exit
fi

sig=$( perl -n -e 'print $1 if m/"signature":"(\w+)"/' <<< "${tx}" )
if [ -z "${sig}" ]; then
	printf "Can't find transaction signature in JSON:\n%s\n" "${tx}"
	exit
fi

printf "Found transaction %s\n" $sig;

printf "\nPaste your dev account private key:\n";
IFS=
read -s privkey
printf "\n"

# Convert to public key
pubkey=$( curl --silent --url "http://localhost:${port}/utils/publickey" --data @- <<< "${privkey}" );
if egrep -v --silent '^\w{44,46}$' <<< "${pubkey}"; then
	printf "Invalid response from API - was your private key correct?\n%s\n" "${pubkey}"
	exit
fi
printf "Your public key: %s\n" ${pubkey}

# Convert to address
address=$( curl --silent --url "http://localhost:${port}/addresses/convert/${pubkey}" );
printf "Your address: %s\n" ${address}

# Grab last reference
lastref=$( curl --silent --url "http://localhost:${port}/addresses/lastreference/{$address}" );
printf "Your last reference: %s\n" ${lastref}

# Build GROUP_APPROVAL transaction
timestamp=$( date +%s )000
tx_json=$( cat <<TX_END
{
  "timestamp": ${timestamp},
  "reference": "${lastref}",
  "fee": 0.01,
  "txGroupId": 0,
  "adminPublicKey": "${pubkey}",
  "pendingSignature": "${sig}",
  "approval": true
}
TX_END
)

raw_tx=$( curl --silent --header "Content-Type: application/json" --url "http://localhost:${port}/groups/approval" --data @- <<< "${tx_json}" )
if egrep -v --silent '^\w{100,}' <<< "${raw_tx}"; then
	printf "Building GROUP_APPROVAL transaction failed:\n%s\n" "${raw_tx}"
	exit
fi
printf "\nRaw approval tx:\n%s\n" ${raw_tx}

# sign
sign_json=$( cat <<SIGN_END
{
  "privateKey": "${privkey}",
  "transactionBytes": "${raw_tx}"
}
SIGN_END
)
signed_tx=$( curl --silent --header "Content-Type: application/json" --url "http://localhost:${port}/transactions/sign" --data @- <<< "${sign_json}" )
printf "\nSigned tx:\n%s\n" ${signed_tx}
if egrep -v --silent '^\w{100,}' <<< "${signed_tx}"; then
	printf "Signing GROUP_APPROVAL transaction failed:\n%s\n" "${signed_tx}"
	exit
fi

# ready to publish?
plural="s"
printf "\n"
for ((seconds = 5; seconds > 0; seconds--)); do
	if [ "${seconds}" = "1" ]; then
		plural=""
	fi
	printf "\rBroadcasting in %d second%s...(CTRL-C) to abort " $seconds $plural
	sleep 1
done

printf "\rBroadcasting signed GROUP_APPROVAL transaction...      \n"
result=$( curl --silent --url "http://localhost:${port}/transactions/process" --data @- <<< "${signed_tx}" )
printf "API response:\n%s\n" "${result}"
