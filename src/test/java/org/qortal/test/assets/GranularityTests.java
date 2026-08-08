package org.qortal.test.assets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.asset.Order;
import org.qortal.repository.DataException;
import org.qortal.test.common.Common;
import org.qortal.utils.Amounts;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

/** Check granularity adjustment values. */
public class GranularityTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	public void testDivisibleGranularities() {
		// Price 1/12 is rounded down to 0.08333333.
		// To keep [divisible] amount * 0.08333333 to nearest 0.00000001 then amounts need to be multiples of 1.00000000.
		testGranularity(true, true, "1", "12", "1");

		// Any amount * 12 will be valid for divisible asset so granularity is 0.00000001
		testGranularity(true, true, "12", "1", "0.00000001");
	}

	@Test
	public void testIndivisibleGranularities() {
		// Price 1/10 is 0.10000000.
		// To keep amount * 0.1 to nearest 1 then amounts need to be multiples of 10.
		testGranularity(false, false, "1", "10", "10");

		// Price is 50307/123 which is 409
		// Any [indivisible] amount * 409 will be valid for divisible asset to granularity is 1
		testGranularity(false, false, "50307", "123", "1");
	}

	@Test
	public void testMixedDivisibilityGranularities() {
		// Price 1/800 is 0.00125000
		// Amounts are indivisible so must be integer.
		// Return-amounts are divisible and can be fractional.
		// So even though amount needs to be multiples of 1.00000000,
		// return-amount will always end up being valid.
		// Thus at price 0.00125000 we expect granularity to be 1
		testGranularity(false, true, "1", "800", "1");

		// Price 1/800 is 0.00125000
		// Amounts are divisible so can be fractional.
		// Return-amounts are indivisible so must be integer.
		// So even though amount can be multiples of 0.00000001,
		// return-amount needs to be multiples of 1.00000000
		// Thus at price 0.00125000 we expect granularity to be 800
		testGranularity(true, false, "1", "800", "800");

		// Price 800
		// Amounts are indivisible so must be integer.
		// Return-amounts are divisible so can be fractional.
		// So even though amount needs to be multiples of 1.00000000,
		// return-amount will always end up being valid.
		// Thus at price 800 we expect granularity to be 1
		testGranularity(false, true, "800", "1", "1");

		// Price 800
		// Amounts are divisible so can be fractional.
		// Return-amounts are indivisible so must be integer.
		// So even though amount can be multiples of 0.00000001,
		// return-amount needs to be multiples of 1.00000000
		// Thus at price 800 we expect granularity to be 0.00125000
		testGranularity(true, false, "800", "1", "0.00125000");
	}

	private void testGranularity(boolean isAmountAssetDivisible, boolean isReturnAssetDivisible, String dividendStr, String divisorStr, String expectedGranularityStr) {
		long dividend = toUnscaledLong(dividendStr);
		long divisor = toUnscaledLong(divisorStr);
		long expectedGranularity = toUnscaledLong(expectedGranularityStr);

		long price = Amounts.scaledDivide(dividend, divisor);

		long granularity = Order.calculateAmountGranularity(isAmountAssetDivisible, isReturnAssetDivisible, price);
		assertEquals("Granularity incorrect", expectedGranularity, granularity);
	}

	private long toUnscaledLong(String value) {
		return new BigDecimal(value).setScale(8).unscaledValue().longValue();
	}

}
