package org.qortal.data.arbitrary;

import java.util.Objects;

public class IssuerIndexKey {

    public String issuer;
    public String name;
    public int category;
    public String link;

    public IssuerIndexKey() {}

    public IssuerIndexKey(ArbitraryDataIndexDetail detail) {
        this.issuer = detail.issuer;
        this.name = detail.name;
        this.category = detail.category;
        this.link = detail.link;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IssuerIndexKey that = (IssuerIndexKey) o;
        return category == that.category && issuer.equals(that.issuer) && name.equals(that.name) && link.equals(that.link);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuer, name, category, link);
    }
}
