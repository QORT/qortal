package org.qortal.test;

import org.junit.Test;
import org.qortal.utils.Unicode;

import static org.junit.Assert.*;
import static org.qortal.utils.Unicode.NO_BREAK_SPACE;
import static org.qortal.utils.Unicode.ZERO_WIDTH_SPACE;

public class UnicodeTests {

	@Test
	public void testWhitespace() {
		String input = "  " + NO_BREAK_SPACE + "test  ";

		String output = Unicode.normalize(input);

		assertEquals("trim & collapse failed", "test", output);
	}

	@Test
	public void testCaseComparison() {
		String input1 = "  " + NO_BREAK_SPACE + "test  ";
		String input2 = "  " + NO_BREAK_SPACE + "TEST  " + ZERO_WIDTH_SPACE;

		assertEquals("strings should match", Unicode.sanitize(input1), Unicode.sanitize(input2));
	}

	@Test
	public void testHomoglyph() {
		String omicron = "\u03bf";

		String input1 = "  " + NO_BREAK_SPACE + "toÁst  ";
		String input2 = "  " + NO_BREAK_SPACE + "t" + omicron + "ast  " + ZERO_WIDTH_SPACE;

		assertEquals("strings should match", Unicode.sanitize(input1), Unicode.sanitize(input2));
	}

	@Test
	public void testEmojis() {
		/*
		 * Emojis shouldn't reduce down to empty strings.
		 *
		 * 🥳 Face with Party Horn and Party Hat Emoji U+1F973
		 */
		String emojis = "\uD83E\uDD73";

		assertFalse(Unicode.sanitize(emojis).isBlank());
	}

	@Test
	public void testSanitize() {
		/*
		 * Check various code points that should be stripped out when sanitizing / reducing
		 */
		String enclosingCombiningMark = "\u1100\u1161\u20DD"; // \u20DD is an enclosing combining mark and should be removed
		String spacingMark = "\u0A39\u0A3f"; // \u0A3f is spacing combining mark and should be removed
		String nonspacingMark = "c\u0302"; // \u0302 is a non-spacing combining mark and should be removed

		assertNotSame(enclosingCombiningMark, Unicode.sanitize(enclosingCombiningMark));
		assertNotSame(spacingMark, Unicode.sanitize(spacingMark));
		assertNotSame(nonspacingMark, Unicode.sanitize(nonspacingMark));

		String control = "\u001B\u009E"; // \u001B and \u009E are a control codes
		String format = "\u202A\u2062"; // \u202A and \u2062 are zero-width formatting codes
		String surrogate = "\uD800\uDFFF"; // surrogates
		String privateUse = "\uE1E0"; // \uE000 - \uF8FF is private use area
		String unassigned = "\uFAFA"; // \uFAFA is currently unassigned

		assertTrue(Unicode.sanitize(control).isBlank());
		assertTrue(Unicode.sanitize(format).isBlank());
		assertTrue(Unicode.sanitize(surrogate).isBlank());
		assertTrue(Unicode.sanitize(privateUse).isBlank());
		assertTrue(Unicode.sanitize(unassigned).isBlank());
	}
}
