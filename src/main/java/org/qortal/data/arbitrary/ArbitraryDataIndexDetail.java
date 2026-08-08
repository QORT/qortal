package org.qortal.data.arbitrary;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArbitraryDataIndexDetail {

    public String issuer;
    public int rank;
    public String term;
    public String name;
    public int category;
    public String link;
    public String indexIdentifer;

    public ArbitraryDataIndexDetail() {}

    public ArbitraryDataIndexDetail(String issuer, int rank, ArbitraryDataIndex index, String indexIdentifer) {
        this.issuer = issuer;
        this.rank = rank;
        this.term = index.t;
        this.name = index.n;
        this.category = index.c;
        this.link = index.l;
        this.indexIdentifer = indexIdentifer;
    }

    @Override
    public String toString() {
        return "ArbitraryDataIndexDetail{" +
                "issuer='" + issuer + '\'' +
                ", rank=" + rank +
                ", term='" + term + '\'' +
                ", name='" + name + '\'' +
                ", category=" + category +
                ", link='" + link + '\'' +
                ", indexIdentifer='" + indexIdentifer + '\'' +
                '}';
    }
}
