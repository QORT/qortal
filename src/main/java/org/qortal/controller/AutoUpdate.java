package org.qortal.controller;

import java.awt.TrayIcon.MessageType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.ApplyUpdate;
import org.qortal.api.ApiRequest;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.globalization.Translator;
import org.qortal.gui.SysTray;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.transaction.ArbitraryTransaction;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.Transformer;

import com.google.common.hash.HashCode;

/* NOTE: It is CRITICAL that we use OpenJDK and not Java SE because our uber jar repacks BouncyCastle which, in turn, unsigns BC causing it to be rejected as a security provider by Java SE. */

public class AutoUpdate extends Thread {

	public static final String JAR_FILENAME = "qortal.jar";
	public static final String NEW_JAR_FILENAME = "new-" + JAR_FILENAME;

	private static final Logger LOGGER = LogManager.getLogger(AutoUpdate.class);
	private static final long CHECK_INTERVAL = 20 * 60 * 1000L; // ms

	private static final int DEV_GROUP_ID = 1;
	private static final int UPDATE_SERVICE = 1;

	private static final int GIT_COMMIT_HASH_LENGTH = 20; // SHA-1
	private static final int EXPECTED_DATA_LENGTH = Transformer.TIMESTAMP_LENGTH + GIT_COMMIT_HASH_LENGTH + Transformer.SHA256_LENGTH;

	/** This byte value used to hide contents from deep-inspection firewalls in case they block updates. */
	public static final byte XOR_VALUE = (byte) 0x5a;

	private static AutoUpdate instance;

	private volatile boolean isStopping = false;

	private AutoUpdate() {
	}

	public static AutoUpdate getInstance() {
		if (instance == null)
			instance = new AutoUpdate();

		return instance;
	}

	@Override
	public void run() {
		Thread.currentThread().setName("Auto-update");

		long buildTimestamp = Controller.getInstance().getBuildTimestamp() * 1000L;
		boolean attemptedUpdate = false;

		while (!isStopping) {
			try {
				Thread.sleep(CHECK_INTERVAL);
			} catch (InterruptedException e) {
				return;
			}

			// Try to clean up any leftover downloads (but if have attempted update then don't delete new JAR)
			if (!attemptedUpdate)
				try {
					Path newJar = Paths.get(NEW_JAR_FILENAME);
					Files.deleteIfExists(newJar);
				} catch (IOException de) {
					// Whatever
				}

			// Look for "update" tx which is arbitrary tx in dev-group with service 1 and timestamp later than buildTimestamp
			try (final Repository repository = RepositoryManager.getRepository()) {
				byte[] signature = repository.getTransactionRepository().getLatestAutoUpdateTransaction(TransactionType.ARBITRARY, DEV_GROUP_ID, UPDATE_SERVICE);
				if (signature == null)
					continue;

				TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
				if (!(transactionData instanceof ArbitraryTransactionData))
					continue;

				// Transaction needs to be newer than this build
				if (transactionData.getTimestamp() <= buildTimestamp)
					continue;

				ArbitraryTransaction arbitraryTransaction = new ArbitraryTransaction(repository, transactionData);
				if (!arbitraryTransaction.isDataLocal())
					continue; // We can't access data

				// TODO: check arbitrary data length (pre-fetch) matches build timestamp (8) + git commit length (20) + sha256 hash length (32) = 60 bytes

				byte[] data = arbitraryTransaction.fetchData();
				if (data.length != EXPECTED_DATA_LENGTH) {
					LOGGER.debug(String.format("Arbitrary data length %d doesn't match %d", data.length, EXPECTED_DATA_LENGTH));
					continue;
				}

				ByteBuffer byteBuffer = ByteBuffer.wrap(data);

				long updateTimestamp = byteBuffer.getLong();
				if (updateTimestamp <= buildTimestamp)
					continue; // update is the same, or older, than current code

				byte[] commitHash = new byte[GIT_COMMIT_HASH_LENGTH];
				byteBuffer.get(commitHash);

				byte[] downloadHash = new byte[Transformer.SHA256_LENGTH];
				byteBuffer.get(downloadHash);

				LOGGER.info(String.format("Update's git commit hash: %s", HashCode.fromBytes(commitHash).toString()));

				String[] autoUpdateRepos = Settings.getInstance().getAutoUpdateRepos();
				for (String repo : autoUpdateRepos)
					if (attemptUpdate(commitHash, downloadHash, repo)) {
						// Consider ourselves updated so don't re-re-re-download
						buildTimestamp = System.currentTimeMillis();
						attemptedUpdate = true;
						break;
					}
			} catch (DataException e) {
				LOGGER.warn("Repository issue to find updates", e);
				// Keep going I guess...
			}
		}

		LOGGER.info("Stopping auto-update service");
	}

