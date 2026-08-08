package org.qortal.test;

import org.junit.Before;
import org.junit.Test;
import org.qortal.utils.ByteArray;

import java.util.*;

import static org.junit.Assert.*;

public class ByteArrayTests {

	private static List<byte[]> testValues;

	@Before
	public void createTestValues() {
		Random random = new Random();

		testValues = new ArrayList<>();
		for (int i = 0; i < 5; ++i) {
			byte[] testValue = new byte[32];
			random.nextBytes(testValue);
			testValues.add(testValue);
		}
	}

	private static void fillMap(Map<ByteArray, String> map) {
		for (byte[] testValue : testValues)
			map.put(ByteArray.wrap(testValue), String.valueOf(map.size()));
	}

	private static byte[] dup(byte[] value) {
		return Arrays.copyOf(value, value.length);
	}

	@Test
	@SuppressWarnings("unlikely-arg-type")
	public void testOriginatingIssue() {
		Map<byte[], String> testMap = new HashMap<>();

		byte[] someValue = testValues.get(3);
		testMap.put(someValue, "someValue");

		byte[] copiedValue = dup(someValue);

		// Show that a byte[] with same values is not found
		System.out.printf("byte[] hashCode: 0x%08x%n", someValue.hashCode());
		System.out.printf("duplicated byte[] hashCode: 0x%08x%n", copiedValue.hashCode());

		/*
		 * Unfortunately this doesn't work because HashMap::containsKey compares hashCodes first,
		 * followed by object references, and copiedValue.hashCode() will never match someValue.hashCode().
		 */
		assertFalse("byte[] with same values, but difference reference, not found", testMap.containsKey(copiedValue));
	}

	@Test
	public void testSameContentReference() {
		// Create two objects, which will have different references, but same content references.
		byte[] testValue = testValues.get(0);
		ByteArray ba1 = ByteArray.wrap(testValue);
		ByteArray ba2 = ByteArray.wrap(testValue);

		// Confirm JVM-assigned references are different
		assertNotSame(ba1, ba2);

		// Confirm "equals" works as intended
		assertTrue("equals did not return true", ba1.equals(ba2));
		assertEquals("ba1 not equal to ba2", ba1, ba2);

		// Confirm "hashCode" results match
		assertEquals("hashCodes do not match", ba1.hashCode(), ba2.hashCode());
	}

	@Test
	public void testSameWrappedContentValue() {
		// Create two objects, which will have different references, and different content references, but same content values.
		byte[] testValue = testValues.get(0);
		ByteArray ba1 = ByteArray.wrap(testValue);

		byte[] copiedValue = dup(testValue);
		ByteArray ba2 = ByteArray.wrap(copiedValue);

		// Confirm JVM-assigned references are different
		assertNotSame(ba1, ba2);

		// Confirm "equals" works as intended
		assertTrue("equals did not return true", ba1.equals(ba2));
		assertEquals("ba1 not equal to ba2", ba1, ba2);

		// Confirm "hashCode" results match
		assertEquals("hashCodes do not match", ba1.hashCode(), ba2.hashCode());
	}

	@Test
	public void testSameCopiedContentValue() {
		// Create two objects, which will have different references, and different content references, but same content values.
		byte[] testValue = testValues.get(0);
		ByteArray ba1 = ByteArray.wrap(testValue);
		ByteArray ba2 = ByteArray.copyOf(testValue);

		// Confirm JVM-assigned references are different
		assertNotSame(ba1, ba2);

		// Confirm "equals" works as intended
		assertTrue("equals did not return true", ba1.equals(ba2));
		assertEquals("ba1 not equal to ba2", ba1, ba2);

		// Confirm "hashCode" results match
		assertEquals("hashCodes do not match", ba1.hashCode(), ba2.hashCode());
	}

	@Test
	@SuppressWarnings("unlikely-arg-type")
	public void testCompareBoxedWithPrimitive() {
		byte[] testValue = testValues.get(0);
		ByteArray wrappedByteArray = ByteArray.wrap(testValue);

		byte[] copiedValue = dup(testValue);
		ByteArray copiedByteArray = ByteArray.copyOf(copiedValue);

		// Confirm "equals" works as intended
		assertTrue("equals did not return true", wrappedByteArray.equals(copiedValue));
		assertEquals("boxed not equal to primitive", wrappedByteArray, copiedValue);

		assertTrue("equals did not return true", copiedByteArray.equals(testValue));
		assertEquals("boxed not equal to primitive", copiedByteArray, testValue);
	}

