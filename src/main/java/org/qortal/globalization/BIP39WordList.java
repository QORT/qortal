package org.qortal.globalization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Providing multi-language BIP39 word lists, downloaded from https://github.com/bitcoin/bips/tree/master/bip-0039 */
public enum BIP39WordList {
	INSTANCE;

	private static final Logger LOGGER = LogManager.getLogger(BIP39WordList.class);

	private static final Map<String, List<String>> wordListsByLang = new HashMap<>();

	public synchronized List<String> getByLang(String lang) {
		List<String> wordList = wordListsByLang.get(lang);

		if (wordList == null && !wordListsByLang.containsKey(lang)) {
			ClassLoader loader = this.getClass().getClassLoader();

			try (InputStream inputStream = loader.getResourceAsStream("BIP39/wordlist_" + lang + ".txt")) {
				if (inputStream == null)
					LOGGER.warn(String.format("Can't locate '%s' BIP39 wordlist", lang));

				wordList = new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.toList());
			} catch (IOException e) {
				LOGGER.warn(String.format("Error reading '%s' BIP39 wordlist: %s", lang, e.getMessage()));
			}

			wordListsByLang.put(lang, wordList);
		}

		if (wordList != null)
			return Collections.unmodifiableList(wordList);
		else
			return null;
	}

}
