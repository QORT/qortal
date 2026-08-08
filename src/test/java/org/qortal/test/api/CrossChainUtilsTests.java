package org.qortal.test.api;

import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.qortal.api.model.CrossChainTradeLedgerEntry;
import org.qortal.api.resource.CrossChainUtils;
import org.qortal.test.common.ApiCommon;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrossChainUtilsTests extends ApiCommon {

    @Test
    public void testReduceDelimeters1() {

        String string = CrossChainUtils.reduceDelimeters("", 1, ',');

        Assert.assertEquals("", string);
    }

    @Test
    public void testReduceDelimeters2() {

        String string = CrossChainUtils.reduceDelimeters("0.17.0", 1, ',');

        Assert.assertEquals("0.17.0", string);
    }

    @Test
    public void testReduceDelimeters3() {

        String string = CrossChainUtils.reduceDelimeters("0.17.0", 1, '.');

        Assert.assertEquals("0.17", string);
    }

    @Test
    public void testReduceDelimeters4() {

        String string = CrossChainUtils.reduceDelimeters("0.17.0", 2, '.');

        Assert.assertEquals("0.17.0", string);
    }

    @Test
    public void testReduceDelimeters5() {

        String string = CrossChainUtils.reduceDelimeters("0.17.0", 10, '.');

        Assert.assertEquals("0.17.0", string);
    }

    @Test
    public void testReduceDelimeters6() {

        String string = CrossChainUtils.reduceDelimeters("0.17.0", -1, '.');

        Assert.assertEquals("0.17.0", string);
    }

    @Test
    public void testReduceDelimeters7() {

        String string = CrossChainUtils.reduceDelimeters("abcdef abcdef", 1, 'd');

        Assert.assertEquals("abcdef abc", string);
    }

    @Test
    public void testGetVersionDecimalThrowNumberFormatExceptionTrue() {

        boolean thrown = false;

        try {
            Map<String, String> map = new HashMap<>();
            map.put("x", "v");
            double versionDecimal = CrossChainUtils.getVersionDecimal(new JSONObject(map), "x");
        }
        catch( NumberFormatException e ) {
            thrown = true;
        }

        Assert.assertTrue(thrown);
    }

    @Test
    public void testGetVersionDecimalThrowNullPointerExceptionTrue() {

        boolean thrown = false;

        try {
            Map<String, String> map = new HashMap<>();

            double versionDecimal = CrossChainUtils.getVersionDecimal(new JSONObject(map), "x");
        }
        catch( NullPointerException e ) {
            thrown = true;
        }

        Assert.assertTrue(thrown);
    }

    @Test
    public void testGetVersionDecimalThrowAnyExceptionFalse() {

        boolean thrown = false;

        try {
            Map<String, String> map = new HashMap<>();
            map.put("x", "5");
            double versionDecimal = CrossChainUtils.getVersionDecimal(new JSONObject(map), "x");
        }
        catch( NullPointerException | NumberFormatException e ) {
            thrown = true;
        }

        Assert.assertFalse(thrown);
    }

    @Test
    public void testGetVersionDecimal1() {

        boolean thrown = false;

        double versionDecimal = 0d;

        try {
            Map<String, String> map = new HashMap<>();
            map.put("x", "5.0.0");
            versionDecimal = CrossChainUtils.getVersionDecimal(new JSONObject(map), "x");
        }
        catch( NullPointerException | NumberFormatException e ) {
            thrown = true;
        }

        Assert.assertEquals(5, versionDecimal, 0.001);
        Assert.assertFalse(thrown);
    }

    @Test
    public void testWriteToLedgerHeaderOnly() throws IOException {
        CrossChainUtils.writeToLedger(new PrintWriter(System.out), new ArrayList<>());
    }

    @Test
    public void testWriteToLedgerOneRow() throws IOException {
        CrossChainUtils.writeToLedger(
            new PrintWriter(System.out),
            List.of(
                new CrossChainTradeLedgerEntry(
                    "QORT",
                    "LTC",
                    1000,
                    0,
                    "LTC",
                    1,
                    System.currentTimeMillis())
            )
        );
    }

    @Test
    public void testWriteToLedgerTwoRows() throws IOException {
        CrossChainUtils.writeToLedger(
            new PrintWriter(System.out),
                List.of(
                    new CrossChainTradeLedgerEntry(
                        "QORT",
                        "LTC",
                        1000,
                        0,
                        "LTC",
                        1,
                        System.currentTimeMillis()
                    ),
                    new CrossChainTradeLedgerEntry(
                        "LTC",
                        "QORT",
                        1,
                        0,
                        "LTC",
                        1000,
                        System.currentTimeMillis()
                    )
                )
        );
    }
}
