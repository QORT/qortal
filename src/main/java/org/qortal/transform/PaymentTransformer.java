package org.qortal.transform;

import com.google.common.primitives.Longs;
import org.qortal.data.PaymentData;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class PaymentTransformer extends Transformer {

	// Property lengths
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;

	private static final int TOTAL_LENGTH = RECIPIENT_LENGTH + ASSET_ID_LENGTH + AMOUNT_LENGTH;

	public static PaymentData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		String recipient = Serialization.deserializeAddress(byteBuffer);

		long assetId = byteBuffer.getLong();

		long amount = byteBuffer.getLong();

		return new PaymentData(recipient, assetId, amount);
	}

	public static int getDataLength() throws TransformationException {
		return TOTAL_LENGTH;
	}

	public static byte[] toBytes(PaymentData paymentData) throws TransformationException {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			Serialization.serializeAddress(bytes, paymentData.getRecipient());

			bytes.write(Longs.toByteArray(paymentData.getAssetId()));

			bytes.write(Longs.toByteArray(paymentData.getAmount()));

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
