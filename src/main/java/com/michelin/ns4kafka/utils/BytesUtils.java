package com.michelin.ns4kafka.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BytesUtils {
    public static final String BYTE = "B";
    public static final String KIBIBYTE = "KiB";
    public static final String MEBIBYTE = "MiB";
    public static final String GIBIBYTE = "GiB";

    /**
     * Converts given bytes to either kibibyte, mebibite or gibibyte
     * @param bytes The bytes to convert
     * @return The converted value as human-readable value
     */
    public static String bytesToHumanReadable(long bytes) {
        double kibibyte = 1024;
        double mebibyte = kibibyte * 1024;
        double gibibyte = mebibyte * 1024;

        if (bytes >= kibibyte && bytes < mebibyte) {
            return BigDecimal.valueOf(bytes / kibibyte).setScale(3, RoundingMode.CEILING).doubleValue() + KIBIBYTE;
        }

        if (bytes >= mebibyte && bytes < gibibyte) {
            return BigDecimal.valueOf(bytes / mebibyte).setScale(3, RoundingMode.CEILING).doubleValue() + MEBIBYTE;
        }

        if (bytes >= gibibyte) {
            return BigDecimal.valueOf(bytes / gibibyte).setScale(3, RoundingMode.CEILING).doubleValue() + GIBIBYTE;
        }

        return bytes + BYTE;
    }

    /**
     * Converts given human-readable measure to bytes
     * @param quota The measure to convert
     * @return The converted value as bytes
     */
    public static long humanReadableToBytes(String quota) {
        long kibibyte = 1024;
        long mebibyte = kibibyte * 1024;
        long gibibyte = mebibyte * 1024;

        if (quota.endsWith(KIBIBYTE)) {
            return BigDecimal.valueOf(Double.parseDouble(quota.replace(KIBIBYTE, "")) * kibibyte)
                    .setScale(0, RoundingMode.CEILING)
                    .longValue();
        }

        if (quota.endsWith(MEBIBYTE)) {
            return BigDecimal.valueOf(Double.parseDouble(quota.replace(MEBIBYTE, "")) * mebibyte)
                    .setScale(0, RoundingMode.CEILING)
                    .longValue();
        }

        if (quota.endsWith(GIBIBYTE)) {
            return BigDecimal.valueOf(Double.parseDouble(quota.replace(GIBIBYTE, "")) * gibibyte)
                    .setScale(0, RoundingMode.CEILING)
                    .longValue();
        }

        return Long.parseLong(quota.replace(BYTE, ""));
    }

    private BytesUtils() {}
}
