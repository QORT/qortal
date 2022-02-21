# How to build a testnet

## Create testnet blockchain config

- You can begin by copying the mainnet blockchain config `src/main/resources/blockchain.json`
- Insert `"isTestChain": true,` after the opening `{`
- Modify testnet genesis block

### Testnet genesis block

- Set `timestamp` to a nearby future value, e.g. 15 mins from 'now'
	This is to give yourself enough time to set up other testnet nodes
- Retain the initial `ISSUE_ASSET` transactions!
- Add `ACCOUNT_FLAGS` transactions with `"andMask": -1, "orMask": 1, "xorMask": 0` to create founders
- Add at least one `REWARD_SHARE` transaction otherwise no-one can mint initial blocks!
	You will need to calculate `rewardSharePublicKey` (and private key),
	or make a new account on mainnet and use self-share key values
- Add `ACCOUNT_LEVEL` transactions to set initial level of accounts as needed
- Add `GENESIS` transactions to add QORT/LEGACY_QORA funds to accounts as needed

## Testnet `settings.json`

- Create a new `settings-test.json`
- Make sure to add `"isTestNet": true,`
- Make sure to reference testnet blockchain config file: `"blockchainConfig": "testchain.json",`
- It is a good idea to use a separate database: `"repositoryPath": "db-testnet",`
- You might also need to add `"bitcoinNet": "TEST3",` and `"litecoinNet": "TEST3",`

## Other nodes

- Copy `testchain.json` and `settings-test.json` to other nodes
- Alternatively, you can run multiple nodes on the same machine by:
	* Copying `settings-test.json` to `settings-test-1.json`
	* Configure different `repositoryPath`
	* Configure use of different ports:
		+ `"apiPort": 22391,`
		+ `"listenPort": 22392,`

## Starting-up

- Start up at least as many nodes as `minBlockchainPeers` (or adjust this value instead)
- Probably best to perform API call `DELETE /peers/known`
- Add other nodes via API call `POST /peers <peer-hostname-or-IP>`
- Add minting private key to node(s) via API call `POST /admin/mintingaccounts <minting-private-key>`
	This key must have corresponding `REWARD_SHARE` transaction in testnet genesis block
- Wait for genesis block timestamp to pass
- A node should mint block 2 approximately 60 seconds after genesis block timestamp
- Other testnet nodes will sync *as long as there is at least `minBlockchainPeers` peers with an "up-to-date" chain`
- You can also use API call `POST /admin/forcesync <connected-peer-IP-and-port>` on stuck nodes

## Dealing with stuck chain

Maybe your nodes have been offline and no-one has minted a recent testnet block.
Your options are:

- Start a new testnet from scratch
- Fire up your testnet node(s)
- Force one of your nodes to mint by:
	+ Set a debugger breakpoint on Settings.getMinBlockchainPeers()
	+ When breakpoint is hit, change `this.minBlockchainPeers` to zero, then continue
- Once one of your nodes has minted blocks up to 'now', you can use "forcesync" on the other nodes

## Tools

- `qort` tool, but use `-t` option for default testnet API port (62391)
- `qort` tool, but first set shell variable: `export BASE_URL=some-node-hostname-or-ip:port`
- `qort` tool, but prepend with one-time shell variable: `BASE_URL=some-node-hostname-or-ip:port qort ......`
- `peer-heights`, but use `-t` option, or `BASE_URL` shell variable as above

