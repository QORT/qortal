package org.qortal.gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ServiceConfigurationError;

public class Gui {

	private static final Logger LOGGER = LogManager.getLogger(Gui.class);
	private static Gui instance;

	private boolean isHeadless;
	private SplashFrame splashFrame = null;
	private SysTray sysTray = null;

	private Gui() {
		try {
			this.isHeadless = GraphicsEnvironment.isHeadless();

			if (!this.isHeadless) {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
						| UnsupportedLookAndFeelException e) {
					// Use whatever look-and-feel comes by default then
				}

				showSplash();
			}
		} catch (Exception e) {
			LOGGER.info("Unable to initialize GUI: {}", e.getMessage());
		}
	}

	private void showSplash() {
		LOGGER.trace(() -> "Splash");
		this.splashFrame = SplashFrame.getInstance();
	}

	protected static BufferedImage loadImage(String resourceName) {
		try (InputStream in = Gui.class.getResourceAsStream("/images/" + resourceName)) {
			return ImageIO.read(in);
		} catch (IllegalArgumentException | IOException | ServiceConfigurationError e) {
			LOGGER.warn(String.format("Couldn't locate image resource \"images/%s\"", resourceName));
			return null;
		}
	}

	public static Gui getInstance() {
		if (instance == null)
			instance = new Gui();

		return instance;
	}

	public void notifyRunning() {
		if (this.isHeadless)
			return;

		this.splashFrame.dispose();
		this.splashFrame = null;

		this.sysTray = SysTray.getInstance();
	}

	public void shutdown() {
		if (this.isHeadless)
			return;

		if (this.splashFrame != null)
			this.splashFrame.dispose();

		if (this.sysTray != null)
			this.sysTray.dispose();
	}

	public void fatalError(String title, String message) {
		if (this.isHeadless)
			return;

		shutdown();

		JOptionPane.showConfirmDialog(null, message, title, JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);

		System.exit(0);
	}

	public void fatalError(String title, Exception e) {
		String message = e.getLocalizedMessage();
		if (e.getCause() != null && e.getCause().getLocalizedMessage() != null)
			message += ": " + e.getCause().getLocalizedMessage();

		this.fatalError(title, message);
	}

}
