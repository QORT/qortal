package org.qortal.test.apps;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.qortal.utils.NTP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NTPTests {

	private static final List<String> CC_TLDS = Arrays.asList("oceania", "europe", "cn", "asia", "africa");

	public static void main(String[] args) throws InterruptedException {
		List<String> ntpServers = new ArrayList<>();

		for (String ccTld : CC_TLDS)
			for (int subpool = 0; subpool <= 3; ++subpool)
				ntpServers.add(new String(subpool + "." + ccTld + ".pool.ntp.org"));

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			NTP.shutdownNow();
		}));

		Logger ntpLogger = LogManager.getLogger(NTP.class);
		LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
		Configuration config = loggerContext.getConfiguration();
		LoggerConfig loggerConfig = config.getLoggerConfig(ntpLogger.getName());

		loggerConfig.setLevel(Level.TRACE);
		loggerContext.updateLoggers(config);

		NTP.start(ntpServers.toArray(new String[0]));

		// Endless sleep
		Thread.sleep(1000000000L);
	}

}
