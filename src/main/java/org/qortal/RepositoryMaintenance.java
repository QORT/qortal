package org.qortal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qortal.controller.Controller;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryFactory;
import org.qortal.repository.RepositoryManager;
import org.qortal.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qortal.settings.Settings;

import java.security.Security;
import java.util.concurrent.TimeoutException;

public class RepositoryMaintenance {

	static {
		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
	}

	private static final Logger LOGGER = LogManager.getLogger(RepositoryMaintenance.class);

	public static void main(String[] args) {
		LOGGER.info("Repository maintenance starting up...");

		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		// Load/check settings, which potentially sets up blockchain config, etc.
		try {
			if (args.length > 0)
				Settings.fileInstance(args[0]);
			else
				Settings.getInstance();
		} catch (Throwable t) {
			LOGGER.error("Settings file error: " + t.getMessage());
			System.exit(2);
		}

		LOGGER.info("Opening repository");
		try {
			RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(Controller.getRepositoryUrl());
			RepositoryManager.setRepositoryFactory(repositoryFactory);
		} catch (DataException e) {
			// If exception has no cause then repository is in use by some other process.
			if (e.getCause() == null) {
				LOGGER.info("Repository in use by another process?");
			} else {
				LOGGER.error("Unable to start repository", e);
			}

			System.exit(1);
		}

		LOGGER.info("Starting repository periodic maintenance. This can take a while...");
		try (final Repository repository = RepositoryManager.getRepository()) {
			repository.performPeriodicMaintenance(null);

			LOGGER.info("Repository periodic maintenance completed");
		} catch (DataException | TimeoutException e) {
			LOGGER.error("Repository periodic maintenance failed", e);
		}

		try {
			LOGGER.info("Shutting down repository");
			RepositoryManager.closeRepositoryFactory();
		} catch (DataException e) {
			LOGGER.error("Error occurred while shutting down repository", e);
		}
	}

}
