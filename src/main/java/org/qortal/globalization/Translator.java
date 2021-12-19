package org.qortal.globalization;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.settings.Settings;

public enum Translator {
	INSTANCE;

	private static final Logger LOGGER = LogManager.getLogger(Translator.class);

	private static final Map<String, ResourceBundle> resourceBundles = new HashMap<>();

	public String translate(String className, String lang, String key, Object... args) {
		ResourceBundle resourceBundle = getOrLoadResourceBundle(className, lang);

		if (resourceBundle == null || !resourceBundle.containsKey(key))
			return "!!" + lang + ":" + className + "." + key + "!!";

		String template = resourceBundle.getString(key);
		try {
			return String.format(template, args);
		} catch (MissingFormatArgumentException e) {
			return template;
		}
	}

	public String translate(String className, String key) {
		return this.translate(className, Settings.getInstance().getLocaleLang(), key);
	}

	public Set<String> keySet(String className, String lang) {
		ResourceBundle resourceBundle = getOrLoadResourceBundle(className, lang);

		if (resourceBundle == null)
			return null;

		return resourceBundle.keySet();
	}

	private synchronized ResourceBundle getOrLoadResourceBundle(String className, String lang) {
		String bundleKey = className + ":" + lang;

		ResourceBundle resourceBundle = resourceBundles.get(bundleKey);
		if (resourceBundle != null || resourceBundles.containsKey(bundleKey))
			return resourceBundle;

		try {
			resourceBundle = ResourceBundle.getBundle("i18n." + className, Locale.forLanguageTag(lang));
		} catch (MissingResourceException e) {
			LOGGER.warn(String.format("Can't locate '%s' translation resource bundle for %s", lang, className));
			// Set to null then fall-through to storing in map so we don't emit warning more than once
			resourceBundle = null;
		}

		resourceBundles.put(bundleKey, resourceBundle);

		return resourceBundle;
	}

}
