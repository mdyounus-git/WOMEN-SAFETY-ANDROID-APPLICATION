package com.example.womensafety.core;

import androidx.annotation.NonNull;

/** Small, dependency-free helpers for Indian mobile numbers. */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /** Keeps digits and a leading '+'. */
    @NonNull
    public static String clean(@NonNull String raw) {
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("[^0-9]", "");
        return trimmed.startsWith("+") ? "+" + digits : digits;
    }

    /** True for a 10-digit Indian mobile number, or any number already in +country format. */
    public static boolean isValid(@NonNull String raw) {
        String c = clean(raw);
        if (c.startsWith("+")) return c.length() >= 11 && c.length() <= 16;
        if (c.length() == 11 && c.startsWith("0")) c = c.substring(1);
        if (c.length() == 12 && c.startsWith("91")) c = c.substring(2);
        return c.length() == 10 && c.charAt(0) >= '6';
    }

    @NonNull
    public static String toInternational(@NonNull String raw) {
        String c = clean(raw);
        if (c.startsWith("+")) return c;
        if (c.length() == 11 && c.startsWith("0")) c = c.substring(1);
        if (c.length() == 12 && c.startsWith("91")) return "+" + c;
        return "+91" + c;
    }
}
