package org.qortal.utils;

import java.util.Arrays;

public class EnumUtils {

    public static String[] getNames(Class<? extends Enum<?>> e) {
        return Arrays.stream(e.getEnumConstants()).map(Enum::name).toArray(String[]::new);
    }

    public static String getNames(Class<? extends Enum<?>> e, String delimiter) {
        return String.join(delimiter, EnumUtils.getNames(e));
    }

}
