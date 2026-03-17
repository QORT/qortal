package org.qortal.test.utils;

import org.junit.Assert;
import org.junit.Test;
import org.qortal.data.arbitrary.AdvancedStringMatcher;
import org.qortal.data.arbitrary.ArbitraryDataIndex;
import org.qortal.data.arbitrary.ArbitraryDataIndexDetail;
import org.qortal.data.arbitrary.ArbitraryDataIndexScorecard;
import org.qortal.utils.ArbitraryIndexUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArbitraryIndexUtilsTests {

    @Test
    public void testMatch1() {
        List<AdvancedStringMatcher.MatchResult> matchedTerms
            = ArbitraryIndexUtils.getMatchedTerms(
                new String[]{"two"},
                Arrays.asList("one two", "two")
        );

        Assert.assertNotNull(matchedTerms);
        Assert.assertEquals(2, matchedTerms.size());
    }

    @Test
    public void testMatch2() {
        List<AdvancedStringMatcher.MatchResult> matchedTerms
            = ArbitraryIndexUtils.getMatchedTerms(
                new String[]{"gun"},
                Arrays.asList("fire", "guns", "water")
        );

        Assert.assertNotNull(matchedTerms);
        Assert.assertEquals(1, matchedTerms.size());
    }

    @Test
    public void testReduceRanks() {
        List<ArbitraryDataIndexDetail> indices = new ArrayList<>();
        String matchedString = "aaaa";
        String originalString = "aaab";
        List<ArbitraryDataIndexDetail> details = new ArrayList<>();

        String issuer = "me";
        int rank = 1;
        ArbitraryDataIndex index = new ArbitraryDataIndex();
        String indexIdentifer = "idx-1";

        details.add(new ArbitraryDataIndexDetail(issuer, rank, index, indexIdentifer));

        Assert.assertEquals(0, indices.size());

        ArbitraryIndexUtils.reduceRanks(indices, 0.1, details);

        Assert.assertEquals(1, indices.size());
        Assert.assertEquals(10, indices.get(0).rank);
    }

    @Test
    public void testGetArbitraryDataIndexScorecards1() {

        List<ArbitraryDataIndexDetail> indices = new ArrayList<>();
        List<ArbitraryDataIndexScorecard> scorecards = ArbitraryIndexUtils.getArbitraryDataIndexScorecards(indices);

        Assert.assertEquals(0, scorecards.size());
    }

    @Test
    public void testGetArbitraryDataIndexScorecards2() {

        List<ArbitraryDataIndexDetail> indices = new ArrayList<>();


        String issuer = "me";
        int rank = 1;
        String term = "secret";
        String name = "king";
        int category = 1;
        String link = "data-location";

        ArbitraryDataIndex index = new ArbitraryDataIndex(term, name, category, link);
        String indexIdentifer = "index-identifier";
        ArbitraryDataIndexDetail detail1 = new ArbitraryDataIndexDetail(issuer, rank, index, indexIdentifer);

        indices.add(detail1);
        List<ArbitraryDataIndexScorecard> scorecards = ArbitraryIndexUtils.getArbitraryDataIndexScorecards(indices);

        Assert.assertEquals(1, scorecards.size());

        Assert.assertEquals(1, scorecards.get(0).getScore(), 0.01);
    }

    @Test
    public void testGetArbitraryDataIndexScorecards3() {

        List<ArbitraryDataIndexDetail> indices = new ArrayList<>();

        int rank = 1;
        String term = "secret";
        String name = "king";
        int category = 1;
        String link = "data-location";

        String issuer1 = "me";
        String issuer2 = "you";

        ArbitraryDataIndex index = new ArbitraryDataIndex(term, name, category, link);
        String indexIdentifer = "index-identifier";

        ArbitraryDataIndexDetail detail1 = new ArbitraryDataIndexDetail(issuer1, rank, index, indexIdentifer);
        indices.add(detail1);

        ArbitraryDataIndexDetail detail2 = new ArbitraryDataIndexDetail(issuer2, rank, index, indexIdentifer);
        indices.add(detail2);

        List<ArbitraryDataIndexScorecard> scorecards = ArbitraryIndexUtils.getArbitraryDataIndexScorecards(indices);

        Assert.assertEquals(1, scorecards.size());

        Assert.assertEquals(2, scorecards.get(0).getScore(), 0.01);
    }

    @Test
    public void testGetArbitraryDataIndexScorecards4() {

        List<ArbitraryDataIndexDetail> indices = new ArrayList<>();

        int rank = 1;
        String term = "secret";
        String name = "king";
        int category = 1;
        String issuer = "me";

        String link1 = "data-location-1";
        ArbitraryDataIndex index1 = new ArbitraryDataIndex(term, name, category, link1);
        String indexIdentifer1 = "index-identifier-1";

        ArbitraryDataIndexDetail detail1 = new ArbitraryDataIndexDetail(issuer, rank, index1, indexIdentifer1);
        indices.add(detail1);

        String link2 = "data-location-2";
        ArbitraryDataIndex index2 = new ArbitraryDataIndex(term, name, category, link2);
        String indexIdentifer2 = "index-identifier-2";

        ArbitraryDataIndexDetail detail2 = new ArbitraryDataIndexDetail(issuer, rank, index2, indexIdentifer2);
        indices.add(detail2);

        List<ArbitraryDataIndexScorecard> scorecards = ArbitraryIndexUtils.getArbitraryDataIndexScorecards(indices);

        Assert.assertEquals(2, scorecards.size());

        Assert.assertEquals(1, scorecards.get(0).getScore(), 0.01);
        Assert.assertEquals(1, scorecards.get(1).getScore(), 0.01);
    }
}