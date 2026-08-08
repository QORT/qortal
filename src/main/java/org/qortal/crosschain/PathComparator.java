package org.qortal.crosschain;

/**
 * Class PathComparator
 */
public class PathComparator implements java.util.Comparator<AddressInfo> {

    private int max;

    public PathComparator(int max) {
        this.max = max;
    }

    @Override
    public int compare(AddressInfo info1, AddressInfo info2) {
        return compareAtLevel(info1, info2, 0);
    }

    private int compareAtLevel(AddressInfo info1, AddressInfo info2, int level) {

        if( level < 0 ) return 0;

        int compareTo = info1.getPath().get(level).compareTo(info2.getPath().get(level));

        if(compareTo != 0 || level == max) return compareTo;

        return compareAtLevel(info1, info2,level + 1);
    }
}
