package org.qortal.data.block;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.Objects;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class DecodedOnlineAccountData {

    private long onlineTimestamp;
    private String minter;
    private String recipient;
    private int sharePercent;
    private boolean minterGroupMember;
    private String name;
    private int level;

    public DecodedOnlineAccountData() {
    }

    public DecodedOnlineAccountData(long onlineTimestamp, String minter, String recipient, int sharePercent, boolean minterGroupMember, String name, int level) {
        this.onlineTimestamp = onlineTimestamp;
        this.minter = minter;
        this.recipient = recipient;
        this.sharePercent = sharePercent;
        this.minterGroupMember = minterGroupMember;
        this.name = name;
        this.level = level;
    }

    public long getOnlineTimestamp() {
        return onlineTimestamp;
    }

    public String getMinter() {
        return minter;
    }

    public String getRecipient() {
        return recipient;
    }

    public int getSharePercent() {
        return sharePercent;
    }

    public boolean isMinterGroupMember() {
        return minterGroupMember;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DecodedOnlineAccountData that = (DecodedOnlineAccountData) o;
        return onlineTimestamp == that.onlineTimestamp && sharePercent == that.sharePercent && minterGroupMember == that.minterGroupMember && level == that.level && Objects.equals(minter, that.minter) && Objects.equals(recipient, that.recipient) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(onlineTimestamp, minter, recipient, sharePercent, minterGroupMember, name, level);
    }

    @Override
    public String toString() {
        return "DecodedOnlineAccountData{" +
                "onlineTimestamp=" + onlineTimestamp +
                ", minter='" + minter + '\'' +
                ", recipient='" + recipient + '\'' +
                ", sharePercent=" + sharePercent +
                ", minterGroupMember=" + minterGroupMember +
                ", name='" + name + '\'' +
                ", level=" + level +
                '}';
    }
}
