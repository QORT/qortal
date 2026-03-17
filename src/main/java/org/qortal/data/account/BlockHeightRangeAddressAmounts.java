package org.qortal.data.account;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.List;
import java.util.Objects;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class BlockHeightRangeAddressAmounts {

    private BlockHeightRange range;

    private List<AddressAmountData> amounts;

    public BlockHeightRangeAddressAmounts() {
    }

    public BlockHeightRangeAddressAmounts(BlockHeightRange range, List<AddressAmountData> amounts) {
        this.range = range;
        this.amounts = amounts;
    }

    public BlockHeightRange getRange() {
        return range;
    }

    public List<AddressAmountData> getAmounts() {
        return amounts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockHeightRangeAddressAmounts that = (BlockHeightRangeAddressAmounts) o;
        return Objects.equals(range, that.range) && Objects.equals(amounts, that.amounts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, amounts);
    }

    @Override
    public String toString() {
        return "BlockHeightRangeAddressAmounts{" +
                "range=" + range +
                ", amounts=" + amounts +
                '}';
    }
}
