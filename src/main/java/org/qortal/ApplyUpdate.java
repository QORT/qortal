package org.qortal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qortal.api.ApiKey;
import org.qortal.api.ApiRequest;
import org.qortal.controller.AutoUpdate;
import org.qortal.settings.Settings;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Security;
import java.util.*;
import java.util.stream.Collectors;

import static org.qortal.controller.AutoUpdate.AGENTLIB_JVM_HOLDER_ARG;

public class ApplyUpdate {

	static {
		// This static block will be called before others if using ApplyUpdate.main()

		// Log into different files for auto-update - this has to be before LogManger.getLogger() calls
		System.setProperty("log4j2.filenameTemplate", "log-apply-update.txt");

		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
	}

	private static final Logger LOGGER = LogManager.getLogger(ApplyUpdate.class);
	private static final String JAR_FILENAME = AutoUpdate.JAR_FILENAME;
	private static final String NEW_JAR_FILENAME = AutoUpdate.NEW_JAR_FILENAME;
	private static final String WINDOWS_EXE_LAUNCHER = "qortal.exe";
	private static final String JAVA_TOOL_OPTIONS_NAME = "JAVA_TOOL_OPTIONS";
	private static final String JAVA_TOOL_OPTIONS_VALUE = "";

	private static final long CHECK_INTERVAL = 30 * 1000L; // ms
	private static final int MAX_ATTEMPTS = 12;

	public static void main(String[] args) {
		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		// Load/check settings, which potentially sets up blockchain config, etc.
		if (args.length > 0)
			Settings.fileInstance(args[0]);
		else
			Settings.getInstance();

		LOGGER.info("Applying update...");

		// Shutdown node using API
		if (!shutdownNode())
			return;

		// Replace JAR
		replaceJar();

		// Restart node
		restartNode(args);

		LOGGER.info("Exiting...");
	}

	private static boolean shutdownNode() {
		String baseUri = "http://localhost:" + Settings.getInstance().getApiPort() + "/";
		LOGGER.info(() -> String.format("Shutting down node using API via %s", baseUri));

		// The /admin/stop endpoint requires an API key, which may or may not be already generated
		boolean apiKeyNewlyGenerated = false;
		ApiKey apiKey = null;
		try {
			apiKey = new ApiKey();
			if (!apiKey.generated()) {
				apiKey.generate();
				apiKeyNewlyGenerated = true;
				LOGGER.info("Generated API key");
			}
		} catch (IOException e) {
			LOGGER.info("Error loading API key: {}", e.getMessage());
		}

		// Create GET params
		Map<String, String> params = new HashMap<>();
		if (apiKey != null) {
			params.put("apiKey", apiKey.toString());
		}

		// Attempt to stop the node
		int attempt;
		for (attempt = 0; attempt < MAX_ATTEMPTS; ++attempt) {
			final int attemptForLogging = attempt;
			LOGGER.info(() -> String.format("Attempt #%d out of %d to shutdown node", attemptForLogging + 1, MAX_ATTEMPTS));
			String response = ApiRequest.perform(baseUri + "admin/stop", params);
			if (response == null) {
				// No response - consider node shut down
				if (apiKeyNewlyGenerated) {
					// API key was newly generated for this auto update, so we need to remove it
					ApplyUpdate.removeGeneratedApiKey();
				}
				return true;
			}

			LOGGER.info(() -> String.format("Response from API: %s", response));

			try {
				Thread.sleep(CHECK_INTERVAL);
			} catch (InterruptedException e) {
				// We still need to check...
				break;
			}
		}

		if (apiKeyNewlyGenerated) {
			// API key was newly generated for this auto update, so we need to remove it
			ApplyUpdate.removeGeneratedApiKey();
		}

		if (attempt == MAX_ATTEMPTS) {
			LOGGER.error("Failed to shutdown node - giving up");
			return false;
		}

		return true;
	}

	private static void removeGeneratedApiKey() {
		try {
			LOGGER.info("Removing newly generated API key...");

			// Delete the API key since it was only generated for this auto update
			ApiKey apiKey = new ApiKey();
			apiKey.delete();

		} catch (IOException e) {
			LOGGER.error("Error loading or deleting API key: {}", e.getMessage());
		}
	}

	private static void replaceJar() {
		// Assuming current working directory contains the JAR files
		Path realJar = Paths.get(JAR_FILENAME);
		Path newJar = Paths.get(NEW_JAR_FILENAME);

		if (!Files.exists(newJar)) {
			LOGGER.warn(() -> String.format("Replacement JAR '%s' not found?", newJar));
			return;
		}

		int attempt;
		for (attempt = 0; attempt < MAX_ATTEMPTS; ++attempt) {
			final int attemptForLogging = attempt;
			LOGGER.info(() -> String.format("Attempt #%d out of %d to replace JAR", attemptForLogging + 1, MAX_ATTEMPTS));

			try {
				Files.copy(newJar, realJar, StandardCopyOption.REPLACE_EXISTING);
				break;
			} catch (IOException e) {
				LOGGER.info(() -> String.format("Unable to replace JAR: %s", e.getMessage()));

				// Try again
			}

			try {
				Thread.sleep(CHECK_INTERVAL);
			} catch (InterruptedException e) {
				LOGGER.warn("Ignoring interrupt...");
				// Doggedly retry
			}
		}

		if (attempt == MAX_ATTEMPTS)
			LOGGER.error("Failed to replace JAR - giving up");
	}

	private static void restartNode(String[] args) {
		String javaHome = System.getProperty("java.home");
		LOGGER.debug(() -> String.format("Java home: %s", javaHome));

		Path javaBinary = Paths.get(javaHome, "bin", "java");
		LOGGER.debug(() -> String.format("Java binary: %s", javaBinary));

		Path exeLauncher = Paths.get(WINDOWS_EXE_LAUNCHER);
		LOGGER.debug(() -> String.format("Windows EXE launcher: %s", exeLauncher));

		List<String> javaCmd;
		if (Files.exists(exeLauncher)) {
			javaCmd = Arrays.asList(exeLauncher.toString());
		} else {
			javaCmd = new ArrayList<>();
			// Java runtime binary itself
			javaCmd.add(javaBinary.toString());

			// JVM arguments
			javaCmd.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());

			// Reapply any retained, but disabled, -agentlib JVM arg
			javaCmd = javaCmd.stream()
					.map(arg -> arg.replace(AGENTLIB_JVM_HOLDER_ARG, "-agentlib"))
					.collect(Collectors.toList());

			// Call mainClass in JAR
			javaCmd.addAll(Arrays.asList("-jar", JAR_FILENAME));

			// Add saved command-line args
			javaCmd.addAll(Arrays.asList(args));
		}

		try {
			LOGGER.debug(String.format("Restarting node with: %s", String.join(" ", javaCmd)));

			ProcessBuilder processBuilder = new ProcessBuilder(javaCmd);

			if (Files.exists(exeLauncher)) {
				LOGGER.debug(() -> String.format("Setting env %s to %s", JAVA_TOOL_OPTIONS_NAME, JAVA_TOOL_OPTIONS_VALUE));
				processBuilder.environment().put(JAVA_TOOL_OPTIONS_NAME, JAVA_TOOL_OPTIONS_VALUE);
			}

			// New process will inherit our stdout and stderr
			processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
			processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

			Process process = processBuilder.start();

			// Nothing to pipe to new process, so close output stream (process's stdin)
			process.getOutputStream().close();
		} catch (Exception e) {
			LOGGER.error(String.format("Failed to restart node (BAD): %s", e.getMessage()));
		}
	}

}