	public void shutdown() {
		isStopping = true;
		this.interrupt();
	}

	private static boolean attemptUpdate(byte[] commitHash, byte[] downloadHash, String repoBaseUri) {
		String repoUri = String.format(repoBaseUri, HashCode.fromBytes(commitHash).toString());
		LOGGER.info(String.format("Fetching update from %s", repoUri));
		Path newJar = Paths.get(NEW_JAR_FILENAME);

		try (InputStream in = ApiRequest.fetchStream(repoUri)) {
			MessageDigest sha256;
			try {
				sha256 = MessageDigest.getInstance("SHA-256");
			} catch (NoSuchAlgorithmException e) {
				return true; // not repo's fault
			}

			// Save input stream into new JAR
			LOGGER.debug(String.format("Saving update from %s into %s", repoUri, newJar.toString()));

			try (OutputStream out = Files.newOutputStream(newJar)) {
				byte[] buffer = new byte[1024 * 1024];
				do {
					int nread = in.read(buffer);
					if (nread == -1)
						break;

					// Hash is based on XORed version
					sha256.update(buffer, 0, nread);

					// ReXOR before writing
					for (int i = 0; i < nread; ++i)
						buffer[i] ^= XOR_VALUE;

					out.write(buffer, 0, nread);
				} while (true);
				out.flush();

				// Check hash
				byte[] hash = sha256.digest();
				if (!Arrays.equals(downloadHash, hash)) {
					LOGGER.warn(String.format("Downloaded JAR's hash %s doesn't match %s", HashCode.fromBytes(hash).toString(), HashCode.fromBytes(downloadHash).toString()));

					try {
						Files.deleteIfExists(newJar);
					} catch (IOException de) {
						LOGGER.warn(String.format("Failed to delete download: %s", de.getMessage()));
					}

					return false;
				}
			} catch (IOException e) {
				LOGGER.warn(String.format("Failed to save update from %s into %s: %s", repoUri, newJar.toString(), e.getMessage()));

				try {
					Files.deleteIfExists(newJar);
				} catch (IOException de) {
					LOGGER.warn(String.format("Failed to delete partial download: %s", de.getMessage()));
				}

				return false; // failed - try another repo
			}
		} catch (IOException e) {
			LOGGER.warn(String.format("Failed to fetch update from %s: %s", repoUri, e.getMessage()));
			return false; // failed - try another repo
		}

		// Give repository a chance to backup in case things go badly wrong (if enabled)
		if (Settings.getInstance().getRepositoryBackupInterval() > 0) {
			try {
				// Timeout if the database isn't ready for backing up after 60 seconds
				long timeout = 60 * 1000L;
				RepositoryManager.backup(true, "backup", timeout);

			} catch (TimeoutException e) {
				LOGGER.info("Attempt to backup repository failed due to timeout: {}", e.getMessage());
				// Continue with the auto update anyway...
			}
		}

		// Call ApplyUpdate to end this process (unlocking current JAR so it can be replaced)
		String javaHome = System.getProperty("java.home");
		LOGGER.debug(String.format("Java home: %s", javaHome));

		Path javaBinary = Paths.get(javaHome, "bin", "java");
		LOGGER.debug(String.format("Java binary: %s", javaBinary));

		try {
			List<String> javaCmd = new ArrayList<>();
			// Java runtime binary itself
			javaCmd.add(javaBinary.toString());

			// JVM arguments
			javaCmd.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());

			// Remove JNI options as they won't be supported by command-line 'java'
			// These are typically added by the AdvancedInstaller Java launcher EXE
			javaCmd.removeAll(Arrays.asList("abort", "exit", "vfprintf"));

			// Call ApplyUpdate using new JAR
			javaCmd.addAll(Arrays.asList("-cp", NEW_JAR_FILENAME, ApplyUpdate.class.getCanonicalName()));

			// Add command-line args saved from start-up
			String[] savedArgs = Controller.getInstance().getSavedArgs();
			if (savedArgs != null)
				javaCmd.addAll(Arrays.asList(savedArgs));

			LOGGER.info(String.format("Applying update with: %s", String.join(" ", javaCmd)));

			SysTray.getInstance().showMessage(Translator.INSTANCE.translate("SysTray", "AUTO_UPDATE"),
					Translator.INSTANCE.translate("SysTray", "APPLYING_UPDATE_AND_RESTARTING"),
					MessageType.INFO);

			new ProcessBuilder(javaCmd).start();

			return true; // applying update OK
		} catch (IOException e) {
			LOGGER.error(String.format("Failed to apply update: %s", e.getMessage()));

			try {
				Files.deleteIfExists(newJar);
			} catch (IOException de) {
				LOGGER.warn(String.format("Failed to delete update download: %s", de.getMessage()));
			}

			return true; // repo was okay, even if applying update failed
		}
	}

}
