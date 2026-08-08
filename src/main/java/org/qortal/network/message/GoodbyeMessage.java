package org.qortal.network.message;

import com.google.common.primitives.Ints;

import java.nio.ByteBuffer;
import java.util.Map;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

public class GoodbyeMessage extends Message {

	public enum Reason {
		NO_HELLO(1),
		BAD_HELLO(2),
		BAD_HELLO_VERSION(3),
		BAD_HELLO_TIMESTAMP(4);

		public final int value;

		private static final Map<Integer, Reason> map = stream(Reason.values())
				.collect(toMap(reason -> reason.value, reason -> reason));

		Reason(int value) {
			this.value = value;
		}

		public static Reason valueOf(int value) {
			return map.get(value);
		}
	}

	private Reason reason;

	public GoodbyeMessage(Reason reason) {
		super(MessageType.GOODBYE);

		this.dataBytes = Ints.toByteArray(reason.value);
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GoodbyeMessage(int id, Reason reason) {
		super(id, MessageType.GOODBYE);

		this.reason = reason;
	}

	public Reason getReason() {
		return this.reason;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws MessageException {
		int reasonValue = byteBuffer.getInt();

		Reason reason = Reason.valueOf(reasonValue);
		if (reason == null)
			throw new MessageException("Invalid reason " + reasonValue + " in GOODBYE message");

		return new GoodbyeMessage(id, reason);
	}

}
