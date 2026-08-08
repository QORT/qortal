package org.qortal.api.model;

import org.qortal.block.SelfSponsorshipAlgoV1Block;
import org.qortal.data.account.AccountData;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class AccountPenaltyStats {

	public Integer totalPenalties;
	public Integer maxPenalty;
	public Integer minPenalty;
	public String penaltyHash;

	protected AccountPenaltyStats() {
	}

	public AccountPenaltyStats(Integer totalPenalties, Integer maxPenalty, Integer minPenalty, String penaltyHash) {
		this.totalPenalties = totalPenalties;
		this.maxPenalty = maxPenalty;
		this.minPenalty = minPenalty;
		this.penaltyHash = penaltyHash;
	}

	public static AccountPenaltyStats fromAccounts(List<AccountData> accounts) {
		int totalPenalties = 0;
		Integer maxPenalty = null;
		Integer minPenalty = null;

		List<String> addresses = new ArrayList<>();
		for (AccountData accountData : accounts) {
			int penalty = accountData.getBlocksMintedPenalty();
			addresses.add(accountData.getAddress());
			totalPenalties++;

			// Penalties are expressed as a negative number, so the min and the max are reversed here
			if (maxPenalty == null || penalty < maxPenalty) maxPenalty = penalty;
			if (minPenalty == null || penalty > minPenalty) minPenalty = penalty;
		}

		String penaltyHash = SelfSponsorshipAlgoV1Block.getHash(addresses);
		return new AccountPenaltyStats(totalPenalties, maxPenalty, minPenalty, penaltyHash);
	}


	@Override
	public String toString() {
		return String.format("totalPenalties: %d, maxPenalty: %d, minPenalty: %d, penaltyHash: %s", totalPenalties, maxPenalty, minPenalty, penaltyHash == null ? "null" : penaltyHash);
	}
}
