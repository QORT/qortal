package org.qortal.data.arbitrary;

import org.qortal.arbitrary.misc.Service;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArbitraryDataIndexScorecard {

    public double score;
    public String name;
    public int category;
    public String link;

    public ArbitraryDataIndexScorecard() {}

    public ArbitraryDataIndexScorecard(double score, String name, int category, String link) {
        this.score = score;
        this.name = name;
        this.category = category;
        this.link = link;
    }

    public double getScore() {
        return score;
    }

    @Override
    public String toString() {
        return "ArbitraryDataIndexScorecard{" +
                "score=" + score +
                ", name='" + name + '\'' +
                ", category=" + category +
                ", link='" + link + '\'' +
                '}';
    }
}
