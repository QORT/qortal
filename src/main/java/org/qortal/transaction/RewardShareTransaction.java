package org.qortal.transaction;

import org.qortal.account.Account;
import org.qortal.account.PublicKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.Block;
import org.qortal.block.BlockChain;
import org.qortal.crypto.Crypto;
import org.qortal.data.account.RewardShareData;
import org.qortal.data.transaction.RewardShareTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.transform.Transformer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RewardShareTransaction extends Transaction {

	public static final int MAX_SHARE = 100 * 100; // unscaled

	// Properties

	private RewardShareTransactionData rewardShareTransactionData;
	private boolean haveCheckedForExistingRewardShare = false;
	private RewardShareData existingRewardShareData = null;

	// Constructors

	public RewardShareTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.rewardShareTransactionData = (RewardShareTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.rewardShareTransactionData.getRecipient());
	}

	private RewardShareData getExistingRewardShare() throws DataException {
		if (!this.haveCheckedForExistingRewardShare) {
			this.haveCheckedForExistingRewardShare = true;

			// Look up any existing reward-share (using transaction's reward-share public key)
			this.existingRewardShareData = this.repository.getAccountRepository().getRewardShare(this.rewardShareTransactionData.getRewardSharePublicKey());

			if (this.existingRewardShareData == null)
				// No luck, try looking up existing reward-share using minting & recipient account info
				this.existingRewardShareData = this.repository.getAccountRepository().getRewardShare(this.rewardShareTransactionData.getMinterPublicKey(), this.rewardShareTransactionData.getRecipient());
		}

		return this.existingRewardShareData;
	}

	private boolean doesRewardShareMatch(RewardShareData rewardShareData) {
		return rewardShareData.getRecipient().equals(this.rewardShareTransactionData.getRecipient())
				&& Arrays.equals(rewardShareData.getMinterPublicKey(), this.rewardShareTransactionData.getMinterPublicKey())
				&& Arrays.equals(rewardShareData.getRewardSharePublicKey(), this.rewardShareTransactionData.getRewardSharePublicKey());
	}

	// Navigation

	public PublicKeyAccount getMintingAccount() {
		return this.getCreator();
	}

	public Account getRecipient() {
		return new Account(this.repository, this.rewardShareTransactionData.getRecipient());
	}

	// Processing

	@Override
	public ValidationResult isFeeValid() throws DataException {
		// Look up any existing reward-share (using transaction's reward-share public key)
		RewardShareData existingRewardShareData = this.getExistingRewardShare();

		// If we have an existing reward-share then minter/recipient/reward-share-public-key should all match.
		// This is to prevent malicious actors using multiple (fake) reward-share public keys for the same minter/recipient combo,
		// or reusing the same reward-share public key for a different minter/recipient pair.
		if (existingRewardShareData != null && !this.doesRewardShareMatch(existingRewardShareData))
			return ValidationResult.INVALID_PUBLIC_KEY;

		final boolean isRecipientAlsoMinter = getCreator().getAddress().equals(this.rewardShareTransactionData.getRecipient());
		final boolean isCancellingSharePercent = this.rewardShareTransactionData.getSharePercent() < 0;

		// Fee can be zero if self-share, and not cancelling
		if (isRecipientAlsoMinter && !isCancellingSharePercent && this.transactionData.getFee() >= 0)
			return ValidationResult.OK;

		return super.isFeeValid();
	}

	@Override
	public ValidationResult isValid() throws DataException {
		final int disableRs = BlockChain.getInstance().getDisableRewardshareHeight();
		final int enableRs = BlockChain.getInstance().getEnableRewardshareHeight();
		int blockchainHeight = this.repository.getBlockRepository().getBlockchainHeight();

		// Check if reward share is disabled.
		if (blockchainHeight >= disableRs && blockchainHeight < enableRs)
			return ValidationResult.GENERAL_TEMPORARY_DISABLED;

		// Check reward share given to recipient. Negative is potentially OK to end a current reward-share. Zero also fine.
		if (this.rewardShareTransactionData.getSharePercent() > MAX_SHARE)
			return ValidationResult.INVALID_REWARD_SHARE_PERCENT;

		// Check reward-share public key is correct length
		if (this.rewardShareTransactionData.getRewardSharePublicKey().length != Transformer.PUBLIC_KEY_LENGTH)
			return ValidationResult.INVALID_PUBLIC_KEY;

		// Check recipient address is valid
		if (!Crypto.isValidAddress(this.rewardShareTransactionData.getRecipient()))
			return ValidationResult.INVALID_ADDRESS;

		PublicKeyAccount creator = getCreator();
		Account recipient = getRecipient();
		final boolean isCancellingSharePercent = this.rewardShareTransactionData.getSharePercent() < 0;

		// Creator themselves needs to be allowed to mint (unless cancelling)
		if (!isCancellingSharePercent && !creator.canMint(false))
			return ValidationResult.NOT_MINTING_ACCOUNT;

		// Qortal: special rules in play depending whether recipient is also minter
		final boolean isRecipientAlsoMinter = creator.getAddress().equals(recipient.getAddress());
		if (!isCancellingSharePercent && !isRecipientAlsoMinter && !creator.canRewardShare())
			return ValidationResult.ACCOUNT_CANNOT_REWARD_SHARE;

		// Look up any existing reward-share (using transaction's reward-share public key)
		RewardShareData existingRewardShareData = this.getExistingRewardShare();

		// If we have an existing reward-share then minter/recipient/reward-share-public-key should all match.
		// This is to prevent malicious actors using multiple (fake) reward-share public keys for the same minter/recipient combo,
		// or reusing the same reward-share public key for a different minter/recipient pair.
		if (existingRewardShareData != null && !this.doesRewardShareMatch(existingRewardShareData))
			return ValidationResult.INVALID_PUBLIC_KEY;

		if (existingRewardShareData == null) {
			// This is a new reward-share

			// Deleting a non-existent reward-share makes no sense
			if (isCancellingSharePercent)
				return ValidationResult.REWARD_SHARE_UNKNOWN;

			// Check the minting account hasn't reach maximum number of reward-shares
			int rewardShareCount = this.repository.getAccountRepository().countRewardShares(creator.getPublicKey());
			int selfShareCount = this.repository.getAccountRepository().countSelfShares(creator.getPublicKey());

			int maxRewardShares = BlockChain.getInstance().getMaxRewardSharesAtTimestamp(this.rewardShareTransactionData.getTimestamp());
			if (creator.isFounder())
				// Founders have a different limit
				maxRewardShares = BlockChain.getInstance().getMaxRewardSharesPerFounderMintingAccount();

			if (rewardShareCount >= maxRewardShares)
				return ValidationResult.MAXIMUM_REWARD_SHARES;

			// When filling all reward share slots, one must be a self share (after feature trigger timestamp)
			if (this.rewardShareTransactionData.getTimestamp() >= BlockChain.getInstance().getRewardShareLimitTimestamp())
				if (!isRecipientAlsoMinter && rewardShareCount == maxRewardShares-1 && selfShareCount == 0)
					return ValidationResult.MAXIMUM_REWARD_SHARES;

		} else {
			// This transaction intends to modify/terminate an existing reward-share

			// Modifying an existing self-share is pointless and forbidden (due to 0 fee). Deleting self-share is OK though.
			if (isRecipientAlsoMinter && !isCancellingSharePercent)
				return ValidationResult.SELF_SHARE_EXISTS;
		}

		// Check creator has enough funds
		if (this.rewardShareTransactionData.getTimestamp() >= BlockChain.getInstance().getFeeValidationFixTimestamp())
			if (creator.getConfirmedBalance(Asset.QORT) < this.rewardShareTransactionData.getFee())
				return ValidationResult.NO_BALANCE;

		else if (!(isRecipientAlsoMinter && existingRewardShareData == null))
			if (creator.getConfirmedBalance(Asset.QORT) < this.rewardShareTransactionData.getFee())
				return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public boolean isConfirmableAtHeight(int height) {
		final int startV2 = BlockChain.getInstance().getSelfSponsorshipAlgoV2Height() - 15;
		final int startV3 = BlockChain.getInstance().getSelfSponsorshipAlgoV3Height() - 15;
		final int endV2 = BlockChain.getInstance().getSelfSponsorshipAlgoV2Height() + 10;
		final int endV3 = BlockChain.getInstance().getSelfSponsorshipAlgoV3Height() + 10;

		if (height >= BlockChain.getInstance().getUnconfirmableRewardSharesHeight()) {
			// Not confirmable in online accounts or distribution blocks
			if (Block.isOnlineAccountsBlock(height) || Block.isBatchRewardDistributionBlock(height)) {
				return false;
			}
		}

		if (height >= startV2 && height <= endV2) {
			// Not confirmable on algo V2 run
			return false;
		}

		if (height >= startV3 && height <= endV3) {
			// Not confirmable on algo V3 run
			return false;
		}

		return true;
	}

	@Override
	public void process() throws DataException {
		PublicKeyAccount mintingAccount = getMintingAccount();

		// Grab any previous share info for orphaning purposes
		RewardShareData rewardShareData = this.repository.getAccountRepository().getRewardShare(mintingAccount.getPublicKey(),
				this.rewardShareTransactionData.getRecipient());

		if (rewardShareData != null)
			this.rewardShareTransactionData.setPreviousSharePercent(rewardShareData.getSharePercent());

		// Save this transaction, with previous share info
		this.repository.getTransactionRepository().save(this.rewardShareTransactionData);

		final boolean isSharePercentNegative = this.rewardShareTransactionData.getSharePercent() < 0;

		// Negative share is actually a request to delete existing reward-share
		if (isSharePercentNegative) {
			this.repository.getAccountRepository().delete(mintingAccount.getPublicKey(), this.rewardShareTransactionData.getRecipient());
		} else {
			// Save reward-share info
			rewardShareData = new RewardShareData(mintingAccount.getPublicKey(), mintingAccount.getAddress(),
					this.rewardShareTransactionData.getRecipient(), this.rewardShareTransactionData.getRewardSharePublicKey(),
					this.rewardShareTransactionData.getSharePercent());
			this.repository.getAccountRepository().save(rewardShareData);
		}
	}

	@Override
	public void processReferencesAndFees() throws DataException {
		super.processReferencesAndFees();

		// If reward-share recipient has no last-reference then use this transaction's signature as last-reference so they can spend their block rewards
		Account recipient = new Account(this.repository, this.rewardShareTransactionData.getRecipient());
		if (recipient.getLastReference() == null)
			recipient.setLastReference(this.rewardShareTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert
		PublicKeyAccount mintingAccount = getMintingAccount();

		if (this.rewardShareTransactionData.getPreviousSharePercent() != null) {
			// Revert previous sharing arrangement
			RewardShareData rewardShareData = new RewardShareData(mintingAccount.getPublicKey(), mintingAccount.getAddress(),
					this.rewardShareTransactionData.getRecipient(), this.rewardShareTransactionData.getRewardSharePublicKey(),
					this.rewardShareTransactionData.getPreviousSharePercent());

			this.repository.getAccountRepository().save(rewardShareData);
		} else {
			// No previous arrangement so simply delete
			this.repository.getAccountRepository().delete(mintingAccount.getPublicKey(), this.rewardShareTransactionData.getRecipient());
		}

		// Save this transaction, with removed previous share info
		this.rewardShareTransactionData.setPreviousSharePercent(null);
		this.repository.getTransactionRepository().save(this.rewardShareTransactionData);
	}

	@Override
	public void orphanReferencesAndFees() throws DataException {
		super.orphanReferencesAndFees();

		// If recipient didn't have a last-reference prior to this transaction then remove it
		Account recipient = new Account(this.repository, this.rewardShareTransactionData.getRecipient());
		if (Arrays.equals(recipient.getLastReference(), this.rewardShareTransactionData.getSignature()))
			recipient.setLastReference(null);
	}

}
