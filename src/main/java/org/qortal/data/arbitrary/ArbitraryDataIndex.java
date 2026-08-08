package org.qortal.data.arbitrary;

import org.qortal.arbitrary.misc.Service;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArbitraryDataIndex {

    public String t;
    public String n;
    public int c;
    public String l;

    public ArbitraryDataIndex() {}

    public ArbitraryDataIndex(String t, String n, int c, String l) {
        this.t = t;
        this.n = n;
        this.c = c;
        this.l = l;
    }

    @Override
    public String toString() {
        return "ArbitraryDataIndex{" +
                "t='" + t + '\'' +
                ", n='" + n + '\'' +
                ", c=" + c +
                ", l='" + l + '\'' +
                '}';
    }
}
