package org.qortal.utils;

import com.google.common.base.CharMatcher;
import com.ibm.icu.text.CaseMap;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.UnicodeSet;
import net.codebox.homoglyph.HomoglyphBuilder;

import java.io.*;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.*;

public abstract class Unicode {

	public static final String NO_BREAK_SPACE = "\u00a0";

	public static final String ZERO_WIDTH_SPACE = "\u200b";
	public static final String ZERO_WIDTH_NON_JOINER = "\u200c";
	public static final String ZERO_WIDTH_JOINER = "\u200d";
	public static final String WORD_JOINER = "\u2060";
	public static final String ZERO_WIDTH_NO_BREAK_SPACE = "\ufeff";

	public static final CharMatcher ZERO_WIDTH_CHAR_MATCHER = CharMatcher.anyOf(ZERO_WIDTH_SPACE + ZERO_WIDTH_NON_JOINER + ZERO_WIDTH_JOINER + WORD_JOINER + ZERO_WIDTH_NO_BREAK_SPACE);
	private static final UnicodeSet removableUniset = new UnicodeSet("[[:Mark:][:Other:]]").freeze();


	private static int[] homoglyphCodePoints;
	private static int[] reducedCodePoints;

	private static final String CHAR_CODES_FILE = "/char_codes.txt";

	static {
		buildHomoglyphCodePointArrays();
	}

	/** Returns string in Unicode canonical normalized form (NFKC),<br>
	 * with zero-width spaces/joiners removed,<br>
	 * leading/trailing whitespace trimmed<br>
	 * and all other whitespace blocks collapsed into a single space character.
	 * <p>
	 * Example: <tt><b>[ZWS]</b></tt> means zero-width space
	 * <ul>
	 * <li><tt>"  powdered <b>[TAB]</b> to<b>[ZWS]</b>ast  "</tt> becomes <tt>"powdered toast"</tt></li>
	 * </ul>
	 * <p>
	 * @see Form#NFKC
	 * @see Unicode#removeZeroWidth(String)
	 * @see CharMatcher#whitespace()
	 * @see CharMatcher#trimAndCollapseFrom(CharSequence, char)
	 */
	public static String normalize(String input) {
		String output;

		// Normalize using NFKC to recompose in canonical form
		output = Normalizer.normalize(input, Form.NFKC);

		// Remove zero-width code-points, used for rendering
		output = removeZeroWidth(output);

		// Normalize whitespace
		output = CharMatcher.whitespace().trimAndCollapseFrom(output, ' ');

		return output;
	}

	/** Returns string after normalization,<br>
	 * conversion to lowercase (locale insensitive)<br>
	 * and homoglyphs replaced with simpler, reduced codepoints.
	 * <p>
	 * Example:
	 * <ul>
	 * <li><tt>"  T&Omicron;&Aacute;ST "</tt> becomes <tt>"toast"</tt>
	 * </ul>
	 * <p>
	 * @see Form#NFKC
	 * @see Unicode#removeZeroWidth(String)
	 * @see CharMatcher#whitespace()
	 * @see CharMatcher#trimAndCollapseFrom(CharSequence, char)
	 * @see String#toLowerCase(Locale)
	 * @see Locale#ROOT
	 * @see Unicode#reduceHomoglyphs(String)
	 */
	public static String sanitize(String input) {
		String output;

		// Normalize using NFKD to decompose into individual combining code points
		output = Normalizer2.getNFKDInstance().normalize(input);

		// Remove zero-width code-points, used for rendering
		output = removeZeroWidth(output);

		// Normalize whitespace
		output = CharMatcher.whitespace().trimAndCollapseFrom(output, ' ');

		// Remove accents, combining marks - see https://www.unicode.org/reports/tr44/#GC_Values_Table
		output = removableUniset.stripFrom(output, true);

		// Convert to lowercase
		output = CaseMap.toLower().apply(Locale.ROOT, output);

		// Reduce homoglyphs
		output = reduceHomoglyphs(output);

		return output;
	}

	public static String removeZeroWidth(String input) {
		return ZERO_WIDTH_CHAR_MATCHER.removeFrom(input);
	}

	public static String reduceHomoglyphs(String input) {
		CodePoints codePoints = new CodePoints(input);
		final int length = codePoints.getLength();

		for (int i = 0; i < length; ++i) {
			int inputCodePoint = codePoints.getValue(i);

			int index = Arrays.binarySearch(homoglyphCodePoints, inputCodePoint);
			if (index >= 0)
				codePoints.setValue(i, reducedCodePoints[index]);
		}

		return codePoints.toString();
	}

	private static void buildHomoglyphCodePointArrays() {
		final InputStream is = HomoglyphBuilder.class.getResourceAsStream(CHAR_CODES_FILE);

		if (is == null)
			throw new MissingResourceException("Unable to read " + CHAR_CODES_FILE, HomoglyphBuilder.class.getName(),
					CHAR_CODES_FILE);

		final Reader reader = new InputStreamReader(is);

		Map<Integer, Integer> homoglyphReductions = new TreeMap<>();

		try (final BufferedReader bufferedReader = new BufferedReader(reader)) {
			String line;

			while ((line = bufferedReader.readLine()) != null) {
				line = line.trim();

				if (line.startsWith("#") || line.isEmpty())
					continue;

				String[] charCodes = line.split(",");

				// We consider the first charCode to be the 'reduced' form
				int reducedCodepoint;
				try {
					reducedCodepoint = Integer.parseInt(charCodes[0], 16);
				} catch (NumberFormatException ex) {
					// ignore badly formatted lines
					continue;
				}

				// Map remaining charCodes
				for (int i = 1; i < charCodes.length; ++i)
					try {
						int homoglyphCodepoint = Integer.parseInt(charCodes[i], 16);

						homoglyphReductions.put(homoglyphCodepoint, reducedCodepoint);
					} catch (NumberFormatException ex) {
						// ignore
					}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		homoglyphCodePoints = homoglyphReductions.keySet().stream().mapToInt(i -> i).toArray();
		reducedCodePoints = homoglyphReductions.values().stream().mapToInt(i -> i).toArray();
	}

	private static class CodePoints {
		private final int[] codepointArray;

		public CodePoints(String text) {
			final List<Integer> codepointList = new ArrayList<>();

			int codepoint;
			for (int offset = 0; offset < text.length(); offset += Character.charCount(codepoint)) {
				codepoint = text.codePointAt(offset);
				codepointList.add(codepoint);
			}

			this.codepointArray = codepointList.stream().mapToInt(i -> i).toArray();
		}

		public int getValue(int i) {
			return codepointArray[i];
		}

		public void setValue(int i, int codepoint) {
			codepointArray[i] = codepoint;
		}

		public int getLength() {
			return codepointArray.length;
		}

		public String toString() {
			final StringBuilder sb = new StringBuilder(this.codepointArray.length);

			for (int i = 0; i < this.codepointArray.length; i++)
				sb.appendCodePoint(this.codepointArray[i]);

			return sb.toString();
		}
	}

}
