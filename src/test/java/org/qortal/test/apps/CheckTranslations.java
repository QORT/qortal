package org.qortal.test.apps;

import org.qortal.api.ApiError;
import org.qortal.globalization.Translator;
import org.qortal.transaction.Transaction.ValidationResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class CheckTranslations {

	private static final String[] SUPPORTED_LANGS = new String[] { "en", "de", "zh", "ru" };
	private static final Set<String> SYSTRAY_KEYS = Set.of("AUTO_UPDATE", "APPLYING_UPDATE_AND_RESTARTING", "BLOCK_HEIGHT",
			"BUILD_VERSION", "CHECK_TIME_ACCURACY", "CONNECTING", "CONNECTION", "CONNECTIONS", "CREATING_BACKUP_OF_DB_FILES",
			"DB_BACKUP", "DB_CHECKPOINT", "EXIT", "LITE_NODE", "MINTING_DISABLED", "MINTING_ENABLED", "PERFORMING_DB_CHECKPOINT",
			"SYNCHRONIZE_CLOCK", "SYNCHRONIZING_BLOCKCHAIN", "SYNCHRONIZING_CLOCK");

	private static String failurePrefix;

	public static void main(String[] args) {
		for (String lang : SUPPORTED_LANGS) {
			System.out.println(String.format("\n# Checking '%s' translations", lang));

			Locale.setDefault(Locale.forLanguageTag(lang));
			failurePrefix = "!!" + lang + ":";

			checkTranslations("TransactionValidity", lang, Arrays.stream(ValidationResult.values()).map(value -> value.name()).collect(Collectors.toSet()));
			checkTranslations("ApiError", lang, Arrays.stream(ApiError.values()).map(value -> value.name()).collect(Collectors.toSet()));

			checkTranslations("SysTray", lang, SYSTRAY_KEYS);
		}
	}

	private static void checkTranslations(String className, String lang, Set<String> keys) {
		System.out.println(String.format("## Checking '%s' translations for %s", lang, className));

		Set<String> allKeys = Translator.INSTANCE.keySet(className, lang);
		if (allKeys == null) {
			System.out.println(String.format("NO '%s' translations for %s!", lang, className));
			allKeys = Collections.emptySet();
		}

		for (String key : keys) {
			allKeys.remove(key);

			String translation = Translator.INSTANCE.translate(className, lang, key);

			if (translation.startsWith(failurePrefix))
				System.out.println(String.format("Missing key '%s' in %s_%s.properties", key, className, lang));
		}

		// Any leftover keys?
		for (String key : allKeys)
			System.out.println(String.format("Extraneous key '%s' in %s_%s.properties", key, className, lang));
	}

}
