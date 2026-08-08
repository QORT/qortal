package org.qortal.data.arbitrary;

import org.qortal.arbitrary.misc.Service;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArbitraryDataIndexScoreKey {

    public String name;
    public int category;
    public String link;

    public ArbitraryDataIndexScoreKey() {}

    public ArbitraryDataIndexScoreKey(String name, int category, String link) {
        this.name = name;
        this.category = category;
        this.link = link;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArbitraryDataIndexScoreKey that = (ArbitraryDataIndexScoreKey) o;
        return category == that.category && Objects.equals(name, that.name) && Objects.equals(link, that.link);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, category, link);
    }


}
