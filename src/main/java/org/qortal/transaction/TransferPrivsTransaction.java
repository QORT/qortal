package org.qortal.transaction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.account.Account;
import org.qortal.asset.Asset;
import org.qortal.block.Block;
import org.qortal.block.BlockChain;
import org.qortal.crypto.Crypto;
import org.qortal.data.account.AccountData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.TransferPrivsTransactionData;
import org.qortal.repository.AccountRepository;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TransferPrivsTransaction extends Transaction {

	private static final Logger LOGGER = LogManager.getLogger(TransferPrivsTransaction.class);

	// Properties
	private TransferPrivsTransactionData transferPrivsTransactionData;

	// Constructors

	public TransferPrivsTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.transferPrivsTransactionData = (TransferPrivsTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.transferPrivsTransactionData.getRecipient());
	}

	// Navigation

	public Account getSender() {
		return this.getCreator();
	}

	public Account getRecipient() {
		return new Account(this.repository, this.transferPrivsTransactionData.getRecipient());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check recipient address is valid
		if (!Crypto.isValidAddress(this.transferPrivsTransactionData.getRecipient()))
			return ValidationResult.INVALID_ADDRESS;

		// Check recipient is new account
		AccountData recipientAccountData = this.repository.getAccountRepository().getAccount(this.transferPrivsTransactionData.getRecipient());
		// Non-existent account data is OK, but if account data exists then reference needs to be null
		if (recipientAccountData != null && recipientAccountData.getReference() != null)
			return ValidationResult.ACCOUNT_ALREADY_EXISTS;

		// Check sender has funds for fee
		if (getSender().getConfirmedBalance(Asset.QORT) < this.transferPrivsTransactionData.getFee())
			return ValidationResult.NO_BALANCE;

		// Check sender doesn't have a blocksMintedPenalty, as these accounts cannot be transferred
		AccountData senderAccountData = this.repository.getAccountRepository().getAccount(getSender().getAddress());
		if (senderAccountData == null || senderAccountData.getBlocksMintedPenalty() != 0)
			return ValidationResult.ACCOUNT_NOT_TRANSFERABLE;

		// Disable Transfer Privs (start - end) from feature trigger
		long transactionTimestamp = this.transferPrivsTransactionData.getTimestamp();
		final long startTimestamp = BlockChain.getInstance().getDisableTransferPrivsTimestamp();
		final long endTimestamp = BlockChain.getInstance().getEnableTransferPrivsTimestamp();
		if (transactionTimestamp > startTimestamp && transactionTimestamp < endTimestamp)
			return ValidationResult.TRANSFER_PRIVS_DISABLED;

		return ValidationResult.OK;
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public boolean isConfirmableAtHeight(int height) {
		if (height >= BlockChain.getInstance().getUnconfirmableRewardSharesHeight()) {
			// Not confirmable in online accounts or distribution blocks
			if (Block.isOnlineAccountsBlock(height) || Block.isBatchRewardDistributionBlock(height)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void process() throws DataException {
		Account sender = this.getSender();
		Account recipient = this.getRecipient();

		int senderFlags = sender.getFlags(); // Sender must exist so we always expect a result
		Integer recipientFlags = recipient.getFlags(); // Recipient might not exist yet, so null possible

		// Save prior values
		this.transferPrivsTransactionData.setPreviousSenderFlags(senderFlags);
		this.transferPrivsTransactionData.setPreviousRecipientFlags(recipientFlags);

		// Combine sender & recipient flags for recipient
		if (recipientFlags != null)
			senderFlags |= recipientFlags;

		recipient.setFlags(senderFlags);

		// Clear sender's flags
		sender.setFlags(0);

		// Combine blocks minted counts/adjustments
		final AccountRepository accountRepository = this.repository.getAccountRepository();

		AccountData senderData = accountRepository.getAccount(sender.getAddress());
		int sendersBlocksMinted = senderData.getBlocksMinted();
		int sendersBlocksMintedAdjustment = senderData.getBlocksMintedAdjustment();

		AccountData recipientData = accountRepository.getAccount(recipient.getAddress());
		int recipientBlocksMinted = recipientData != null ? recipientData.getBlocksMinted() : 0;
		int recipientBlocksMintedAdjustment = recipientData != null ? recipientData.getBlocksMintedAdjustment() : 0;

		// Save prior values
		this.transferPrivsTransactionData.setPreviousSenderBlocksMinted(sendersBlocksMinted);
		this.transferPrivsTransactionData.setPreviousSenderBlocksMintedAdjustment(sendersBlocksMintedAdjustment);

		// Combine blocks minted
		recipientData.setBlocksMinted(recipientBlocksMinted + sendersBlocksMinted);
		accountRepository.setMintedBlockCount(recipientData);
		recipientData.setBlocksMintedAdjustment(recipientBlocksMintedAdjustment + sendersBlocksMintedAdjustment);
		accountRepository.setBlocksMintedAdjustment(recipientData);

		// Determine new recipient level based on blocks
		final List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();
		final int maximumLevel = cumulativeBlocksByLevel.size() - 1;
		final int effectiveBlocksMinted = recipientData.getBlocksMinted() + recipientData.getBlocksMintedAdjustment();

		for (int newLevel = maximumLevel; newLevel > 0; --newLevel)
			if (effectiveBlocksMinted >= cumulativeBlocksByLevel.get(newLevel)) {
				if (newLevel > recipientData.getLevel()) {
					// Account has increased in level!
					recipientData.setLevel(newLevel);
					accountRepository.setLevel(recipientData);
					LOGGER.trace(() -> String.format("TRANSFER_PRIVS recipient %s bumped to level %d", recipientData.getAddress(), recipientData.getLevel()));
				}

				break;
			}

		// Reset sender's level
		sender.setLevel(0);

		// Reset sender's blocks minted count & adjustment
		senderData.setBlocksMinted(0);
		accountRepository.setMintedBlockCount(senderData);
		senderData.setBlocksMintedAdjustment(0);
		accountRepository.setBlocksMintedAdjustment(senderData);

		// Save this transaction
		this.repository.getTransactionRepository().save(this.transferPrivsTransactionData);
	}

	@Override
	public void processReferencesAndFees() throws DataException {
		super.processReferencesAndFees();

		// If recipient has no last-reference then use this transaction's signature as last-reference so they can spend their block rewards
		Account recipient = new Account(this.repository, this.transferPrivsTransactionData.getRecipient());
		if (recipient.getLastReference() == null)
			recipient.setLastReference(this.transferPrivsTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		Account sender = this.getSender();
		Account recipient = this.getRecipient();

		final AccountRepository accountRepository = this.repository.getAccountRepository();

		AccountData senderData = accountRepository.getAccount(sender.getAddress());
		AccountData recipientData = accountRepository.getAccount(recipient.getAddress());

		// Restore sender's flags
		senderData.setFlags(this.transferPrivsTransactionData.getPreviousSenderFlags());
		accountRepository.setFlags(senderData);

		// Restore recipient's flags
		Integer previousRecipientFlags = this.transferPrivsTransactionData.getPreviousRecipientFlags();
		if (previousRecipientFlags != null) {
			recipientData.setFlags(previousRecipientFlags);
			accountRepository.setFlags(recipientData);
		}

		// Clean values in transaction data
		this.transferPrivsTransactionData.setPreviousSenderFlags(null);
		this.transferPrivsTransactionData.setPreviousRecipientFlags(null);

		final List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();
		final int maximumLevel = cumulativeBlocksByLevel.size() - 1;

		// Restore sender's block minted count/adjustment
		senderData.setBlocksMinted(this.transferPrivsTransactionData.getPreviousSenderBlocksMinted());
		accountRepository.setMintedBlockCount(senderData);
		senderData.setBlocksMintedAdjustment(this.transferPrivsTransactionData.getPreviousSenderBlocksMintedAdjustment());
		accountRepository.setBlocksMintedAdjustment(senderData);

		// Recalculate sender's level
		int effectiveBlocksMinted = senderData.getBlocksMinted() + senderData.getBlocksMintedAdjustment();
		for (int newLevel = maximumLevel; newLevel > 0; --newLevel)
			if (effectiveBlocksMinted >= cumulativeBlocksByLevel.get(newLevel)) {
				// Account level
				senderData.setLevel(newLevel);
				accountRepository.setLevel(senderData);
				LOGGER.trace(() -> String.format("TRANSFER_PRIVS sender %s reset to level %d", senderData.getAddress(), senderData.getLevel()));

				break;
			}

		if (previousRecipientFlags != null) {
			// Restore recipient block minted count/adjustment
			recipientData.setBlocksMinted(recipientData.getBlocksMinted() - this.transferPrivsTransactionData.getPreviousSenderBlocksMinted());
			accountRepository.setMintedBlockCount(recipientData);
			recipientData.setBlocksMintedAdjustment(recipientData.getBlocksMintedAdjustment() - this.transferPrivsTransactionData.getPreviousSenderBlocksMintedAdjustment());
			accountRepository.setBlocksMintedAdjustment(recipientData);

			// Recalculate recipient's level
			effectiveBlocksMinted = recipientData.getBlocksMinted() + recipientData.getBlocksMintedAdjustment();
			for (int newLevel = maximumLevel; newLevel > 0; --newLevel)
				if (effectiveBlocksMinted >= cumulativeBlocksByLevel.get(newLevel)) {
					// Account level
					recipientData.setLevel(newLevel);
					accountRepository.setLevel(recipientData);
					LOGGER.trace(() -> String.format("TRANSFER_PRIVS recipient %s reset to level %d", recipientData.getAddress(), recipientData.getLevel()));

					break;
				}
		} else {
			// Recipient didn't exist before now
			accountRepository.delete(recipient.getAddress());
		}

		// Clear values in transaction data
		this.transferPrivsTransactionData.setPreviousSenderBlocksMinted(null);
		this.transferPrivsTransactionData.setPreviousSenderBlocksMintedAdjustment(null);

		// Save this transaction
		this.repository.getTransactionRepository().save(this.transferPrivsTransactionData);
	}

	@Override
	public void orphanReferencesAndFees() throws DataException {
		super.orphanReferencesAndFees();

		// If recipient didn't have a last-reference prior to this transaction then remove it
		Account recipient = new Account(this.repository, this.transferPrivsTransactionData.getRecipient());
		if (Arrays.equals(recipient.getLastReference(), this.transferPrivsTransactionData.getSignature()))
			recipient.setLastReference(null);
	}

}
