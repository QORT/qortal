package org.qortal.test.utils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.block.Block;
import org.qortal.block.BlockChain;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.utils.Groups;

import java.util.List;

import static org.junit.Assert.*;

public class GroupsTests extends Common {

    public static final String ALICE = "alice";
    public static final String BOB = "bob";
    public static final String CHLOE = "chloe";
    public static final String DILBERT = "dilbert";


    private static final int HEIGHT_1 = 5;
    private static final int HEIGHT_2 = 8;
    private static final int HEIGHT_3 = 12;

    @Before
    public void beforeTest() throws DataException {
        Common.useDefaultSettings();
    }

    @After
    public void afterTest() throws DataException {
        Common.orphanCheck();
    }

    @Test
    public void testGetGroupIdsToMintSimple() {
        List<Integer> ids = Groups.getGroupIdsToMint(BlockChain.getInstance(), 0);

        Assert.assertNotNull(ids);
        Assert.assertEquals(0, ids.size());
    }

    @Test
    public void testGetGroupIdsToMintComplex() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            Block block1 = BlockUtils.mintBlocks(repository, HEIGHT_1);
            int height1 = block1.getBlockData().getHeight().intValue();
            assertEquals(HEIGHT_1 + 1, height1);

            List<Integer> ids1 = Groups.getGroupIdsToMint(BlockChain.getInstance(), height1);

            Assert.assertEquals(1, ids1.size() );
            Assert.assertTrue( ids1.contains( 694 ) );

            Block block2 = BlockUtils.mintBlocks(repository, HEIGHT_2 - HEIGHT_1);
            int height2 = block2.getBlockData().getHeight().intValue();
            assertEquals( HEIGHT_2 + 1, height2);

            List<Integer> ids2 = Groups.getGroupIdsToMint(BlockChain.getInstance(), height2);

            Assert.assertEquals(2, ids2.size() );

            Assert.assertTrue( ids2.contains( 694 ) );
            Assert.assertTrue( ids2.contains( 800 ) );

            Block block3 = BlockUtils.mintBlocks(repository, HEIGHT_3 - HEIGHT_2);
            int height3 = block3.getBlockData().getHeight().intValue();
            assertEquals( HEIGHT_3 + 1, height3);

            List<Integer> ids3 = Groups.getGroupIdsToMint(BlockChain.getInstance(), height3);

            Assert.assertEquals( 1, ids3.size() );

