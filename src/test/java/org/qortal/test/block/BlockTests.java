package org.qortal.test.block;

import org.checkerframework.checker.units.qual.K;
import org.junit.Assert;
import org.junit.Test;
import org.qortal.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class BlockTests {

    @Test
    public void testDistributeToAccountsOneDistribution(){
        List<String> addresses = new ArrayList<>();
        addresses.add("a");
        addresses.add("b");
        addresses.add("c");

        HashMap<String, Long> balanceByAddress = new HashMap<>();
        long total = Block.distributeToAccounts( 10L, addresses, balanceByAddress);

        Assert.assertEquals(9, total);

        Assert.assertEquals(3, balanceByAddress.size());
        Assert.assertTrue(balanceByAddress.containsKey("a"));
        Assert.assertTrue(balanceByAddress.containsKey("b"));
        Assert.assertTrue(balanceByAddress.containsKey("c"));
        Assert.assertEquals(3L, balanceByAddress.getOrDefault("a", 0L).longValue());
        Assert.assertEquals(3L, balanceByAddress.getOrDefault("b", 0L).longValue());
        Assert.assertEquals(3L, balanceByAddress.getOrDefault("c", 0L).longValue());
    }

    @Test
    public void testDistributeToAccountsTwoDistributions(){
        List<String> addresses = new ArrayList<>();
        addresses.add("a");
        addresses.add("b");
        addresses.add("c");

        HashMap<String, Long> balanceByAddress = new HashMap<>();
        long total1 = Block.distributeToAccounts( 10L, addresses, balanceByAddress);
        long total2 = Block.distributeToAccounts( 20L, addresses, balanceByAddress);

        Assert.assertEquals(9, total1);
        Assert.assertEquals(18, total2);

        Assert.assertEquals(3, balanceByAddress.size());
        Assert.assertTrue(balanceByAddress.containsKey("a"));
        Assert.assertTrue(balanceByAddress.containsKey("b"));
        Assert.assertTrue(balanceByAddress.containsKey("c"));
        Assert.assertEquals(9L, balanceByAddress.getOrDefault("a", 0L).longValue());
        Assert.assertEquals(9L, balanceByAddress.getOrDefault("b", 0L).longValue());
        Assert.assertEquals(9L, balanceByAddress.getOrDefault("c", 0L).longValue());
    }
}
