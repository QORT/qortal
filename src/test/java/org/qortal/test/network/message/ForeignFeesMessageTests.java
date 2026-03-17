package org.qortal.test.network.message;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Test;
import org.qortal.crypto.Crypto;
import org.qortal.data.crosschain.ForeignFeeDecodedData;
import org.qortal.test.utils.TestUtils;
import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;
import org.qortal.utils.ForeignFeesMessageUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Class ForeignFeesMessageTests
 */
public class ForeignFeesMessageTests {

    /**
     * Random
     *
     * Random input generator for seeding keys/addresses.
     */
    private static final Random RANDOM = new Random();

    static {
        // add the Bouncy Castle provider for keys/addresses
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testDataToSendBytesToDataEmpty() {

        byte[] bytes = ForeignFeesMessageUtils.fromDataToSendBytes(new ArrayList<>(0));

        List<ForeignFeeDecodedData> list = ForeignFeesMessageUtils.fromSendBytesToData(ByteBuffer.wrap(bytes));

        Assert.assertNotNull(list);

        Assert.assertEquals(0, list.size());
    }

    @Test
    public void testDataToGetBytesToDataEmpty() {

        byte[] bytes = ForeignFeesMessageUtils.fromDataToGetBytes(new ArrayList<>(0));

        List<ForeignFeeDecodedData> list = ForeignFeesMessageUtils.fromGetBytesToData(ByteBuffer.wrap(bytes));

        Assert.assertNotNull(list);

        Assert.assertEquals(0, list.size());
    }

    @Test
    public void testSignature() {

        boolean exceptionThrown = false;

        try {
            KeyPair keyPair = TestUtils.generateKeyPair();

            long timestamp = 1_000_000L;
            String atAddress = generateAtAddress();
            int fee = 1;

            assertSignature(keyPair, timestamp, atAddress, fee);

        } catch (Exception e) {
             exceptionThrown = true;
        }

        Assert.assertFalse(exceptionThrown);
    }

    /**
     * Assert Signature
     *
     * @param keyPair the key pair that is signing
     * @param timestamp the timestamp for the data
     * @param atAddress the AT address
     * @param fee the fee
     *
     * @return the signature bytes
     *
     * @throws IOException
     */
    private static byte[] assertSignature(KeyPair keyPair, long timestamp, String atAddress, int fee) throws IOException {

        // build the message and sign it
        byte[] message = ForeignFeesMessageUtils.buildForeignFeesDataMessage(timestamp, atAddress, fee);
        byte[] signature = Crypto.sign( keyPair.getPrivate().getEncoded(), message );

        // assert signaute length
        Assert.assertEquals(Transformer.SIGNATURE_LENGTH, signature.length);

        // assert verification
        boolean verified = Crypto.verify(Crypto.toPublicKey(keyPair.getPrivate().getEncoded()), signature, message);
        Assert.assertTrue(verified);

        return signature;
    }

    @Test
    public void testDataToSendBytesToDataSingle() {

        Long timestamp = 1_000_000L;
        String atAddress = generateAtAddress();
        int fee = 1;

        boolean exceptionThrown = false;

        try {
            // random key generation for signing data
            KeyPair keyPair = TestUtils.generateKeyPair();

            // data to send, a list of 1 foreign fee data
            List<ForeignFeeDecodedData> sendData
                = List.of(
                    new ForeignFeeDecodedData(timestamp, assertSignature(keyPair,timestamp,atAddress, fee), atAddress, fee)
                );

            // from data to bytes
            byte[] sendBytes = ForeignFeesMessageUtils.fromDataToSendBytes(sendData);

            // from bytes to data
            List<ForeignFeeDecodedData> returnData = ForeignFeesMessageUtils.fromSendBytesToData(ByteBuffer.wrap(sendBytes));


            assertListedForeignFees(sendData, returnData, true);

        } catch (Exception e) {
            exceptionThrown = true;
        }

        Assert.assertFalse(exceptionThrown);
    }

    @Test
    public void testDataToGetBytesToDataSingle() {

        Long timestamp = 1_000_000L;
        String atAddress = generateAtAddress();
        int fee = 1;

        boolean exceptionThrown = false;

        try {
            // random key generation for signing data
            KeyPair keyPair = TestUtils.generateKeyPair();

            // data to send, a list of 1 foreign fee data
            List<ForeignFeeDecodedData> sendData
                    = List.of(
                    new ForeignFeeDecodedData(timestamp, assertSignature(keyPair,timestamp,atAddress, fee), atAddress, fee)
            );

            // from data to bytes
            byte[] sendBytes = ForeignFeesMessageUtils.fromDataToGetBytes(sendData);

            // from bytes to data
            List<ForeignFeeDecodedData> returnData = ForeignFeesMessageUtils.fromGetBytesToData(ByteBuffer.wrap(sendBytes));


            assertListedForeignFees(sendData, returnData, false);

        } catch (Exception e) {
            exceptionThrown = true;
        }

        Assert.assertFalse(exceptionThrown);
    }

    @Test
    public void testDataToSendBytesToDataTriple() {

        Long timestamp1 = 1_000_000L;
        String atAddress1 = generateAtAddress();
        int fee1 = 1;

        Long timestamp2 = 2_000_000L;
        String atAddress2 = generateAtAddress();
        int fee2 = 2;

        Long timestamp3 = 5_000_000L;
        String atAddress3 = generateAtAddress();
        int fee3 = 3;

        boolean exceptionThrown = false;

        try {
            // random key generation for signing data
            KeyPair keyPair1 = TestUtils.generateKeyPair();
            KeyPair keyPair2 = TestUtils.generateKeyPair();

            // data to send, a list of 3 foreign fee data
            List<ForeignFeeDecodedData> sendData
                    = List.of(
                    new ForeignFeeDecodedData(timestamp1, assertSignature(keyPair1,timestamp1,atAddress1, fee1), atAddress1, fee1),
                    new ForeignFeeDecodedData(timestamp2, assertSignature(keyPair1,timestamp2,atAddress2, fee2), atAddress2, fee2),
                    new ForeignFeeDecodedData(timestamp3, assertSignature(keyPair2,timestamp3,atAddress3, fee3), atAddress3, fee3)
            );

            // from data to bytes
            byte[] sendBytes = ForeignFeesMessageUtils.fromDataToSendBytes(sendData);

            // from bytes to data
            List<ForeignFeeDecodedData> returnData = ForeignFeesMessageUtils.fromSendBytesToData(ByteBuffer.wrap(sendBytes));


            assertListedForeignFees(sendData, returnData, true);

        } catch (Exception e) {
            exceptionThrown = true;
        }

        Assert.assertFalse(exceptionThrown);
    }

    @Test
    public void testDataToGetBytesToDataTriple() {

        Long timestamp1 = 1_000_000L;
        String atAddress1 = generateAtAddress();
        int fee1 = 1;

        Long timestamp2 = 2_000_000L;
        String atAddress2 = generateAtAddress();
        int fee2 = 2;

        Long timestamp3 = 5_000_000L;
        String atAddress3 = generateAtAddress();
        int fee3 = 3;

        boolean exceptionThrown = false;

        try {
            // random key generation for signing data
            KeyPair keyPair1 = TestUtils.generateKeyPair();
            KeyPair keyPair2 = TestUtils.generateKeyPair();

            // data to send, a list of 3 foreign fee data
            List<ForeignFeeDecodedData> sendData
                    = List.of(
                    new ForeignFeeDecodedData(timestamp1, assertSignature(keyPair1,timestamp1,atAddress1, fee1), atAddress1, fee1),
                    new ForeignFeeDecodedData(timestamp2, assertSignature(keyPair1,timestamp2,atAddress2, fee2), atAddress2, fee2),
                    new ForeignFeeDecodedData(timestamp3, assertSignature(keyPair2,timestamp3,atAddress3, fee3), atAddress3, fee3)
            );

            // from data to bytes
            byte[] sendBytes = ForeignFeesMessageUtils.fromDataToGetBytes(sendData);

            // from bytes to data
            List<ForeignFeeDecodedData> returnData = ForeignFeesMessageUtils.fromGetBytesToData(ByteBuffer.wrap(sendBytes));


            assertListedForeignFees(sendData, returnData, false);

        } catch (Exception e) {
            exceptionThrown = true;
        }

        Assert.assertFalse(exceptionThrown);
    }

    /**
     * Assert Listed Foreign Fees
     *
     * @param expectedList
     * @param actualList
     * @param includeSignature
     */
    private static void assertListedForeignFees(List<ForeignFeeDecodedData> expectedList, List<ForeignFeeDecodedData> actualList, boolean includeSignature) {

        int expectedSize = expectedList.size();

        // basic assertions on return data
        Assert.assertNotNull(actualList);
        Assert.assertEquals(expectedSize, actualList.size());

        for( int index = 0; index < expectedSize; index++ ) {
            // expected and actual fee data
            ForeignFeeDecodedData expected = expectedList.get(index);
            ForeignFeeDecodedData actual = actualList.get(index);

            assertForeignFeeEquality(expected, actual, includeSignature);
        }
    }

    /**
     * Assert Foreign Fee Equality
     *
     * @param expected         the expected data, for comparison
     * @param actual           the actual data, the response to evaluate
     * @param includeSignature
     */
    private static void assertForeignFeeEquality(ForeignFeeDecodedData expected, ForeignFeeDecodedData actual, boolean includeSignature) {
        // assert
        Assert.assertEquals(expected, actual);

        if( includeSignature ) {
            // get the data members of each, since the data members are not part of the object comparison above
            byte[] expectedData = expected.getData();
            byte[] actualData = actual.getData();

            // assert data members, must encode them to strings for comparisons
            Assert.assertNotNull(actualData);
            Assert.assertEquals(Base58.encode(expectedData), Base58.encode(actualData));
        }
    }

    /**
     * Generate AT Address
     *
     * Generate AT address using a random inpute seed.
     *
     * @return the AT address
     */
    private static String generateAtAddress() {

        byte[] signature = new byte[64];
        RANDOM.nextBytes(signature);
        String atAddress = Crypto.toATAddress(signature);

        return atAddress;
    }
}