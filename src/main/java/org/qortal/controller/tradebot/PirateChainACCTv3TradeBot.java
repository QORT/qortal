package org.qortal.controller.tradebot;

import com.google.common.hash.HashCode;
import com.rust.litewalletjni.LiteWalletJni;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitcoinj.core.Bech32;
import org.bitcoinj.core.Coin;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.account.PublicKeyAccount;
import org.qortal.api.model.crosschain.TradeBotCreateRequest;
import org.qortal.api.resource.CrossChainUtils;
import org.qortal.asset.Asset;
import org.qortal.crosschain.*;
import org.qortal.crypto.Crypto;
import org.qortal.data.at.ATData;
import org.qortal.data.crosschain.CrossChainTradeData;
import org.qortal.data.crosschain.TradeBotData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.DeployAtTransactionData;
import org.qortal.data.transaction.MessageTransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.transaction.DeployAtTransaction;
import org.qortal.transaction.MessageTransaction;
import org.qortal.transaction.Transaction.ValidationResult;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.DeployAtTransactionTransformer;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.qortal.controller.tradebot.TradeStates.State;

/**
 * Performing cross-chain trading steps on behalf of user.
 * <p>
 * We deal with three different independent state-spaces here:
 * <ul>
 * 	<li>Qortal blockchain</li>
 * 	<li>Foreign blockchain</li>
 * 	<li>Trade-bot entries</li>
 * </ul>
 */
public class PirateChainACCTv3TradeBot implements AcctTradeBot {

	private static final Logger LOGGER = LogManager.getLogger(PirateChainACCTv3TradeBot.class);

	/** Maximum time Bob waits for his AT creation transaction to be confirmed into a block. (milliseconds) */
	private static final long MAX_AT_CONFIRMATION_PERIOD = 24 * 60 * 60 * 1000L; // ms

	private static PirateChainACCTv3TradeBot instance;

	private final List<String> endStates = Arrays.asList(State.BOB_DONE, State.BOB_REFUNDED, State.ALICE_DONE, State.ALICE_REFUNDING_A, State.ALICE_REFUNDED).stream()
			.map(State::name)
			.collect(Collectors.toUnmodifiableList());

	private PirateChainACCTv3TradeBot() {
	}

	public static synchronized PirateChainACCTv3TradeBot getInstance() {
		if (instance == null)
			instance = new PirateChainACCTv3TradeBot();

		return instance;
	}

	@Override
	public List<String> getEndStates() {
		return this.endStates;
	}

	/**
	 * Creates a new trade-bot entry from the "Bob" viewpoint, i.e. OFFERing QORT in exchange for ARRR.
	 * <p>
	 * Generates:
	 * <ul>
	 * 	<li>new 'trade' private key</li>
	 * </ul>
	 * Derives:
	 * <ul>
	 * 	<li>'native' (as in Qortal) public key, public key hash, address (starting with Q)</li>
	 * 	<li>'foreign' (as in PirateChain) public key, public key hash</li>
	 * </ul>
	 * A Qortal AT is then constructed including the following as constants in the 'data segment':
	 * <ul>
	 * 	<li>'native'/Qortal 'trade' address - used as a MESSAGE contact</li>
	 * 	<li>'foreign'/PirateChain public key hash - used by Alice's P2SH scripts to allow redeem</li>
	 * 	<li>QORT amount on offer by Bob</li>
	 * 	<li>ARRR amount expected in return by Bob (from Alice)</li>
	 * 	<li>trading timeout, in case things go wrong and everyone needs to refund</li>
	 * </ul>
	 * Returns a DEPLOY_AT transaction that needs to be signed and broadcast to the Qortal network.
	 * <p>
	 * Trade-bot will wait for Bob's AT to be deployed before taking next step.
	 * <p>
	 * @param repository
	 * @param tradeBotCreateRequest
	 * @return raw, unsigned DEPLOY_AT transaction
	 * @throws DataException
	 */
	public byte[] createTrade(Repository repository, TradeBotCreateRequest tradeBotCreateRequest) throws DataException {
		byte[] tradePrivateKey = TradeBot.generateTradePrivateKey();

		byte[] tradeNativePublicKey = TradeBot.deriveTradeNativePublicKey(tradePrivateKey);
		byte[] tradeNativePublicKeyHash = Crypto.hash160(tradeNativePublicKey);
		String tradeNativeAddress = Crypto.toAddress(tradeNativePublicKey);

		byte[] tradeForeignPublicKey = TradeBot.deriveTradeForeignPublicKey(tradePrivateKey);
		byte[] tradeForeignPublicKeyHash = Crypto.hash160(tradeForeignPublicKey);

		// ARRR wallet must be loaded before a trade can be created
		// This is to stop trades from nodes on unsupported architectures (e.g. 32bit)
		if (!LiteWalletJni.isLoaded()) {
			throw new DataException("Pirate wallet not found. Check wallets screen for details.");
		}

		if (!PirateChain.getInstance().isValidAddress(tradeBotCreateRequest.receivingAddress)) {
			throw new DataException("Unsupported Pirate Chain receiving address: " + tradeBotCreateRequest.receivingAddress);
		}

		Bech32.Bech32Data decodedReceivingAddress = Bech32.decode(tradeBotCreateRequest.receivingAddress);
		byte[] pirateChainReceivingAccountInfo = decodedReceivingAddress.data;

		PublicKeyAccount creator = new PublicKeyAccount(repository, tradeBotCreateRequest.creatorPublicKey);

		// Deploy AT
		long timestamp = NTP.getTime();
		byte[] reference = creator.getLastReference();
		long fee = 0L;
		byte[] signature = null;
		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, Group.NO_GROUP, reference, creator.getPublicKey(), fee, signature);