            Assert.assertTrue( ids3.contains( 800 ) );
        }
    }

    @Test
    public void testMemberExistsInAnyGroupSimple() throws DataException {

        try (final Repository repository = RepositoryManager.getRepository()) {

            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");

            // Create group
            int groupId = GroupsTestUtils.createGroup(repository, alice, "closed-group", false);

            // Confirm Bob is not a member
            Assert.assertFalse( Groups.memberExistsInAnyGroup(repository.getGroupRepository(), List.of(groupId), bob.getAddress()) );

            // Bob to join
            GroupsTestUtils.joinGroup(repository, bob, groupId);

            // Confirm Bob still not a member
            assertFalse(GroupsTestUtils.isMember(repository, bob.getAddress(), groupId));

            // Have Alice 'invite' Bob to confirm membership
            GroupsTestUtils.groupInvite(repository, alice, groupId, bob.getAddress(), 0); // non-expiring invite

            // Confirm Bob now a member
            Assert.assertTrue( Groups.memberExistsInAnyGroup(repository.getGroupRepository(), List.of(groupId), bob.getAddress()) );
        }
    }

    @Test
    public void testGroupsListedFunctionality() throws DataException {

        try (final Repository repository = RepositoryManager.getRepository()) {

            PrivateKeyAccount alice = Common.getTestAccount(repository, ALICE);
            PrivateKeyAccount bob = Common.getTestAccount(repository, BOB);
            PrivateKeyAccount chloe = Common.getTestAccount(repository, CHLOE);
            PrivateKeyAccount dilbert = Common.getTestAccount(repository, DILBERT);

            // Create groups
            int group1Id = GroupsTestUtils.createGroup(repository, alice, "group-1", false);
            int group2Id = GroupsTestUtils.createGroup(repository, bob, "group-2", false);

            // test memberExistsInAnyGroup
            Assert.assertTrue(Groups.memberExistsInAnyGroup(repository.getGroupRepository(), List.of(group1Id, group2Id), alice.getAddress()));
            Assert.assertFalse(Groups.memberExistsInAnyGroup(repository.getGroupRepository(), List.of(group1Id, group2Id), chloe.getAddress()));

            // alice is a member
            Assert.assertTrue(GroupsTestUtils.isMember(repository, alice.getAddress(), group1Id));
            List<String> allMembersBeforeJoin = Groups.getAllMembers(repository.getGroupRepository(), List.of(group1Id));

            // assert one member
            Assert.assertNotNull(allMembersBeforeJoin);
            Assert.assertEquals(1, allMembersBeforeJoin.size());

            List<String> allAdminsBeforeJoin = Groups.getAllAdmins(repository.getGroupRepository(), List.of(group1Id));

            // assert one admin
            Assert.assertNotNull(allAdminsBeforeJoin);
            Assert.assertEquals( 1, allAdminsBeforeJoin.size());

            // Bob to join
            GroupsTestUtils.joinGroup(repository, bob, group1Id);

            // Have Alice 'invite' Bob to confirm membership
            GroupsTestUtils.groupInvite(repository, alice, group1Id, bob.getAddress(), 0); // non-expiring invite

            List<String> allMembersAfterJoin = Groups.getAllMembers(repository.getGroupRepository(), List.of(group1Id));

            // alice and bob are members
            Assert.assertNotNull(allMembersAfterJoin);
            Assert.assertEquals(2, allMembersAfterJoin.size());

            List<String> allAdminsAfterJoin = Groups.getAllAdmins(repository.getGroupRepository(), List.of(group1Id));

            // assert still one admin
            Assert.assertNotNull(allAdminsAfterJoin);
            Assert.assertEquals(1, allAdminsAfterJoin.size());

            List<String> allAdminsFor2Groups = Groups.getAllAdmins(repository.getGroupRepository(), List.of(group1Id, group2Id));

            // assert 2 admins when including the second group
            Assert.assertNotNull(allAdminsFor2Groups);
            Assert.assertEquals(2, allAdminsFor2Groups.size());

            List<String> allMembersFor2Groups = Groups.getAllMembers(repository.getGroupRepository(), List.of(group1Id, group2Id));

            // assert 2 members when including the seconds group
            Assert.assertNotNull(allMembersFor2Groups);
            Assert.assertEquals(2, allMembersFor2Groups.size());

            GroupsTestUtils.leaveGroup(repository, bob, group1Id);

            List<String> allMembersForAfterBobLeavesGroup1InAllGroups = Groups.getAllMembers(repository.getGroupRepository(), List.of(group1Id, group2Id));

            // alice and bob are members of one group still
            Assert.assertNotNull(allMembersForAfterBobLeavesGroup1InAllGroups);
            Assert.assertEquals(2, allMembersForAfterBobLeavesGroup1InAllGroups.size());

            GroupsTestUtils.groupInvite(repository, alice, group1Id, chloe.getAddress(), 3600);
            GroupsTestUtils.groupInvite(repository, bob, group2Id, chloe.getAddress(), 3600);

            GroupsTestUtils.joinGroup(repository, chloe, group1Id);
            GroupsTestUtils.joinGroup(repository, chloe, group2Id);

            List<String> allMembersAfterDilbert = Groups.getAllMembers((repository.getGroupRepository()), List.of(group1Id, group2Id));

            // 3 accounts are now members of one group or another
            Assert.assertNotNull(allMembersAfterDilbert);
            Assert.assertEquals(3, allMembersAfterDilbert.size());
        }
    }

}