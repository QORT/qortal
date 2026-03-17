package org.qortal.utils;

import org.qortal.block.BlockChain;
import org.qortal.data.group.GroupAdminData;
import org.qortal.data.group.GroupMemberData;
import org.qortal.repository.DataException;
import org.qortal.repository.GroupRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class Groups
 *
 * A utility class for group related functionality.
 */
public class Groups {

    /**
     * Does the member exist in any of these groups?
     *
     * @param groupRepository the group data repository
     * @param groupsIds the group Ids to look for the address
     * @param address the address
     *
     * @return true if the address is in any of the groups listed otherwise false
     * @throws DataException
     */
    public static boolean memberExistsInAnyGroup(GroupRepository groupRepository, List<Integer> groupsIds, String address) throws DataException {

        // if any of the listed groups have the address as a member, then return true
        for( Integer groupIdToMint : groupsIds) {
            if( groupRepository.memberExists(groupIdToMint, address) ) {
                return true;
            }
        }

        // if none of the listed groups have the address as a member, then return false
        return false;
    }

    /**
     * Get All Members
     *
     * Get all the group members from a list of groups.
     *
     * @param groupRepository the group data repository
     * @param groupIds the list of group Ids to look at
     *
     * @return the list of all members belonging to any of the groups, no duplicates
     * @throws DataException
     */
    public static List<String> getAllMembers( GroupRepository groupRepository, List<Integer> groupIds ) throws DataException {
        // collect all the members in a set, the set keeps out duplicates
        Set<String> allMembers = new HashSet<>();

        // add all members from each group to the all members set
        for( int groupId : groupIds ) {
            allMembers.addAll( groupRepository.getGroupMembers(groupId).stream().map(GroupMemberData::getMember).collect(Collectors.toList()));
        }

        return new ArrayList<>(allMembers);
    }

    /**
     * Get All Admins
     *
     * Get all the admins from a list of groups.
     *
     * @param groupRepository the group data repository
     * @param groupIds the list of group Ids to look at
     *
     * @return the list of all admins to any of the groups, no duplicates
     * @throws DataException
     */
    public static List<String> getAllAdmins( GroupRepository groupRepository, List<Integer> groupIds ) throws DataException {
        // collect all the admins in a set, the set keeps out duplicates
        Set<String> allAdmins = new HashSet<>();

        // collect admins for each group
        for( int groupId : groupIds ) {
            allAdmins.addAll( groupRepository.getGroupAdmins(groupId).stream().map(GroupAdminData::getAdmin).collect(Collectors.toList()) );
        }

        return new ArrayList<>(allAdmins);
    }

    /**
     * Get Group Ids To Mint
     *
     * @param blockchain the blockchain
     * @param blockchainHeight the block height to mint
     *
     * @return the group Ids for the minting groups at the height given
     */
    public static List<Integer> getGroupIdsToMint(BlockChain blockchain, int blockchainHeight) {

        // sort heights lowest to highest
        Comparator<BlockChain.IdsForHeight> compareByHeight = Comparator.comparingInt(entry -> entry.height);

        // sort heights highest to lowest
        Comparator<BlockChain.IdsForHeight> compareByHeightReversed = compareByHeight.reversed();

        // get highest height that is less than the blockchain height
        Optional<BlockChain.IdsForHeight> ids = blockchain.getMintingGroupIds().stream()
                .filter(entry -> entry.height < blockchainHeight)
                .sorted(compareByHeightReversed)
                .findFirst();

        if( ids.isPresent()) {
            return ids.get().ids;
        }
        else {
            return new ArrayList<>(0);
        }
    }
}