		String name = "QORT/ARRR ACCT";
		String description = "QORT/ARRR cross-chain trade";
		String aTType = "ACCT";
		String tags = "ACCT QORT ARRR";
		byte[] creationBytes = PirateChainACCTv3.buildQortalAT(tradeNativeAddress, tradeForeignPublicKey, tradeBotCreateRequest.qortAmount,
				tradeBotCreateRequest.foreignAmount, tradeBotCreateRequest.tradeTimeout);
		long amount = tradeBotCreateRequest.fundingQortAmount;

		DeployAtTransactionData deployAtTransactionData = new DeployAtTransactionData(baseTransactionData, name, description, aTType, tags, creationBytes, amount, Asset.QORT);

		DeployAtTransaction deployAtTransaction = new DeployAtTransaction(repository, deployAtTransactionData);
		fee = deployAtTransaction.calcRecommendedFee();
		deployAtTransactionData.setFee(fee);

		DeployAtTransaction.ensureATAddress(deployAtTransactionData);
		String atAddress = deployAtTransactionData.getAtAddress();

		TradeBotData tradeBotData =  new TradeBotData(tradePrivateKey, PirateChainACCTv3.NAME,
				State.BOB_WAITING_FOR_AT_CONFIRM.name(), State.BOB_WAITING_FOR_AT_CONFIRM.value,
				creator.getAddress(), atAddress, timestamp, tradeBotCreateRequest.qortAmount,
				tradeNativePublicKey, tradeNativePublicKeyHash, tradeNativeAddress,
				null, null,
				SupportedBlockchain.PIRATECHAIN.name(),
				tradeForeignPublicKey, tradeForeignPublicKeyHash,
				tradeBotCreateRequest.foreignAmount, null, null, null, pirateChainReceivingAccountInfo);

		TradeBot.updateTradeBotState(repository, tradeBotData, () -> String.format("Built AT %s. Waiting for deployment", atAddress));

		// Attempt to backup the trade bot data
		TradeBot.backupTradeBotData(repository, null);

