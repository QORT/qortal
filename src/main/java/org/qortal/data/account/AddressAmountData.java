package org.qortal.data.account;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Objects;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class AddressAmountData {

    private String address;

    @XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
    private long amount;

    public AddressAmountData() {
    }

    public AddressAmountData(String address, long amount) {

        this.address = address;
        this.amount = amount;
    }

    public String getAddress() {
        return address;
    }

    public long getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddressAmountData that = (AddressAmountData) o;
        return amount == that.amount && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, amount);
    }

    @Override
    public String toString() {
        return "AddressAmountData{" +
                "address='" + address + '\'' +
                ", amount=" + amount +
                '}';
    }
}
