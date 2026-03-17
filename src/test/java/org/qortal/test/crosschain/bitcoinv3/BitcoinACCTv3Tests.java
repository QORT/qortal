package org.qortal.test.crosschain.bitcoinv3;

import com.google.common.hash.HashCode;
import org.qortal.crosschain.ACCT;
import org.qortal.crosschain.BitcoinACCTv3;
import org.qortal.test.crosschain.ACCTTests;

public class BitcoinACCTv3Tests extends ACCTTests {

	public static final byte[] bitcoinPublicKeyHash = HashCode.fromString("bb00bb11bb22bb33bb44bb55bb66bb77bb88bb99").asBytes();
	private static final String SYMBOL = "BTC";
	private static final String NAME = "Bitcoin";

	@Override
	protected byte[] getPublicKey() {
		return bitcoinPublicKeyHash;
	}

	@Override
	protected byte[] buildQortalAT(String address, byte[] publicKey, long redeemAmount, long foreignAmount, int tradeTimeout) {
		return BitcoinACCTv3.buildQortalAT(address, publicKey, redeemAmount, foreignAmount, tradeTimeout);
	}

	@Override
	protected ACCT getInstance() {
		return BitcoinACCTv3.getInstance();
	}

	@Override
	protected int calcRefundTimeout(long partnersOfferMessageTransactionTimestamp, int lockTimeA) {
		return BitcoinACCTv3.calcRefundTimeout(partnersOfferMessageTransactionTimestamp, lockTimeA);
	}

	@Override
	protected byte[] buildTradeMessage(String address, byte[] publicKey, byte[] hashOfSecretA, int lockTimeA, int refundTimeout) {
		return BitcoinACCTv3.buildTradeMessage(address, publicKey, hashOfSecretA, lockTimeA, refundTimeout);
	}

	@Override
	protected byte[] buildRedeemMessage(byte[] secretA, String address) {
		return BitcoinACCTv3.buildRedeemMessage(secretA, address);
	}

	@Override
	protected byte[] getCodeBytesHash() {
		return BitcoinACCTv3.CODE_BYTES_HASH;
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
