package com.arka.smp.economy;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * All money is stored as a whole number of paise (1 rupee = 100) in a long. No floating point
 * is ever used for balances, so there is no rounding drift, NaN or infinity to exploit.
 */
public final class Money {
    public static final long SCALE = 100;
    /** Hard ceiling for any single amount or balance: 1,000,000,000,000 rupees. */
    public static final long MAX = 1_000_000_000_000L * SCALE;

    private static final Pattern AMOUNT = Pattern.compile("\\d{1,13}(\\.\\d{1,2})?");

    private Money() {
    }

    /**
     * Parse player input like "500", "1,000", "₹250.50". Returns paise, or null when the input is
     * not a clean positive amount with at most 2 decimals (rejects negatives, NaN, exponents, junk).
     */
    public static Long parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace("₹", "").replace(",", "");
        if (s.isEmpty() || s.length() > 16 || !AMOUNT.matcher(s).matches()) return null;
        BigDecimal minor = new BigDecimal(s).movePointRight(2);
        if (minor.compareTo(BigDecimal.valueOf(MAX)) > 0) return null;
        long v = minor.longValueExact();
        return v > 0 ? v : null;
    }

    /** 1234567.5 rupees -> "12,34,567.50" (Indian digit grouping). Decimals only when non-zero. */
    public static String plain(long paise) {
        long rupees = paise / SCALE;
        long rest = paise % SCALE;
        String s = Long.toString(rupees);
        StringBuilder out = new StringBuilder();
        int len = s.length();
        if (len <= 3) {
            out.append(s);
        } else {
            String head = s.substring(0, len - 3);
            String tail = s.substring(len - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) out.append(',');
                out.append(head.charAt(i));
            }
            out.append(',').append(tail);
        }
        if (rest != 0) out.append('.').append(String.format("%02d", rest));
        return out.toString();
    }

    public static double toRupees(long paise) {
        return paise / (double) SCALE;
    }
}
