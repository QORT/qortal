package org.qortal.utils;

import org.qortal.list.ResourceListManager;

import java.util.List;

public class ListUtils {

    /* Blocking */

    public static List<String> blockedNames() {
        return ResourceListManager.getInstance().getStringsInListsWithPrefix("blockedNames");
    }

    public static boolean isNameBlocked(String name) {
        return ResourceListManager.getInstance().listWithPrefixContains("blockedNames", name, false);
    }

    public static boolean isAddressBlocked(String address) {
        return ResourceListManager.getInstance().listWithPrefixContains("blockedAddresses", address, true);
    }


    /* Following */

    public static List<String> followedNames() {
        return ResourceListManager.getInstance().getStringsInListsWithPrefix("followedNames");
    }

    public static boolean isFollowingName(String name) {
        return ResourceListManager.getInstance().listWithPrefixContains("followedNames", name, false);
    }

    public static int followedNamesCount() {
        return ListUtils.followedNames().size();
    }

}
