package org.qortal.crosschain;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.qortal.utils.ByteArray;
import org.qortal.utils.Triple;

public enum SupportedBlockchain {

	BITCOIN(Arrays.asList(
				Triple.valueOf(BitcoinACCTv1.NAME, BitcoinACCTv1.CODE_BYTES_HASH, BitcoinACCTv1::getInstance)
				// Could add improved BitcoinACCTv2 here in the future
			)) {
		@Override
		public ForeignBlockchain getInstance() {
			return Bitcoin.getInstance();
		}

		@Override
		public ACCT getLatestAcct() {
			return BitcoinACCTv1.getInstance();
		}
	},

	LITECOIN(Arrays.asList(
			Triple.valueOf(LitecoinACCTv1.NAME, LitecoinACCTv1.CODE_BYTES_HASH, LitecoinACCTv1::getInstance),
			Triple.valueOf(LitecoinACCTv2.NAME, LitecoinACCTv2.CODE_BYTES_HASH, LitecoinACCTv2::getInstance),
			Triple.valueOf(LitecoinACCTv3.NAME, LitecoinACCTv3.CODE_BYTES_HASH, LitecoinACCTv3::getInstance)
		)) {
		@Override
		public ForeignBlockchain getInstance() {
			return Litecoin.getInstance();
		}

		@Override
		public ACCT getLatestAcct() {
			return LitecoinACCTv3.getInstance();
		}
	},

	DOGECOIN(Arrays.asList(
			Triple.valueOf(DogecoinACCTv1.NAME, DogecoinACCTv1.CODE_BYTES_HASH, DogecoinACCTv1::getInstance),
			Triple.valueOf(DogecoinACCTv2.NAME, DogecoinACCTv2.CODE_BYTES_HASH, DogecoinACCTv2::getInstance),
			Triple.valueOf(DogecoinACCTv3.NAME, DogecoinACCTv3.CODE_BYTES_HASH, DogecoinACCTv3::getInstance)
		)) {
		@Override
		public ForeignBlockchain getInstance() {
			return Dogecoin.getInstance();
		}

		@Override
		public ACCT getLatestAcct() {
			return DogecoinACCTv3.getInstance();
		}
	};

	private static final Map<ByteArray, Supplier<ACCT>> supportedAcctsByCodeHash = Arrays.stream(SupportedBlockchain.values())
			.map(supportedBlockchain -> supportedBlockchain.supportedAccts)
			.flatMap(List::stream)
			.collect(Collectors.toUnmodifiableMap(triple -> new ByteArray(triple.getB()), Triple::getC));

	private static final Map<String, Supplier<ACCT>> supportedAcctsByName = Arrays.stream(SupportedBlockchain.values())
			.map(supportedBlockchain -> supportedBlockchain.supportedAccts)
			.flatMap(List::stream)
			.collect(Collectors.toUnmodifiableMap(Triple::getA, Triple::getC));

	private static final Map<String, SupportedBlockchain> blockchainsByName = Arrays.stream(SupportedBlockchain.values())
			.collect(Collectors.toUnmodifiableMap(Enum::name, blockchain -> blockchain));

	private final List<Triple<String, byte[], Supplier<ACCT>>> supportedAccts;

	SupportedBlockchain(List<Triple<String, byte[], Supplier<ACCT>>> supportedAccts) {
		this.supportedAccts = supportedAccts;
	}

	public abstract ForeignBlockchain getInstance();
	public abstract ACCT getLatestAcct();

	public static Map<ByteArray, Supplier<ACCT>> getAcctMap() {
		return supportedAcctsByCodeHash;
	}

	public static SupportedBlockchain fromString(String name) {
		return blockchainsByName.get(name);
	}

	public static Map<ByteArray, Supplier<ACCT>> getFilteredAcctMap(SupportedBlockchain blockchain) {
		if (blockchain == null)
			return getAcctMap();

		return blockchain.supportedAccts.stream()
				.collect(Collectors.toUnmodifiableMap(triple -> new ByteArray(triple.getB()), Triple::getC));
	}

	public static Map<ByteArray, Supplier<ACCT>> getFilteredAcctMap(String specificBlockchain) {
		if (specificBlockchain == null)
			return getAcctMap();

		SupportedBlockchain blockchain = blockchainsByName.get(specificBlockchain);
		if (blockchain == null)
			return Collections.emptyMap();

		return getFilteredAcctMap(blockchain);
	}

	public static ACCT getAcctByCodeHash(byte[] codeHash) {
		ByteArray wrappedCodeHash = new ByteArray(codeHash);

		Supplier<ACCT> acctInstanceSupplier = supportedAcctsByCodeHash.get(wrappedCodeHash);

		if (acctInstanceSupplier == null)
			return null;

		return acctInstanceSupplier.get();
	}

	public static ACCT getAcctByName(String acctName) {
		Supplier<ACCT> acctInstanceSupplier = supportedAcctsByName.get(acctName);

		if (acctInstanceSupplier == null)
			return null;

		return acctInstanceSupplier.get();
	}

}
