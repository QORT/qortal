package org.qortal.data.chat;

import org.bouncycastle.util.encoders.Base64;
import org.qortal.utils.Base58;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class ChatMessage {

	public enum Encoding {
		BASE58,
		BASE64
	}

	// Properties

	private long timestamp;

	private int txGroupId;

	private byte[] reference;

	private byte[] senderPublicKey;

	/* Address of sender */
	private String sender;

	// Not always present
	private String senderName;

	/* Address of recipient (if any) */
	private String recipient; // can be null

	private String recipientName;

	private byte[] chatReference;

	private Encoding encoding;

	private String data;

	private boolean isText;
	private boolean isEncrypted;

	private byte[] signature;

	// Constructors

	protected ChatMessage() {
		/* For JAXB */
	}

	// For repository use
	public ChatMessage(long timestamp, int txGroupId, byte[] reference, byte[] senderPublicKey, String sender,
					   String senderName, String recipient, String recipientName, byte[] chatReference,
					   Encoding encoding, byte[] data, boolean isText, boolean isEncrypted, byte[] signature) {
		this.timestamp = timestamp;
		this.txGroupId = txGroupId;
		this.reference = reference;
		this.senderPublicKey = senderPublicKey;
		this.sender = sender;
		this.senderName = senderName;
		this.recipient = recipient;
		this.recipientName = recipientName;
		this.chatReference = chatReference;
		this.encoding = encoding != null ? encoding : Encoding.BASE58;

		if (data != null) {
			switch (this.encoding) {
				case BASE64:
					this.data = Base64.toBase64String(data);
					break;

				case BASE58:
				default:
					this.data = Base58.encode(data);
					break;
			}
		}
		else {
			this.data = null;
		}

		this.isText = isText;
		this.isEncrypted = isEncrypted;
		this.signature = signature;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public int getTxGroupId() {
		return this.txGroupId;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public String getSender() {
		return this.sender;
	}

	public String getSenderName() {
		return this.senderName;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public String getRecipientName() {
		return this.recipientName;
	}

	public byte[] getChatReference() {
		return this.chatReference;
	}

	public String getData() {
		return this.data;
	}

	public boolean isText() {
		return this.isText;
	}

	public boolean isEncrypted() {
		return this.isEncrypted;
	}

	public byte[] getSignature() {
		return this.signature;
	}

}
