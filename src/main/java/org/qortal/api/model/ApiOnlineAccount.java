package org.qortal.api.model;

import org.qortal.account.Account;
import org.qortal.repository.DataException;
import org.qortal.repository.RepositoryManager;
import org.qortal.repository.Repository;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class ApiOnlineAccount {

	protected long timestamp;
	protected byte[] signature;
	protected byte[] rewardSharePublicKey;
	protected String minterAddress;
	protected String recipientAddress;

	// Constructors

	// necessary for JAXB serialization
	protected ApiOnlineAccount() {
	}

	public ApiOnlineAccount(long timestamp, byte[] signature, byte[] rewardSharePublicKey, String minterAddress, String recipientAddress) {
		this.timestamp = timestamp;
		this.signature = signature;
		this.rewardSharePublicKey = rewardSharePublicKey;
		this.minterAddress = minterAddress;
		this.recipientAddress = recipientAddress;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public byte[] getPublicKey() {
		return this.rewardSharePublicKey;
	}

	public String getMinterAddress() {
		return this.minterAddress;
	}

	public String getRecipientAddress() {
		return this.recipientAddress;
	}

	public int getMinterLevelFromPublicKey() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return Account.getRewardShareEffectiveMintingLevel(repository, this.rewardSharePublicKey);
		} catch (DataException e) {
			return 0;
		}
	}

	public boolean getIsMember() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getGroupRepository().memberExists(694, getMinterAddress());
		} catch (DataException e) {
			return false;
		}
	}

	// JAXB special
	
	@XmlElement(name = "minterLevel")
	protected int getMinterLevel() {
		return getMinterLevelFromPublicKey();
	}

	@XmlElement(name = "isMinterMember")
	protected boolean getMinterMember() {
		return getIsMember();
	}
}
