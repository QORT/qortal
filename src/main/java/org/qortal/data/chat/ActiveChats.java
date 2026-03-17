package org.qortal.data.chat;

import org.bouncycastle.util.encoders.Base64;
import org.qortal.utils.Base58;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.List;

import static org.qortal.data.chat.ChatMessage.Encoding;

@XmlAccessorType(XmlAccessType.FIELD)
public class ActiveChats {

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class GroupChat {
		private int groupId;
		private String groupName;

		// The following fields might not be present for groupId 0
		private Long timestamp;
		private String sender;
		private String senderName;
		private byte[] signature;
		private Encoding encoding;
		private String data;

		protected GroupChat() {
			/* JAXB */
		}

		public GroupChat(int groupId, String groupName, Long timestamp, String sender, String senderName,
						 byte[] signature, Encoding encoding, byte[] data) {
			this.groupId = groupId;
			this.groupName = groupName;
			this.timestamp = timestamp;
			this.sender = sender;
			this.senderName = senderName;
			this.signature = signature;
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
		}

		public int getGroupId() {
			return this.groupId;
		}

		public String getGroupName() {
			return this.groupName;
		}

		public Long getTimestamp() {
			return this.timestamp;
		}

		public String getSender() {
			return this.sender;
		}

		public String getSenderName() {
			return this.senderName;
		}

		public byte[] getSignature() {
			return this.signature;
		}

		public String getData() {
			return this.data;
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class DirectChat {
		private String address;
		private String name;
		private long timestamp;
		private String sender;
		private String senderName;

		protected DirectChat() {
			/* JAXB */
		}

		public DirectChat(String address, String name, long timestamp, String sender, String senderName) {
			this.address = address;
			this.name = name;
			this.timestamp = timestamp;
			this.sender = sender;
			this.senderName = senderName;
		}

		public String getAddress() {
			return this.address;
		}

		public String getName() {
			return this.name;
		}

		public long getTimestamp() {
			return this.timestamp;
		}

		public String getSender() {
			return this.sender;
		}

		public String getSenderName() {
			return this.senderName;
		}
	}

	// Properties

	private List<GroupChat> groups;

	private List<DirectChat> direct;

	// Constructors

	protected ActiveChats() {
		/* For JAXB */
	}

	// For repository use
	public ActiveChats(List<GroupChat> groups, List<DirectChat> direct) {
		this.groups = groups;
		this.direct = direct;
	}

	public List<GroupChat> getGroups() {
		return this.groups;
	}

	public List<DirectChat> getDirect() {
		return this.direct;
	}

}