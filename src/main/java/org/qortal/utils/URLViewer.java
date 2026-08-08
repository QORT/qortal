package org.qortal.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class URLViewer {

	private static final Logger LOGGER = LogManager.getLogger(URLViewer.class);

	public static void openWebpage(URI uri) throws Exception {
		Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;

		if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE))
			desktop.browse(uri);
	}

	public static void openWebpage(URL url) throws Exception {
		try {
			openWebpage(url.toURI());
		} catch (URISyntaxException e) {
			LOGGER.error(String.format("Invalid URL: %s", url.toString()));
		}
	}

}