		// Return to user for signing and broadcast as we don't have their Qortal private key
		try {
			return DeployAtTransactionTransformer.toBytes(deployAtTransactionData);
		} catch (TransformationException e) {
			throw new DataException("Failed to transform DEPLOY_AT transaction?", e);
		}
	}

	/**
	 * Creates a trade-bot entry from the 'Alice' viewpoint, i.e. matching ARRR to an existing offer.
	 * <p>
	 * Requires a chosen trade offer from Bob, passed by <tt>crossChainTradeData</tt>
	 * and access to a PirateChain wallet via <tt>xprv58</tt>.
	 * <p>
	 * The <tt>crossChainTradeData</tt> contains the current trade offer state
	 * as extracted from the AT's data segment.
	 * <p>
	 * Access to a funded wallet is via a PirateChain BIP32 hierarchical deterministic key,
	 * passed via <tt>xprv58</tt>.
	 * <b>This key will be stored in your node's database</b>
	 * to allow trade-bot to create/fund the necessary P2SH transactions!
	 * However, due to the nature of BIP32 keys, it is possible to give the trade-bot
	 * only a subset of wallet access (see BIP32 for more details).
	 * <p>
	 * As an example, the xprv58 can be extract from a <i>legacy, password-less</i>
	 * Electrum wallet by going to the console tab and entering:<br>
	 * <tt>wallet.keystore.xprv</tt><br>
	 * which should result in a base58 string starting with either 'xprv' (for PirateChain main-net)
	 * or 'tprv' for (PirateChain test-net).
	 * <p>
	 * It is envisaged that the value in <tt>xprv58</tt> will actually come from a Qortal-UI-managed wallet.
	 * <p>
	 * If sufficient funds are available, <b>this method will actually fund the P2SH-A</b>
	 * with the PirateChain amount expected by 'Bob'.
	 * <p>
	 * If the PirateChain transaction is successfully broadcast to the network then
	 * we also send a MESSAGE to Bob's trade-bot to let them know.
	 * <p>
	 * The trade-bot entry is saved to the repository and the cross-chain trading process commences.
	 * <p>
	 * @param repository
	 * @param crossChainTradeData chosen trade OFFER that Alice wants to match
	 * @param seed58 funded wallet xprv in base58
	 * @return true if P2SH-A funding transaction successfully broadcast to PirateChain network, false otherwise
	 * @throws DataException
	 */
	public ResponseResult startResponse(Repository repository, ATData atData, ACCT acct, CrossChainTradeData crossChainTradeData, String seed58, String receivingAddress) throws DataException {
		byte[] tradePrivateKey = TradeBot.generateTradePrivateKey();
		byte[] secretA = TradeBot.generateSecret();
		byte[] hashOfSecretA = Crypto.hash160(secretA);

		byte[] tradeNativePublicKey = TradeBot.deriveTradeNativePublicKey(tradePrivateKey);
		byte[] tradeNativePublicKeyHash = Crypto.hash160(tradeNativePublicKey);
		String tradeNativeAddress = Crypto.toAddress(tradeNativePublicKey);

		byte[] tradeForeignPublicKey = TradeBot.deriveTradeForeignPublicKey(tradePrivateKey);
		byte[] tradeForeignPublicKeyHash = Crypto.hash160(tradeForeignPublicKey);
		byte[] receivingPublicKeyHash = Base58.decode(receivingAddress); // Actually the whole address, not just PKH

		String tradePrivateKey58 = Base58.encode(tradePrivateKey);
        String tradeForeignPublicKey58 = Base58.encode(tradeForeignPublicKey);
		String secret58 = Base58.encode(secretA);

		// We need to generate lockTime-A: add tradeTimeout to now
		long now = NTP.getTime();
		int lockTimeA = crossChainTradeData.tradeTimeout * 60 + (int) (now / 1000L);

		TradeBotData tradeBotData =  new TradeBotData(tradePrivateKey, PirateChainACCTv3.NAME,
				State.ALICE_WAITING_FOR_AT_LOCK.name(), State.ALICE_WAITING_FOR_AT_LOCK.value,
				receivingAddress, crossChainTradeData.qortalAtAddress, now, crossChainTradeData.qortAmount,
				tradeNativePublicKey, tradeNativePublicKeyHash, tradeNativeAddress,
				secretA, hashOfSecretA,
				SupportedBlockchain.PIRATECHAIN.name(),
				tradeForeignPublicKey, tradeForeignPublicKeyHash,
				crossChainTradeData.expectedForeignAmount, seed58, null, lockTimeA, receivingPublicKeyHash);

		// Attempt to backup the trade bot data
		// Include tradeBotData as an additional parameter, since it's not in the repository yet
		TradeBot.backupTradeBotData(repository, Arrays.asList(tradeBotData));

		// Check we have enough funds via xprv58 to fund P2SH to cover expectedForeignAmount
		long p2shFee;
		try {
			p2shFee = PirateChain.getInstance().getP2shFee(now);
		} catch (ForeignBlockchainException e) {
			LOGGER.debug("Couldn't estimate PirateChain fees?");
			return ResponseResult.NETWORK_ISSUE;
		}

		// Fee for redeem/refund is subtracted from P2SH-A balance.
		// Do not include fee for funding transaction as this is covered by buildSpend()
		long amountA = crossChainTradeData.expectedForeignAmount + p2shFee /*redeeming/refunding P2SH-A*/;

		// P2SH-A to be funded
		byte[] redeemScriptBytes = PirateChainHTLC.buildScript(tradeForeignPublicKey, lockTimeA, crossChainTradeData.creatorForeignPKH, hashOfSecretA);
		String p2shAddressT3 = PirateChain.getInstance().deriveP2shAddress(redeemScriptBytes); // Use t3 prefix when funding
		byte[] redeemScriptWithPrefixBytes = PirateChainHTLC.buildScriptWithPrefix(tradeForeignPublicKey, lockTimeA, crossChainTradeData.creatorForeignPKH, hashOfSecretA);
		String redeemScriptWithPrefix58 = Base58.encode(redeemScriptWithPrefixBytes);

		// Send to P2SH address
		try {
			String txid = PirateChain.getInstance().fundP2SH(seed58, p2shAddressT3, amountA, redeemScriptWithPrefix58);
			LOGGER.info("fundingTxidHex: {}", txid);

		} catch (ForeignBlockchainException e) {
			LOGGER.debug("Unable to build and send P2SH-A funding transaction - lack of funds?");
			return ResponseResult.BALANCE_ISSUE;
		}

		// Attempt to send MESSAGE to Bob's Qortal trade address
		byte[] messageData = CrossChainUtils.buildOfferMessage(tradeBotData.getTradeForeignPublicKey(), tradeBotData.getHashOfSecret(), tradeBotData.getLockTimeA());
		String messageRecipient = crossChainTradeData.qortalCreatorTradeAddress;

		boolean isMessageAlreadySent = repository.getMessageRepository().exists(tradeBotData.getTradeNativePublicKey(), messageRecipient, messageData);
		if (!isMessageAlreadySent) {
			// Do this in a new thread so caller doesn't have to wait for computeNonce()
			// In the unlikely event that the transaction doesn't validate then the buy won't happen and eventually Alice's AT will be refunded
			new Thread(() -> {
				try (final Repository threadsRepository = RepositoryManager.getRepository()) {
					PrivateKeyAccount sender = new PrivateKeyAccount(threadsRepository, tradeBotData.getTradePrivateKey());
					MessageTransaction messageTransaction = MessageTransaction.build(threadsRepository, sender, Group.NO_GROUP, messageRecipient, messageData, false, false);

					LOGGER.info("Computing nonce at difficulty {} for AT {} and recipient {}", messageTransaction.getPoWDifficulty(), tradeBotData.getAtAddress(), messageRecipient);
					messageTransaction.computeNonce();
					MessageTransactionData newMessageTransactionData = (MessageTransactionData) messageTransaction.getTransactionData();
					LOGGER.info("Computed nonce {} at difficulty {}", newMessageTransactionData.getNonce(), messageTransaction.getPoWDifficulty());
					messageTransaction.sign(sender);

					// reset repository state to prevent deadlock
					threadsRepository.discardChanges();

					if (messageTransaction.isSignatureValid()) {
						ValidationResult result = messageTransaction.importAsUnconfirmed();

						if (result != ValidationResult.OK) {
							LOGGER.warn(() -> String.format("Unable to send MESSAGE to Bob's trade-bot %s: %s", messageRecipient, result.name()));
						}
					}
					else {
						LOGGER.warn(() -> String.format("Unable to send MESSAGE to Bob's trade-bot %s: signature invalid", messageRecipient));
					}
				} catch (DataException e) {
					LOGGER.warn(() -> String.format("Unable to send MESSAGE to Bob's trade-bot %s: %s", messageRecipient, e.getMessage()));
				}
			}, "TradeBot response").start();
		}

		TradeBot.updateTradeBotState(repository, tradeBotData, () -> String.format("Funding P2SH-A %s. Messaged Bob. Waiting for AT-lock", p2shAddressT3));

		return ResponseResult.OK;
	}

	public static String hex(byte[] bytes) {
		StringBuilder result = new StringBuilder();
		for (byte aByte : bytes) {
			result.append(String.format("%02x", aByte));
			// upper case
			// result.append(String.format("%02X", aByte));
		}
		return result.toString();
	}

	@Override
	public boolean canDelete(Repository repository, TradeBotData tradeBotData) throws DataException {
		State tradeBotState = State.valueOf(tradeBotData.getStateValue());
		if (tradeBotState == null)
			return true;

		// If the AT doesn't exist then we might as well let the user tidy up
		if (!repository.getATRepository().exists(tradeBotData.getAtAddress()))
			return true;

		switch (tradeBotState) {
			case BOB_WAITING_FOR_AT_CONFIRM:
			case ALICE_DONE:
			case BOB_DONE:
			case ALICE_REFUNDED:
			case BOB_REFUNDED:
			case ALICE_REFUNDING_A:
				return true;

			default:
				return false;
		}
	}

	@Override
	public void progress(Repository repository, TradeBotData tradeBotData) throws DataException, ForeignBlockchainException {
		State tradeBotState = State.valueOf(tradeBotData.getStateValue());
		if (tradeBotState == null) {
			LOGGER.info(() -> String.format("Trade-bot entry for AT %s has invalid state?", tradeBotData.getAtAddress()));
			return;
		}

		ATData atData = null;
		CrossChainTradeData tradeData = null;

		if (tradeBotState.requiresAtData) {
			// Attempt to fetch AT data
			atData = repository.getATRepository().fromATAddress(tradeBotData.getAtAddress());
			if (atData == null) {
				LOGGER.debug(() -> String.format("Unable to fetch trade AT %s from repository", tradeBotData.getAtAddress()));
				return;
			}

			if (tradeBotState.requiresTradeData) {
				tradeData = PirateChainACCTv3.getInstance().populateTradeData(repository, atData);
				if (tradeData == null) {
					LOGGER.warn(() -> String.format("Unable to fetch ACCT trade data for AT %s from repository", tradeBotData.getAtAddress()));
					return;
				}
			}
		}

		switch (tradeBotState) {
			case BOB_WAITING_FOR_AT_CONFIRM:
				handleBobWaitingForAtConfirm(repository, tradeBotData);
				break;

			case BOB_WAITING_FOR_MESSAGE:
				TradeBot.getInstance().updatePresence(repository, tradeBotData, tradeData);
				handleBobWaitingForMessage(repository, tradeBotData, atData, tradeData);
				break;

			case ALICE_WAITING_FOR_AT_LOCK:
				TradeBot.getInstance().updatePresence(repository, tradeBotData, tradeData);
				handleAliceWaitingForAtLock(repository, tradeBotData, atData, tradeData);
				break;

			case BOB_WAITING_FOR_AT_REDEEM:
				TradeBot.getInstance().updatePresence(repository, tradeBotData, tradeData);
				handleBobWaitingForAtRedeem(repository, tradeBotData, atData, tradeData);
				break;

			case ALICE_DONE:
			case BOB_DONE:
				break;

			case ALICE_REFUNDING_A:
				TradeBot.getInstance().updatePresence(repository, tradeBotData, tradeData);
				handleAliceRefundingP2shA(repository, tradeBotData, atData, tradeData);
				break;

			case ALICE_REFUNDED:
			case BOB_REFUNDED:
				break;
		}
	}

	/**
	 * Trade-bot is waiting for Bob's AT to deploy.
	 * <p>
	 * If AT is deployed, then trade-bot's next step is to wait for MESSAGE from Alice.
	 */
	private void handleBobWaitingForAtConfirm(Repository repository, TradeBotData tradeBotData) throws DataException {
		if (!repository.getATRepository().exists(tradeBotData.getAtAddress())) {
			if (NTP.getTime() - tradeBotData.getTimestamp() <= MAX_AT_CONFIRMATION_PERIOD)
				return;

			// We've waited ages for AT to be confirmed into a block but something has gone awry.
			// After this long we assume transaction loss so give up with trade-bot entry too.
			tradeBotData.setState(State.BOB_REFUNDED.name());
			tradeBotData.setStateValue(State.BOB_REFUNDED.value);
			tradeBotData.setTimestamp(NTP.getTime());
			// We delete trade-bot entry here instead of saving, hence not using updateTradeBotState()
			repository.getCrossChainRepository().delete(tradeBotData.getTradePrivateKey());
			repository.saveChanges();

			LOGGER.info(() -> String.format("AT %s never confirmed. Giving up on trade", tradeBotData.getAtAddress()));
			TradeBot.notifyStateChange(tradeBotData);
			return;
		}

		TradeBot.updateTradeBotState(repository, tradeBotData, State.BOB_WAITING_FOR_MESSAGE,
				() -> String.format("AT %s confirmed ready. Waiting for trade message", tradeBotData.getAtAddress()));
	}

	/**
	 * Trade-bot is waiting for MESSAGE from Alice's trade-bot, containing Alice's trade info.
	 * <p>
	 * It's possible Bob has cancelling his trade offer, receiving an automatic QORT refund,
	 * in which case trade-bot is done with this specific trade and finalizes on refunded state.
	 * <p>
	 * Assuming trade is still on offer, trade-bot checks the contents of MESSAGE from Alice's trade-bot.
	 * <p>
	 * Details from Alice are used to derive P2SH-A address and this is checked for funding balance.
	 * <p>
	 * Assuming P2SH-A has at least expected PirateChain balance,
	 * Bob's trade-bot constructs a zero-fee, PoW MESSAGE to send to Bob's AT with more trade details.
	 * <p>
	 * On processing this MESSAGE, Bob's AT should switch into 'TRADE' mode and only trade with Alice.
	 * <p>
	 * Trade-bot's next step is to wait for Alice to redeem the AT, which will allow Bob to
	 * extract secret-A needed to redeem Alice's P2SH.
	 * @throws ForeignBlockchainException
	 */
	private void handleBobWaitingForMessage(Repository repository, TradeBotData tradeBotData,
			ATData atData, CrossChainTradeData crossChainTradeData) throws DataException, ForeignBlockchainException {
		// If AT has finished then Bob likely cancelled his trade offer
		if (atData.getIsFinished()) {
			TradeBot.updateTradeBotState(repository, tradeBotData, State.BOB_REFUNDED,
					() -> String.format("AT %s cancelled - trading aborted", tradeBotData.getAtAddress()));
			return;
		}

		PirateChain pirateChain = PirateChain.getInstance();

		String address = tradeBotData.getTradeNativeAddress();
		List<MessageTransactionData> messageTransactionsData = repository.getMessageRepository().getMessagesByParticipants(null, address, null, null, null);

		for (MessageTransactionData messageTransactionData : messageTransactionsData) {
			if (messageTransactionData.isText())
				continue;

			// We're expecting: HASH160(secret-A), Alice's PirateChain pubkeyhash and lockTime-A
			byte[] messageData = messageTransactionData.getData();
			PirateChainACCTv3.OfferMessageData offerMessageData = PirateChainACCTv3.extractOfferMessageData(messageData);
			if (offerMessageData == null)
				continue;

			byte[] aliceForeignPublicKey = offerMessageData.partnerPirateChainPublicKey;
			byte[] hashOfSecretA = offerMessageData.hashOfSecretA;
			int lockTimeA = (int) offerMessageData.lockTimeA;
			long messageTimestamp = messageTransactionData.getTimestamp();
			int refundTimeout = PirateChainACCTv3.calcRefundTimeout(messageTimestamp, lockTimeA);

			// Determine P2SH-A address and confirm funded
			byte[] redeemScriptA = PirateChainHTLC.buildScript(aliceForeignPublicKey, lockTimeA, tradeBotData.getTradeForeignPublicKey(), hashOfSecretA);
			String p2shAddress = pirateChain.deriveP2shAddressBPrefix(redeemScriptA); // Use 'b' prefix when checking status

			long feeTimestamp = calcFeeTimestamp(lockTimeA, crossChainTradeData.tradeTimeout);
			long p2shFee = PirateChain.getInstance().getP2shFee(feeTimestamp);
			final long minimumAmountA = tradeBotData.getForeignAmount() + p2shFee;

			BitcoinyHTLC.Status htlcStatusA = PirateChainHTLC.determineHtlcStatus(pirateChain.getBlockchainProvider(), p2shAddress, minimumAmountA);

			switch (htlcStatusA) {
				case UNFUNDED:
				case FUNDING_IN_PROGRESS:
					// There might be another MESSAGE from someone else with an actually funded P2SH-A...
					continue;

				case REDEEM_IN_PROGRESS:
				case REDEEMED:
					// We've already redeemed this?
					TradeBot.updateTradeBotState(repository, tradeBotData, State.BOB_DONE,
							() -> String.format("P2SH-A %s already spent? Assuming trade complete", p2shAddress));
					return;

				case REFUND_IN_PROGRESS:
				case REFUNDED:
					// This P2SH-A is burnt, but there might be another MESSAGE from someone else with an actually funded P2SH-A...
					continue;

				case FUNDED:
					// Fall-through out of switch...
					break;
			}

			// Good to go - send MESSAGE to AT

			String aliceNativeAddress = Crypto.toAddress(messageTransactionData.getCreatorPublicKey());

			// Build outgoing message, padding each part to 32 bytes to make it easier for AT to consume
			byte[] outgoingMessageData = PirateChainACCTv3.buildTradeMessage(aliceNativeAddress, aliceForeignPublicKey, hashOfSecretA, lockTimeA, refundTimeout);
			String messageRecipient = tradeBotData.getAtAddress();

			boolean isMessageAlreadySent = repository.getMessageRepository().exists(tradeBotData.getTradeNativePublicKey(), messageRecipient, outgoingMessageData);
			if (!isMessageAlreadySent) {
				PrivateKeyAccount sender = new PrivateKeyAccount(repository, tradeBotData.getTradePrivateKey());
				MessageTransaction outgoingMessageTransaction = MessageTransaction.build(repository, sender, Group.NO_GROUP, messageRecipient, outgoingMessageData, false, false);

				LOGGER.info("Computing nonce at difficulty {} for AT {} and recipient {}", outgoingMessageTransaction.getPoWDifficulty(), tradeBotData.getAtAddress(), messageRecipient);
				outgoingMessageTransaction.computeNonce();
				MessageTransactionData newMessageTransactionData = (MessageTransactionData) outgoingMessageTransaction.getTransactionData();
				LOGGER.info("Computed nonce {} at difficulty {}", newMessageTransactionData.getNonce(), outgoingMessageTransaction.getPoWDifficulty());
				outgoingMessageTransaction.sign(sender);

				// reset repository state to prevent deadlock
				repository.discardChanges();

				if (outgoingMessageTransaction.isSignatureValid()) {
					ValidationResult result = outgoingMessageTransaction.importAsUnconfirmed();

					if (result != ValidationResult.OK) {
						LOGGER.warn(() -> String.format("Unable to send MESSAGE to AT %s: %s", messageRecipient, result.name()));
						return;
					}
				}
				else {
					LOGGER.warn(() -> String.format("Unable to send MESSAGE to AT %s: signature invalid", messageRecipient));
					return;
				}
			}

			TradeBot.updateTradeBotState(repository, tradeBotData, State.BOB_WAITING_FOR_AT_REDEEM,
					() -> String.format("Locked AT %s to %s. Waiting for AT redeem", tradeBotData.getAtAddress(), aliceNativeAddress));

			return;
		}
	}

	/**
	 * Trade-bot is waiting for Bob's AT to switch to TRADE mode and lock trade to Alice only.
	 * <p>
	 * It's possible that Bob has cancelled his trade offer in the mean time, or that somehow
	 * this process has taken so long that we've reached P2SH-A's locktime, or that someone else
	 * has managed to trade with Bob. In any of these cases, trade-bot switches to begin the refunding process.
	 * <p>
	 * Assuming Bob's AT is locked to Alice, trade-bot checks AT's state data to make sure it is correct.
	 * <p>
	 * If all is well, trade-bot then redeems AT using Alice's secret-A, releasing Bob's QORT to Alice.
	 * <p>
	 * In revealing a valid secret-A, Bob can then redeem the ARRR funds from P2SH-A.
	 * <p>
	 * @throws ForeignBlockchainException
	 */
	private void handleAliceWaitingForAtLock(Repository repository, TradeBotData tradeBotData,
			ATData atData, CrossChainTradeData crossChainTradeData) throws DataException, ForeignBlockchainException {
		if (aliceUnexpectedState(repository, tradeBotData, atData, crossChainTradeData))
			return;

		PirateChain pirateChain = PirateChain.getInstance();
		int lockTimeA = tradeBotData.getLockTimeA();

		// Refund P2SH-A if we've passed lockTime-A
		if (NTP.getTime() >= lockTimeA * 1000L) {
			byte[] redeemScriptA = PirateChainHTLC.buildScript(tradeBotData.getTradeForeignPublicKey(), lockTimeA, crossChainTradeData.creatorForeignPKH, tradeBotData.getHashOfSecret());
			String p2shAddress = pirateChain.deriveP2shAddressBPrefix(redeemScriptA); // Use 'b' prefix when checking status

			long feeTimestamp = calcFeeTimestamp(lockTimeA, crossChainTradeData.tradeTimeout);
			long p2shFee = PirateChain.getInstance().getP2shFee(feeTimestamp);
			long minimumAmountA = crossChainTradeData.expectedForeignAmount + p2shFee;

			BitcoinyHTLC.Status htlcStatusA = PirateChainHTLC.determineHtlcStatus(pirateChain.getBlockchainProvider(), p2shAddress, minimumAmountA);

			switch (htlcStatusA) {
				case UNFUNDED:
				case FUNDING_IN_PROGRESS:
				case FUNDED:
					break;

				case REDEEM_IN_PROGRESS:
				case REDEEMED:
					// Already redeemed?
					TradeBot.updateTradeBotState(repository, tradeBotData, State.ALICE_DONE,
							() -> String.format("P2SH-A %s already spent? Assuming trade completed", p2shAddress));
					return;

				case REFUND_IN_PROGRESS:
				case REFUNDED:
					TradeBot.updateTradeBotState(repository, tradeBotData, State.ALICE_REFUNDED,
							() -> String.format("P2SH-A %s already refunded. Trade aborted", p2shAddress));
					return;

			}

			TradeBot.updateTradeBotState(repository, tradeBotData, State.ALICE_REFUNDING_A,
					() -> atData.getIsFinished()
					? String.format("AT %s cancelled. Refunding P2SH-A %s - aborting trade", tradeBotData.getAtAddress(), p2shAddress)
					: String.format("LockTime-A reached, refunding P2SH-A %s - aborting trade", p2shAddress));

			return;
		}

		// We're waiting for AT to be in TRADE mode
		if (crossChainTradeData.mode != AcctMode.TRADING)
			return;

		// AT is in TRADE mode and locked to us as checked by aliceUnexpectedState() above

		// Find our MESSAGE to AT from previous state
		List<MessageTransactionData> messageTransactionsData = repository.getMessageRepository().getMessagesByParticipants(tradeBotData.getTradeNativePublicKey(),
				crossChainTradeData.qortalCreatorTradeAddress, null, null, null);
		if (messageTransactionsData == null || messageTransactionsData.isEmpty()) {
			LOGGER.warn(() -> String.format("Unable to find our message to trade creator %s?", crossChainTradeData.qortalCreatorTradeAddress));
			return;
		}

		long recipientMessageTimestamp = messageTransactionsData.get(0).getTimestamp();
		int refundTimeout = PirateChainACCTv3.calcRefundTimeout(recipientMessageTimestamp, lockTimeA);

		// Our calculated refundTimeout should match AT's refundTimeout
		if (refundTimeout != crossChainTradeData.refundTimeout) {
			LOGGER.debug(() -> String.format("Trade AT refundTimeout '%d' doesn't match our refundTimeout '%d'", crossChainTradeData.refundTimeout, refundTimeout));
			// We'll eventually refund
			return;
		}

		// We're good to redeem AT

		// Send 'redeem' MESSAGE to AT using both secret
		byte[] secretA = tradeBotData.getSecret();
		String qortalReceivingAddress = Base58.encode(tradeBotData.getReceivingAccountInfo()); // Actually contains whole address, not just PKH
		byte[] messageData = PirateChainACCTv3.buildRedeemMessage(secretA, qortalReceivingAddress);
		String messageRecipient = tradeBotData.getAtAddress();

		boolean isMessageAlreadySent = repository.getMessageRepository().exists(tradeBotData.getTradeNativePublicKey(), messageRecipient, messageData);
		if (!isMessageAlreadySent) {
			PrivateKeyAccount sender = new PrivateKeyAccount(repository, tradeBotData.getTradePrivateKey());
			MessageTransaction messageTransaction = MessageTransaction.build(repository, sender, Group.NO_GROUP, messageRecipient, messageData, false, false);

			LOGGER.info("Computing nonce at difficulty {} for AT {} and recipient {}", messageTransaction.getPoWDifficulty(), tradeBotData.getAtAddress(), messageRecipient);
			messageTransaction.computeNonce();
			MessageTransactionData newMessageTransactionData = (MessageTransactionData) messageTransaction.getTransactionData();
			LOGGER.info("Computed nonce {} at difficulty {}", newMessageTransactionData.getNonce(), messageTransaction.getPoWDifficulty());
			messageTransaction.sign(sender);

			// Reset repository state to prevent deadlock
			repository.discardChanges();

			if (messageTransaction.isSignatureValid()) {
				ValidationResult result = messageTransaction.importAsUnconfirmed();

				if (result != ValidationResult.OK) {
					LOGGER.warn(() -> String.format("Unable to send MESSAGE to AT %s: %s", messageRecipient, result.name()));
					return;
				}
			}
			else {
				LOGGER.warn(() -> String.format("Unable to send MESSAGE to AT %s: signature invalid", messageRecipient));
				return;
			}
		}

		TradeBot.updateTradeBotState(repository, tradeBotData, State.ALICE_DONE,
				() -> String.format("Redeeming AT %s. Funds should arrive at %s",
						tradeBotData.getAtAddress(), qortalReceivingAddress));
	}

	/**
	 * Trade-bot is waiting for Alice to redeem Bob's AT, thus revealing secret-A which is required to spend the ARRR funds from P2SH-A.
	 * <p>
	 * It's possible that Bob's AT has reached its trading timeout and automatically refunded QORT back to Bob. In which case,
	 * trade-bot is done with this specific trade and finalizes in refunded state.
	 * <p>
	 * Assuming trade-bot can extract a valid secret-A from Alice's MESSAGE then trade-bot uses that to redeem the ARRR funds from P2SH-A
	 * to Bob's 'foreign'/PirateChain trade legacy-format address, as derived from trade private key.
	 * <p>
	 * (This could potentially be 'improved' to send ARRR to any address of Bob's choosing by changing the transaction output).
	 * <p>
	 * If trade-bot successfully broadcasts the transaction, then this specific trade is done.
	 * @throws ForeignBlockchainException
	 */
	private void handleBobWaitingForAtRedeem(Repository repository, TradeBotData tradeBotData,
			ATData atData, CrossChainTradeData crossChainTradeData) throws DataException, ForeignBlockchainException {
		// AT should be 'finished' once Alice has redeemed QORT funds
		if (!atData.getIsFinished())
			// Not finished yet
			return;

		// If AT is REFUNDED or CANCELLED then something has gone wrong
		if (crossChainTradeData.mode == AcctMode.REFUNDED || crossChainTradeData.mode == AcctMode.CANCELLED) {
			// Alice hasn't redeemed the QORT, so there is no point in trying to redeem the ARRR
			TradeBot.updateTradeBotState(repository, tradeBotData, State.BOB_REFUNDED,
					() -> String.format("AT %s has auto-refunded - trade aborted", tradeBotData.getAtAddress()));

			return;
		}

		byte[] secretA = PirateChainACCTv3.getInstance().findSecretA(repository, crossChainTradeData);
		if (secretA == null) {
			LOGGER.debug(() -> String.format("Unable to find secret-A from redeem message to AT %s?", tradeBotData.getAtAddress()));
			return;
		}

		// Use secret-A to redeem P2SH-A

		PirateChain pirateChain = PirateChain.getInstance();

		byte[] receivingAccountInfo = tradeBotData.getReceivingAccountInfo();
		int lockTimeA = crossChainTradeData.lockTimeA;
		byte[] redeemScriptA = PirateChainHTLC.buildScript(crossChainTradeData.partnerForeignPKH, lockTimeA, crossChainTradeData.creatorForeignPKH, crossChainTradeData.hashOfSecretA);
		String p2shAddress = pirateChain.deriveP2shAddressBPrefix(redeemScriptA); // Use 'b' prefix when checking status
		String p2shAddressT3 = pirateChain.deriveP2shAddress(redeemScriptA); // Use 't3' prefix when refunding

		// Fee for redeem/refund is subtracted from P2SH-A balance.
		long feeTimestamp = calcFeeTimestamp(lockTimeA, crossChainTradeData.tradeTimeout);
		long p2shFee = PirateChain.getInstance().getP2shFee(feeTimestamp);
		long minimumAmountA = crossChainTradeData.expectedForeignAmount + p2shFee;
		String receivingAddress = Bech32.encode("zs", receivingAccountInfo);

		BitcoinyHTLC.Status htlcStatusA = PirateChainHTLC.determineHtlcStatus(pirateChain.getBlockchainProvider(), p2shAddress, minimumAmountA);

		switch (htlcStatusA) {
			case UNFUNDED:
			case FUNDING_IN_PROGRESS:
				// P2SH-A suddenly not funded? Our best bet at this point is to hope for AT auto-refund
				return;

			case REDEEM_IN_PROGRESS:
			case REDEEMED:
				// Double-check that we have redeemed P2SH-A...
				break;

			case REFUND_IN_PROGRESS:
			case REFUNDED:
				// Wait for AT to auto-refund
				return;

			case FUNDED: {
				// Get funding txid
				String fundingTxidHex = PirateChainHTLC.getUnspentFundingTxid(pirateChain.getBlockchainProvider(), p2shAddress, minimumAmountA);
				if (fundingTxidHex == null) {
					throw new ForeignBlockchainException("Missing funding txid when redeeming P2SH");
				}
				String fundingTxid58 = Base58.encode(HashCode.fromString(fundingTxidHex).asBytes());

				// Redeem P2SH
				Coin redeemAmount = Coin.valueOf(crossChainTradeData.expectedForeignAmount);
				byte[] privateKey = tradeBotData.getTradePrivateKey();
				String secret58 = Base58.encode(secretA);
				String privateKey58 = Base58.encode(privateKey);
				String redeemScript58 = Base58.encode(redeemScriptA);

				String txid = PirateChain.getInstance().redeemP2sh(p2shAddressT3, receivingAddress, redeemAmount.value,
						redeemScript58, fundingTxid58, secret58, privateKey58);
				LOGGER.info("Redeem txid: {}", txid);
				break;
			}
		}

		TradeBot.updateTradeBotState(repository, tradeBotData, State.BOB_DONE,
				() -> String.format("P2SH-A %s redeemed. Funds should arrive at %s", tradeBotData.getAtAddress(), receivingAddress));
	}

	/**
	 * Trade-bot is attempting to refund P2SH-A.
	 * @throws ForeignBlockchainException
	 */
	private void handleAliceRefundingP2shA(Repository repository, TradeBotData tradeBotData,
			ATData atData, CrossChainTradeData crossChainTradeData) throws DataException, ForeignBlockchainException {
		int lockTimeA = tradeBotData.getLockTimeA();

		// We can't refund P2SH-A until lockTime-A has passed
		if (NTP.getTime() <= lockTimeA * 1000L)
			return;

		PirateChain pirateChain = PirateChain.getInstance();

		// We can't refund P2SH-A until median block time has passed lockTime-A (see BIP113)
		int medianBlockTime = pirateChain.getMedianBlockTime();
		if (medianBlockTime <= lockTimeA)
			return;

		byte[] redeemScriptA = PirateChainHTLC.buildScript(tradeBotData.getTradeForeignPublicKey(), lockTimeA, crossChainTradeData.creatorForeignPKH, tradeBotData.getHashOfSecret());
		String p2shAddress = pirateChain.deriveP2shAddressBPrefix(redeemScriptA); // Use 'b' prefix when checking status
		String p2shAddressT3 = pirateChain.deriveP2shAddress(redeemScriptA); // Use 't3' prefix when refunding

		// Fee for redeem/refund is subtracted from P2SH-A balance.
		long feeTimestamp = calcFeeTimestamp(lockTimeA, crossChainTradeData.tradeTimeout);
		long p2shFee = PirateChain.getInstance().getP2shFee(feeTimestamp);
		long minimumAmountA = crossChainTradeData.expectedForeignAmount + p2shFee;
		BitcoinyHTLC.Status htlcStatusA = PirateChainHTLC.determineHtlcStatus(pirateChain.getBlockchainProvider(), p2shAddress, minimumAmountA);

		switch (htlcStatusA) {
			case UNFUNDED:
			case FUNDING_IN_PROGRESS:
				// Still waiting for P2SH-A to be funded...
				return;

			case REDEEM_IN_PROGRESS:
			case REDEEMED:
				// Too late!
				TradeBot.updateTradeBotState(repository, tradeBotData, State.ALICE_DONE,
						() -> String.format("P2SH-A %s already spent!", p2shAddress));
				return;

			case REFUND_IN_PROGRESS:
			case REFUNDED:
				break;

			case FUNDED:{
				// Get funding txid
				String fundingTxidHex = PirateChainHTLC.getUnspentFundingTxid(pirateChain.getBlockchainProvider(), p2shAddress, minimumAmountA);
				if (fundingTxidHex == null) {
					throw new ForeignBlockchainException("Missing funding txid when refunding P2SH");
				}
				String fundingTxid58 = Base58.encode(HashCode.fromString(fundingTxidHex).asBytes());

				Coin refundAmount = Coin.valueOf(crossChainTradeData.expectedForeignAmount);
				byte[] privateKey = tradeBotData.getTradePrivateKey();
				String privateKey58 = Base58.encode(privateKey);
				String redeemScript58 = Base58.encode(redeemScriptA);
				String receivingAddress = pirateChain.getWalletAddress(tradeBotData.getForeignKey());

				String txid = PirateChain.getInstance().refundP2sh(p2shAddressT3,
						receivingAddress, refundAmount.value, redeemScript58, fundingTxid58, lockTimeA, privateKey58);
				LOGGER.info("Refund txid: {}", txid);
				break;
			}
		}

		TradeBot.updateTradeBotState(repository, tradeBotData, State.ALICE_REFUNDED,
				() -> String.format("LockTime-A reached. Refunded P2SH-A %s. Trade aborted", p2shAddress));
	}

	/**
	 * Returns true if Alice finds AT unexpectedly cancelled, refunded, redeemed or locked to someone else.
	 * <p>
	 * Will automatically update trade-bot state to <tt>ALICE_REFUNDING_A</tt> or <tt>ALICE_DONE</tt> as necessary.
	 * 
	 * @throws DataException
	 * @throws ForeignBlockchainException
	 */
	private boolean aliceUnexpectedState(Repository repository, TradeBotData tradeBotData,
			ATData atData, CrossChainTradeData crossChainTradeData) throws DataException, ForeignBlockchainException {
		// This is OK
		if (!atData.getIsFinished() && crossChainTradeData.mode == AcctMode.OFFERING)
			return false;

		boolean isAtLockedToUs = tradeBotData.getTradeNativeAddress().equals(crossChainTradeData.qortalPartnerAddress);

		if (!atData.getIsFinished() && crossChainTradeData.mode == AcctMode.TRADING)
			if (isAtLockedToUs) {
				// AT is trading with us - OK
				return false;
			} else {
				TradeBot.updateTradeBotState(repository, tradeBotData, State.ALICE_REFUNDING_A,
						() -> String.format("AT %s trading with someone else: %s. Refunding & aborting trade", tradeBotData.getAtAddress(), crossChainTradeData.qortalPartnerAddress));

				return true;
			}

		if (atData.getIsFinished() && crossChainTradeData.mode == AcctMode.REDEEMED && isAtLockedToUs) {
			// We've redeemed already?
			TradeBot.updateTradeBotState(repository, tradeBotData, State.ALICE_DONE,
					() -> String.format("AT %s already redeemed by us. Trade completed", tradeBotData.getAtAddress()));
		} else {
			// Any other state is not good, so start defensive refund
			TradeBot.updateTradeBotState(repository, tradeBotData, State.ALICE_REFUNDING_A,
					() -> String.format("AT %s cancelled/refunded/redeemed by someone else/invalid state. Refunding & aborting trade", tradeBotData.getAtAddress()));
		}

		return true;
	}

	private long calcFeeTimestamp(int lockTimeA, int tradeTimeout) {
		return (lockTimeA - tradeTimeout * 60) * 1000L;
	}

}
