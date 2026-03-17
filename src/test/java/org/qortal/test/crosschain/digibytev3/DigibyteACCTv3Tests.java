package org.qortal.test.crosschain.digibytev3;

import com.google.common.hash.HashCode;
import org.qortal.crosschain.ACCT;
import org.qortal.crosschain.DigibyteACCTv3;
import org.qortal.test.crosschain.ACCTTests;

public class DigibyteACCTv3Tests extends ACCTTests {

	public static final byte[] digibytePublicKeyHash = HashCode.fromString("bb00bb11bb22bb33bb44bb55bb66bb77bb88bb99").asBytes();
	private static final String SYMBOL = "DGB";
	private static final String NAME = "DigiByte";

	@Override
	protected byte[] getPublicKey() {
		return digibytePublicKeyHash;
	}

	@Override
	protected byte[] buildQortalAT(String address, byte[] publicKey, long redeemAmount, long foreignAmount, int tradeTimeout) {
		return DigibyteACCTv3.buildQortalAT(address, publicKey, redeemAmount, foreignAmount, tradeTimeout);
	}

	@Override
	protected ACCT getInstance() {
		return DigibyteACCTv3.getInstance();
	}

	@Override
	protected int calcRefundTimeout(long partnersOfferMessageTransactionTimestamp, int lockTimeA) {
		return DigibyteACCTv3.calcRefundTimeout(partnersOfferMessageTransactionTimestamp, lockTimeA);
	}

	@Override
	protected byte[] buildTradeMessage(String address, byte[] publicKey, byte[] hashOfSecretA, int lockTimeA, int refundTimeout) {
		return DigibyteACCTv3.buildTradeMessage(address, publicKey, hashOfSecretA, lockTimeA, refundTimeout);
	}

	@Override
	protected byte[] buildRedeemMessage(byte[] secretA, String address) {
		return DigibyteACCTv3.buildRedeemMessage(secretA, address);
	}

	@Override
	protected byte[] getCodeBytesHash() {
		return DigibyteACCTv3.CODE_BYTES_HASH;
	}

	@Override
	protected String getSymbol() {
		return SYMBOL;
	}

	@Override
	protected String getName() {
		return NAME;
	}
}
