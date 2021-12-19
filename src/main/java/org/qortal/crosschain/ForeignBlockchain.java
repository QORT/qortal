package org.qortal.crosschain;

public interface ForeignBlockchain {

	public boolean isValidAddress(String address);

	public boolean isValidWalletKey(String walletKey);

	public long getMinimumOrderAmount();

}
