package org.qortal.crosschain;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import java.util.Map;

public enum AcctMode {
	OFFERING(0), TRADING(1), CANCELLED(2), REFUNDED(3), REDEEMED(4);

	public final int value;
	private static final Map<Integer, AcctMode> map = stream(AcctMode.values()).collect(toMap(mode -> mode.value, mode -> mode));

	AcctMode(int value) {
		this.value = value;
	}

	public static AcctMode valueOf(int value) {
		return map.get(value);
	}
}