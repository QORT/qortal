package org.qortal.data.crosschain;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class UnsignedFeeEvent {

    private boolean positive;

    private String address;

    public UnsignedFeeEvent() {
    }

    public UnsignedFeeEvent(boolean positive, String address) {

        this.positive = positive;
        this.address = address;
    }

    public boolean isPositive() {
        return positive;
    }

    public String getAddress() {
        return address;
    }
}
