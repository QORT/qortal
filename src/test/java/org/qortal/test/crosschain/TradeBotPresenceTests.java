package org.qortal.test.crosschain;

import org.junit.Test;
import org.qortal.utils.ByteArray;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TradeBotPresenceTests {

    public static final long ROUNDING = 15 * 60 * 1000L; // to nearest X mins
    public static final long LIFETIME = 30 * 60 * 1000L; // lifetime: X mins
    public static final long EARLY_RENEWAL_LIFETIME = 5 * 60 * 1000L; // X mins before expiry
    public static final long CHECK_INTERVAL = 5 * 60 * 1000L; // X mins
    public static final long MAX_TIMESTAMP = 100 * 60 * 1000L; // run tests for X mins

    // We want to generate timestamps that expire 30 mins into the future, but also round to nearest X min?
    // We want to regenerate timestamps early (e.g. 15 mins before expiry) to allow for network propagation

    // We want to keep the latest timestamp for any given public key
    // We want to reject out-of-bound timestamps from peers (>30 mins into future, not now/past)

    // We want to make sure that we don't incorrectly delete an entry at 15-min and 30-min boundaries

    @Test
    public void testGeneratedExpiryTimestamps() {
        for (long timestamp = 0; timestamp <= MAX_TIMESTAMP; timestamp += CHECK_INTERVAL) {
            long expiry = generateExpiry(timestamp);

            System.out.println(String.format("time: % 3dm, expiry: % 3dm",
                    timestamp / 60_000L,
                    expiry / 60_000L
            ));
        }
    }

    @Test
    public void testEarlyRenewal() {
        Long currentExpiry = null;

        for (long timestamp = 0; timestamp <= MAX_TIMESTAMP; timestamp += CHECK_INTERVAL) {
            long newExpiry = generateExpiry(timestamp);

            if (currentExpiry == null || currentExpiry - timestamp <= EARLY_RENEWAL_LIFETIME) {
                currentExpiry = newExpiry;
            }

            System.out.println(String.format("time: % 3dm, expiry: % 3dm",
                    timestamp / 60_000L,
                    currentExpiry / 60_000L
            ));
        }
    }

    @Test
    public void testEnforceLatestTimestamp() {
        ByteArray pubkeyByteArray = ByteArray.wrap("publickey".getBytes(StandardCharsets.UTF_8));

        Map<ByteArray, Long> timestampsByPublicKey = new HashMap<>();

        // Working backwards this time
        for (long timestamp = MAX_TIMESTAMP; timestamp >= 0; timestamp -= CHECK_INTERVAL){
            long newExpiry = generateExpiry(timestamp);

            timestampsByPublicKey.compute(pubkeyByteArray, (k, v) ->
               v == null || v < newExpiry ? newExpiry : v
            );

            Long currentExpiry = timestampsByPublicKey.get(pubkeyByteArray);

            System.out.println(String.format("time: % 3dm, expiry: % 3dm",
                    timestamp / 60_000L,
                    currentExpiry / 60_000L
            ));
        }
    }

    @Test
    public void testEnforcePeerExpiryBounds() {
        System.out.println(String.format("%40s", "Our time"));

        for (long ourTimestamp = 0; ourTimestamp <= MAX_TIMESTAMP; ourTimestamp += CHECK_INTERVAL) {
            System.out.print(String.format("%s% 3dm ",
                    ourTimestamp != 0 ? "| " : "        ",
                    ourTimestamp / 60_000L
            ));
        }
        System.out.println();

        for (long peerTimestamp = 0; peerTimestamp <= MAX_TIMESTAMP; peerTimestamp += CHECK_INTERVAL) {
            System.out.print(String.format("% 4dm ", peerTimestamp / 60_000L));

            for (long ourTimestamp = 0; ourTimestamp <= MAX_TIMESTAMP; ourTimestamp += CHECK_INTERVAL) {
                System.out.print(String.format("|    %s ",
                        isPeerExpiryValid(ourTimestamp, peerTimestamp) ? "✔" : "✘"
                ));
            }
            System.out.println();
        }

        System.out.println("Peer's expiry time");
    }

    private long generateExpiry(long timestamp) {
        return ((timestamp - 1) / ROUNDING) * ROUNDING + LIFETIME;
    }

    private boolean isPeerExpiryValid(long nowTimestamp, long peerExpiry) {
        return peerExpiry > nowTimestamp && peerExpiry <= LIFETIME + nowTimestamp;
    }
}
