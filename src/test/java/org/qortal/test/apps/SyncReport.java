package org.qortal.test.apps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/*
 * We are expecting log entries like:
 * 
 * 2019-07-24 11:31:25 DEBUG Synchronizer:121 - Synchronizing with peer 52.33.79.54:9889 at height 73139, sig 2cfqHyJ4, ts 1563964269587; our height 73140, sig 8hae8BLg, ts 1563964271060
 * 
 * 2019-07-24 11:31:26 DEBUG Synchronizer:138 - Common block with peer 52.33.79.54:9889 is at height 73138, sig 7LKMHZxU, ts 1563964148818
 * 
 * 2019-07-24 11:31:26 INFO  Synchronizer:326 - Synchronized with peer 52.33.79.54:9889 to height 73139, sig 2cfqHyJ4, ts: 1563964269587
 * 
 */
public class SyncReport {

	private static final Pattern START_PATTERN;
	private static final Pattern COMMON_PATTERN;
	private static final Pattern DONE_PATTERN;
	private static final Pattern FAIL_PATTERN;
	static {
		try {
			START_PATTERN = Pattern.compile("(\\d+-\\d+-\\d+ \\d+:\\d+:\\d+) .*? Synchronizing with peer (\\S+) at height (\\d+), sig (\\S+), ts (\\d+); our height (\\d+), sig (\\S+), ts (\\d+)");
			COMMON_PATTERN = Pattern.compile(".*? Common block with peer (\\S+) is at height (\\d+), sig (\\S+), ts (\\d+)");
			DONE_PATTERN = Pattern.compile(".*? Synchronized with peer (\\S+) to height (\\d+), sig (\\S+), ts:? (\\d+)");
			FAIL_PATTERN = Pattern.compile(".*? .ynchronized? with peer (\\S+) \\((\\S+)\\)");
		} catch (PatternSyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	static class SyncEvent {
		long timestamp;
		String peer;
		String fromBlockSig;
		String commonBlockSig;
		String tipBlockSig;
	}

	public static void main(String args[]) {
		if (args.length == 0) {
			System.err.println("usage: SyncReport <logfile> [<logfile> ...]");
			System.exit(1);
		}

		List<SyncEvent> syncEvents = new ArrayList<>();
		Map<String, Integer> blockHeights = new HashMap<>();

		InputStream in = null;
		Scanner scanner = null;
		int argIndex = 0;

		Matcher matcher;
		SyncEvent syncEvent = null;

		while (argIndex < args.length) {
			if (in == null) {
				// Open a log file
				Path path = Paths.get(args[argIndex]);

				try {
					in = Files.newInputStream(path);
				} catch (IOException e) {
					System.err.println(String.format("Unable to open '%s': %s", path.toString(), e.getMessage()));
					System.exit(2);
				}

				scanner = new Scanner(in, "UTF8");
				scanner.useDelimiter("\n");
			}

			if (!scanner.hasNext()) {
				scanner.close();
				scanner = null;

				try {
					in.close();
				} catch (IOException e) {
					System.err.println(String.format("Unable to close InputStream: %s", e.getMessage()));
					System.exit(2);
				}
				in = null;

				++argIndex;

				continue;
			}

			String line = scanner.next();

			matcher = START_PATTERN.matcher(line);
			if (matcher.matches()) {
				long logTimestamp = 0;
				try {
					Date logDate = LOG_DATE_FORMAT.parse(matcher.group(1));
					logTimestamp = logDate.toInstant().toEpochMilli();
				} catch (ParseException e) {
					System.err.println(String.format("Couldn't determine log entry timestamp from '%s'", matcher.group(1)));
					System.exit(3);
				}

				Integer tipHeight = Integer.valueOf(matcher.group(3));
				Integer ourHeight = Integer.valueOf(matcher.group(6));

				if (syncEvent != null) {
					// Salvageable?

					if (syncEvent.commonBlockSig != null && ourHeight > blockHeights.get(syncEvent.fromBlockSig)) {
						syncEvent.tipBlockSig = matcher.group(7);
						syncEvents.add(syncEvent);
					}
				}

				syncEvent = new SyncEvent();
				syncEvent.timestamp = logTimestamp;
				syncEvent.peer = matcher.group(2);
				syncEvent.fromBlockSig = matcher.group(7);

				blockHeights.put(matcher.group(4), tipHeight);
				blockHeights.put(matcher.group(7), ourHeight);
				continue;
			}

			matcher = COMMON_PATTERN.matcher(line);
			if (matcher.matches()) {
				String peer = matcher.group(1);

				if (syncEvent == null || !peer.equals(syncEvent.peer)) {
					System.err.println(String.format("No current synchronization with %s", peer));
					continue;
				}

				if (syncEvent.commonBlockSig != null) {
					System.err.println(String.format("We already have a common block: %s", syncEvent.commonBlockSig));
					continue;
				}

				syncEvent.commonBlockSig = matcher.group(3);
				blockHeights.put(matcher.group(3), Integer.valueOf(matcher.group(2)));
				continue;
			}

			matcher = DONE_PATTERN.matcher(line);
			if (matcher.matches()) {
				String peer = matcher.group(1);

				if (syncEvent == null || !peer.equals(syncEvent.peer)) {
					System.err.println(String.format("No current synchronization with %s", peer));
					continue;
				}

				syncEvent.tipBlockSig = matcher.group(3);

				// Add successful sync to list
				syncEvents.add(syncEvent);
				syncEvent = null;
				continue;
			}

			matcher = FAIL_PATTERN.matcher(line);
			if (matcher.matches()) {
				String result = matcher.group(2);

				if (result.equals("NO_REPLY"))
					// Possibly salvageable
					continue;

				// Sync went bad
				syncEvent = null;
            }
		}

		for (SyncEvent se : syncEvents) {
			if (se.commonBlockSig.equals(se.fromBlockSig))
				System.out.println(String.format("%s: %s (%d) ===> %s (%d) (peer %s)",
						LOG_DATE_FORMAT.format(Date.from(Instant.ofEpochMilli(se.timestamp))),
						se.fromBlockSig, blockHeights.get(se.fromBlockSig),
						se.tipBlockSig, blockHeights.get(se.tipBlockSig),
						se.peer));
			else
				System.out.println(String.format("%s: %s (%d) ---> %s (%d) ===> %s (%d) (peer %s)",
						LOG_DATE_FORMAT.format(Date.from(Instant.ofEpochMilli(se.timestamp))),
						se.fromBlockSig, blockHeights.get(se.fromBlockSig),
						se.commonBlockSig, blockHeights.get(se.commonBlockSig),
						se.tipBlockSig, blockHeights.get(se.tipBlockSig),
						se.peer));
		}
	}

}
