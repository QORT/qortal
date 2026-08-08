package org.qortal.utils;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.data.crosschain.ForeignFeeDecodedData;
import org.qortal.transform.Transformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.qortal.transform.Transformer.ADDRESS_LENGTH;

/**
 * Class ForeignFeesMessageUtils
 */
public class ForeignFeesMessageUtils {

    private static final Logger LOGGER = LogManager.getLogger(ForeignFeesMessageUtils.class);

    /**
     * From Data To Send Bytes
     *
     * Convert foreign fee data into bytes for send messages.
     *
     * @param foreignFees the data
     *
     * @return the bytes
     */
    public static byte[] fromDataToSendBytes(List<ForeignFeeDecodedData> foreignFees) {

        return fromDataToBytes(foreignFees, true);
    }

    /**
     * From Data To Bytes
     *
     * @param foreignFees
     * @param includeSignature
     * @return
     */
    private static byte[] fromDataToBytes(List<ForeignFeeDecodedData> foreignFees, boolean includeSignature) {
        try {
            if (foreignFees.isEmpty()) {
                return new byte[0];
            }
            else {
                // allocate size for each data item for timestamp, AT address, fee and signature
                int byteSize
                    = foreignFees.size()
                        *
                    (Transformer.TIMESTAMP_LENGTH + Transformer.ADDRESS_LENGTH + Transformer.INT_LENGTH + Transformer.SIGNATURE_LENGTH);

                if( includeSignature ) byteSize += foreignFees.size() * Transformer.SIGNATURE_LENGTH;

                ByteArrayOutputStream bytes = new ByteArrayOutputStream(byteSize);

                // for each foreign fee data item, convert to bytes and fill the array
                for( ForeignFeeDecodedData feeData : foreignFees) {
                    bytes.write(Longs.toByteArray(feeData.getTimestamp()));
                    bytes.write(Base58.decode(feeData.getAtAddress()));
                    bytes.write(Ints.toByteArray(feeData.getFee()));
                    if( includeSignature ) bytes.write(feeData.getData());
                }
                return bytes.toByteArray();
            }
        } catch (Exception e) {
            LOGGER.warn(e.getMessage());

            return new byte[0];
        }
    }

    /**
     * From Send Bytes to Data
     *
     * @param bytes the bytes to convert to data
     *
     * @return the data
     */
    public static List<ForeignFeeDecodedData> fromSendBytesToData(ByteBuffer bytes) {

        return fromBytesToData(bytes, true);
    }

    /**
     * From Bytes To Data
     *
     * @param bytes the bytes
     * @param includeSignature true if the bytes include signatures
     *
     * @return the foreign fee data with signatures (data member)
     */
    private static List<ForeignFeeDecodedData> fromBytesToData(ByteBuffer bytes, boolean includeSignature) {
        if( !bytes.hasRemaining() ) return new ArrayList<>(0);

        List<ForeignFeeDecodedData> foreignFees = new ArrayList<>();

        try {
            while (bytes.hasRemaining()) {
                // read in the timestamp as a long
                long timestamp = bytes.getLong();

                // read in the address as a byte array with a predetermined length
                byte[] atAddressBytes = new byte[ADDRESS_LENGTH];
                bytes.get(atAddressBytes);
                String atAddress = Base58.encode(atAddressBytes);

                // rwad in the fee as an integer
                int fee = bytes.getInt();

                byte[] signature;

                if( includeSignature ) {
                    signature = new byte[Transformer.SIGNATURE_LENGTH];
                    bytes.get(signature);
                }
                else {
                    signature = null;
                }

                foreignFees.add(new ForeignFeeDecodedData(timestamp, signature, atAddress, fee));

            }
        }
        // if there are any exception, log the error as a warning and clear the list before returning it
        catch (Exception e) {
            LOGGER.warn(e.getMessage());
            foreignFees.clear();
        }

        return foreignFees;
    }

    /**
     * From Data To Get Bytes
     *
     * Convert foreign fees data objects into get foreign fees messages. Get messages do not include signatures.
     *
     * @param foreignFees the foreign fees objects
     *
     * @return the messages
     */
    public static byte[] fromDataToGetBytes(List<ForeignFeeDecodedData> foreignFees) {
        return fromDataToBytes(foreignFees, false);
    }

    /**
     * From Get Bytes to Data
     *
     * Convert bytes from get foreign fees messages to foreign fees objects. Get messages do not include signatures.
     *
     * @param bytes the bytes to convert
     *
     * @return the foreign fees data objects
     */
    public static List<ForeignFeeDecodedData> fromGetBytesToData(ByteBuffer bytes) {
        return fromBytesToData(bytes, false);
    }

    /**
     * Build Foreign Fees Data Message
     *
     * Build the unsigned message for the foreign fees data objects.
     *
     * @param timestamp the timestamp in milliseconds
     * @param atAddress the AT address
     * @param fee the fee
     * @return
     * @throws IOException
     */
    public static byte[] buildForeignFeesDataMessage(Long timestamp, String atAddress, int fee) throws IOException {
        int byteSize = Transformer.TIMESTAMP_LENGTH + Transformer.ADDRESS_LENGTH + Transformer.INT_LENGTH;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(byteSize);

        bytes.write(Longs.toByteArray(timestamp));
        bytes.write(Base58.decode(atAddress));
        bytes.write(Ints.toByteArray(fee));

        return bytes.toByteArray();
    }
}
