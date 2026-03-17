package org.qortal.gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.controller.Controller;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class SplashFrame {

	protected static final Logger LOGGER = LogManager.getLogger(SplashFrame.class);

	private static SplashFrame instance;
	private JFrame splashDialog;
	private SplashPanel splashPanel;

	@SuppressWarnings("serial")
	public static class SplashPanel extends JPanel {
		private BufferedImage image;

		private String defaultSplash = "Qlogo_512.png";

		private JLabel statusLabel;

		public SplashPanel() {
			image = Gui.loadImage(defaultSplash);

			setOpaque(true);
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBorder(new EmptyBorder(10, 10, 10, 10));
			setBackground(Color.BLACK);

			// Add logo
			JLabel imageLabel = new JLabel(new ImageIcon(image));
			imageLabel.setSize(new Dimension(300, 300));
			add(imageLabel);

			// Add spacing
			add(Box.createRigidArea(new Dimension(0, 16)));

			// Add status label
			String text = String.format("Starting Qortal Core v%s...", Controller.getInstance().getVersionStringWithoutPrefix());
			statusLabel = new JLabel(text, JLabel.CENTER);
			statusLabel.setMaximumSize(new Dimension(500, 50));
			statusLabel.setFont(new Font("Verdana", Font.PLAIN, 20));
			statusLabel.setBackground(Color.BLACK);
			statusLabel.setForeground(new Color(255, 255, 255, 255));
			statusLabel.setOpaque(true);
			statusLabel.setBorder(null);
			add(statusLabel);
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(500, 580);
		}

		public void updateStatus(String text) {
			if (statusLabel != null) {
				statusLabel.setText(text);
			}
		}
	}

	private SplashFrame() {
		if (GraphicsEnvironment.isHeadless()) {
			return;
		}

		this.splashDialog = new JFrame();

		List<Image> icons = new ArrayList<>();
		icons.add(Gui.loadImage("icons/icon16.png"));
		icons.add(Gui.loadImage("icons/qortal_ui_tray_synced.png"));
		icons.add(Gui.loadImage("icons/qortal_ui_tray_syncing_time-alt.png"));
		icons.add(Gui.loadImage("icons/qortal_ui_tray_minting.png"));
		icons.add(Gui.loadImage("icons/qortal_ui_tray_syncing.png"));
		icons.add(Gui.loadImage("icons/icon64.png"));
		icons.add(Gui.loadImage("icons/Qlogo_128.png"));
		this.splashDialog.setIconImages(icons);

		this.splashPanel = new SplashPanel();
		this.splashDialog.getContentPane().add(this.splashPanel);
		this.splashDialog.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		this.splashDialog.setUndecorated(true);
		this.splashDialog.pack();
		this.splashDialog.setLocationRelativeTo(null);
		this.splashDialog.setBackground(Color.BLACK);
		this.splashDialog.setVisible(true);
	}

	public static SplashFrame getInstance() {
		if (instance == null)
			instance = new SplashFrame();

		return instance;
	}

	public void setVisible(boolean b) {
		this.splashDialog.setVisible(b);
	}

	public void dispose() {
		this.splashDialog.dispose();
	}

	public void updateStatus(String text) {
		if (this.splashPanel != null) {
			this.splashPanel.updateStatus(text);
		}
	}

}