	@Test
	@SuppressWarnings("unlikely-arg-type")
	public void testHashMapContainsKey() {
		Map<ByteArray, String> testMap = new HashMap<>();
		fillMap(testMap);

		// Create new ByteArray object with an existing value.
		byte[] copiedValue = dup(testValues.get(3));
		ByteArray ba = ByteArray.wrap(copiedValue);

		// Confirm object can be found in map
		assertTrue("ByteArray not found in map", testMap.containsKey(ba));

		assertTrue("boxed not equal to primitive", ba.equals(copiedValue));

		/*
		 * Unfortunately this doesn't work because HashMap::containsKey compares hashCodes first,
		 * followed by object references, and copiedValue.hashCode() will never match ba.hashCode().
		 */
		assertFalse("Primitive shouldn't be found in HashMap", testMap.containsKey(copiedValue));
	}

	@Test
	@SuppressWarnings("unlikely-arg-type")
	public void testTreeMapContainsKey() {
		Map<ByteArray, String> testMap = new TreeMap<>();
		fillMap(testMap);

		// Create new ByteArray object with an existing value.
		byte[] copiedValue = dup(testValues.get(3));
		ByteArray ba = ByteArray.wrap(copiedValue);

		// Confirm object can be found in map
		assertTrue("ByteArray not found in map", testMap.containsKey(ba));

		assertTrue("boxed not equal to primitive", ba.equals(copiedValue));

		/*
		 * Unfortunately this doesn't work because TreeMap::containsKey(byte[]) wants to cast byte[] to
		 * Comparable<? super ByteArray> and byte[] does not fit <? super ByteArray>
		 * so this throws a ClassCastException.
		 */
		try {
			assertFalse("Primitive shouldn't be found in TreeMap", testMap.containsKey(copiedValue));
			fail();
		} catch (ClassCastException e) {
			// Expected
		}
	}

	@Test
	@SuppressWarnings("unlikely-arg-type")
	public void testArrayListContains() {
		// Create new ByteArray object with an existing value.
		byte[] copiedValue = dup(testValues.get(3));
		ByteArray ba = ByteArray.wrap(copiedValue);

		// Confirm object can be found in list
		assertTrue("ByteArray not found in map", testValues.contains(ba));

		assertTrue("boxed not equal to primitive", ba.equals(copiedValue));

		/*
		 * Unfortunately this doesn't work because ArrayList::contains performs
		 * copiedValue.equals(byte[]) for each byte[] in testValues, and byte[].equals()
		 * simply compares object references, so will never match any ByteArray.
		 */
		assertFalse("Primitive shouldn't be found in ArrayList", testValues.contains(copiedValue));
	}

	@Test
	public void debugBoxedVersusPrimitive() {
		byte[] testValue = testValues.get(0);
		ByteArray ba1 = ByteArray.wrap(testValue);

		byte[] copiedValue = dup(testValue);

		System.out.printf("Primitive hashCode: 0x%08x%n", testValue.hashCode());
		System.out.printf("Boxed hashCode: 0x%08x%n", ba1.hashCode());
		System.out.printf("Duplicated primitive hashCode: 0x%08x%n", copiedValue.hashCode());
	}

	@Test
	public void testCompareTo() {
		ByteArray testValue0 = ByteArray.wrap(new byte[] { 0x00 });
		ByteArray testValue1 = ByteArray.wrap(new byte[] { 0x01 });
		ByteArray testValueFf = ByteArray.wrap(new byte[] {(byte) 0xFF});

		assertTrue("0 should be the same as 0", testValue0.compareTo(testValue0) == 0);
		assertTrue("0 should be before 1", testValue0.compareTo(testValue1) < 0);
		assertTrue("1 should be after 0", testValue1.compareTo(testValue0) > 0);
		assertTrue("FF should be after 0", testValueFf.compareTo(testValue0) > 0);
	}

}
