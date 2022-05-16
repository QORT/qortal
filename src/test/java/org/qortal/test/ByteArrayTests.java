package org.qortal.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.qortal.utils.ByteArray;

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
			map.put(new ByteArray(testValue), String.valueOf(map.size()));
	}

	private static byte[] dup(byte[] value) {
		return Arrays.copyOf(value, value.length);
	}

	@Test
	public void testSameContentReference() {
		// Create two objects, which will have different references, but same content.
		byte[] testValue = testValues.get(0);
		ByteArray ba1 = new ByteArray(testValue);
		ByteArray ba2 = new ByteArray(testValue);

		// Confirm JVM-assigned references are different
		assertNotSame(ba1, ba2);

		// Confirm "equals" works as intended
		assertTrue("equals did not return true", ba1.equals(ba2));
		assertEquals("ba1 not equal to ba2", ba1, ba2);

		// Confirm "hashCode" results match
		assertEquals("hashCodes do not match", ba1.hashCode(), ba2.hashCode());
	}

	@Test
	public void testSameContentValue() {
		// Create two objects, which will have different references, but same content.
		byte[] testValue = testValues.get(0);
		ByteArray ba1 = new ByteArray(testValue);

		byte[] copiedValue = dup(testValue);
		ByteArray ba2 = new ByteArray(copiedValue);

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
		ByteArray ba1 = new ByteArray(testValue);

		byte[] copiedValue = dup(testValue);

		// Confirm "equals" works as intended
		assertTrue("equals did not return true", ba1.equals(copiedValue));
		assertEquals("boxed not equal to primitive", ba1, copiedValue);
	}

	@Test
	@SuppressWarnings("unlikely-arg-type")
	public void testHashMapContainsKey() {
		Map<ByteArray, String> testMap = new HashMap<>();
		fillMap(testMap);

		// Create new ByteArray object with an existing value.
		byte[] copiedValue = dup(testValues.get(3));
		ByteArray ba = new ByteArray(copiedValue);

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
		ByteArray ba = new ByteArray(copiedValue);

		// Confirm object can be found in map
		assertTrue("ByteArray not found in map", testMap.containsKey(ba));

		assertTrue("boxed not equal to primitive", ba.equals(copiedValue));

		/*
		 * Unfortunately this doesn't work because TreeMap::containsKey(x) wants to cast x to
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
		ByteArray ba = new ByteArray(copiedValue);

		// Confirm object can be found in list
		assertTrue("ByteArray not found in map", testValues.contains(ba));

		assertTrue("boxed not equal to primitive", ba.equals(copiedValue));

		/*
		 * Unfortunately this doesn't work because ArrayList::contains performs
		 * copiedValue.equals(x) for each x in testValues, and byte[].equals()
		 * simply compares object references, so will never match any ByteArray.
		 */
		assertFalse("Primitive shouldn't be found in ArrayList", testValues.contains(copiedValue));
	}

	@Test
	public void debugBoxedVersusPrimitive() {
		byte[] testValue = testValues.get(0);
		ByteArray ba1 = new ByteArray(testValue);

		byte[] copiedValue = dup(testValue);

		System.out.println(String.format("Primitive hashCode: 0x%08x", testValue.hashCode()));
		System.out.println(String.format("Boxed hashCode: 0x%08x", ba1.hashCode()));
		System.out.println(String.format("Duplicated primitive hashCode: 0x%08x", copiedValue.hashCode()));
	}

	@Test
	public void testCompareTo() {
		ByteArray testValue0 = new ByteArray(new byte[] { 0x00 });
		ByteArray testValue1 = new ByteArray(new byte[] { 0x01 });

		assertEquals("0 should be the same as 0", 0, testValue0.compareTo(testValue0));
		assertEquals("0 should be before 1", -1, testValue0.compareTo(testValue1));
		assertEquals("1 should be after 0", 1, testValue1.compareTo(testValue0));
	}

}
