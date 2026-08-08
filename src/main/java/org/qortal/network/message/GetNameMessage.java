package org.qortal.network.message;

import org.qortal.naming.Name;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class GetNameMessage extends Message {

	private String name;

	public GetNameMessage(String address) {
		super(MessageType.GET_NAME);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			Serialization.serializeSizedStringV2(bytes, name);

		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetNameMessage(int id, String name) {
		super(id, MessageType.GET_NAME);

		this.name = name;
	}

	public String getName() {
		return this.name;
	}


	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws MessageException {
		try {
			String name = Serialization.deserializeSizedStringV2(bytes, Name.MAX_NAME_SIZE);

			return new GetNameMessage(id, name);

		} catch (TransformationException e) {
			throw new MessageException(e.getMessage(), e);
		}
	}

}
