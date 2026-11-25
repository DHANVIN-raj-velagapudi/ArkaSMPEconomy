package com.arka.smp.economy;

import java.math.BigDecimal;
import java.util.Locale;
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
     * Parse player input like "500", "1,000", "₹250.50", or the short forms "2.5k" (2,500), "1.5l" (1,50,000)
     * and "1cr" (1,00,00,000). Returns paise, or null when the input is not a clean positive amount with at
     * most 2 decimals (rejects negatives, NaN, exponents, junk).
     */
    public static Long parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace("₹", "").replace(",", "").toLowerCase(Locale.ROOT);
        int exp = 0;
        if (s.endsWith("cr")) {
            exp = 7;
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("l")) {
            exp = 5;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("k")) {
            exp = 3;
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty() || s.length() > 16 || !AMOUNT.matcher(s).matches()) return null;
        BigDecimal minor = new BigDecimal(s).movePointRight(2 + exp);
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

    /**
     * Short form for display: 999 stays "999", 1,500 is "1.5k", 99,999 is "99.99k", 1,00,000 is "1L", 2,50,000 is
     * "2.5L" and 1,00,00,000 is "1Cr". So k starts at 1,000, L (lakh) at 100k and Cr (crore) at 100L.
     * At most 2 decimals, cut off and never rounded up, so 99,999.90 never shows as "100k".
     */
    public static String compact(long paise) {
        if (paise < 1_000 * SCALE) return plain(paise);
        long div;
        String unit;
        if (paise >= 10_000_000L * SCALE) {
            div = 10_000_000L;
            unit = "Cr";
        } else if (paise >= 100_000L * SCALE) {
            div = 100_000L;
            unit = "L";
        } else {
            div = 1_000L;
            unit = "k";
        }
        long hundredths = paise / div;       // paise / div = (rupees x 100) / div = hundredths of the unit
        long whole = hundredths / 100;
        long frac = hundredths % 100;
        StringBuilder out = new StringBuilder(plain(whole * SCALE));
        if (frac != 0) {
            String f2 = String.format("%02d", frac);
            out.append('.').append(f2.endsWith("0") ? f2.substring(0, 1) : f2);
        }
        return out.append(unit).toString();
    }

    public static double toRupees(long paise) {
        return paise / (double) SCALE;
    }
}
