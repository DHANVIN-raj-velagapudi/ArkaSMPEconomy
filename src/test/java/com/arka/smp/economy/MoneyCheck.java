package com.arka.smp.economy;

/** Checks the short money form (k, L, Cr) and the short input form. Run: java -cp target/classes;target/test-classes ...MoneyCheck */
public final class MoneyCheck {
    static int failed = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (!ok) failed++;
    }

    static String c(double rupees) {
        return Money.compact(Math.round(rupees * 100));
    }

    static boolean is(String input, long rupeesTimes100) {
        Long v = Money.parse(input);
        return v != null && v == rupeesTimes100;
    }

    public static void main(String[] args) {
        check("under 1,000 stays exact", c(0).equals("0") && c(999).equals("999") && c(999.5).equals("999.50") && c(0.25).equals("0.25"));
        check("1,000 is 1k", c(1000).equals("1k"));
        check("1,500 is 1.5k and 1,250 is 1.25k", c(1500).equals("1.5k") && c(1250).equals("1.25k"));
        check("12,345 is 12.34k (cut, not rounded up)", c(12345).equals("12.34k"));
        check("99,999 is 99.99k and never 100k", c(99999).equals("99.99k") && c(99999.99).equals("99.99k"));
        check("100,000 is 1L (one lakh)", c(100000).equals("1L"));
        check("2,50,000 is 2.5L", c(250000).equals("2.5L"));
        check("99,99,999 is 99.99L and never 100L", c(9999999).equals("99.99L"));
        check("1,00,00,000 (100L) is 1Cr (one crore)", c(10000000).equals("1Cr"));
        check("12,50,00,000 is 12.5Cr", c(125000000).equals("12.5Cr"));
        check("a lot: 1,000,000,000,000 rupees is 1,00,000Cr", c(1_000_000_000_000.0).equals("1,00,000Cr"));
        check("no paise: 1,250.50 shows as 1,250 (cut, not rounded up) and 999.99 as 999",
                Money.plain(Money.noPaise(125050)).equals("1,250") && Money.plain(Money.noPaise(99999)).equals("999"));
        check("no paise: 0.15 shows as 0, 9.99 as 9, whole amounts are unchanged, nothing is rounded up",
                Money.noPaise(15) == 0 && Money.noPaise(999) == 900 && Money.noPaise(1000) == 1000 && Money.noPaise(0) == 0
                        && Money.noPaise(-150) == -100);
        check("no paise with the short form: 1,250.50 is 1.25k and 12,345.67 is 12.34k",
                Money.compact(Money.noPaise(125050)).equals("1.25k") && Money.compact(Money.noPaise(1234567)).equals("12.34k"));
        check("plain form is unchanged", Money.plain(123456750).equals("12,34,567.50"));

        check("parse: plain numbers still work", is("500", 50000) && is("1,000", 100000) && is("₹250.50", 25050));
        check("parse: 2.5k = 2,500", is("2.5k", 250000) && is("2K", 200000));
        check("parse: 1.5l = 1,50,000", is("1.5l", 15000000L) && is("2L", 20000000L));
        check("parse: 1cr = 1,00,00,000", is("1cr", 1_000_000_000L) && is("1.25Cr", 1_250_000_000L));
        check("parse: junk and negatives are refused", Money.parse("k") == null && Money.parse("-5k") == null
                && Money.parse("1.234k") == null && Money.parse("1e3") == null && Money.parse("abc") == null && Money.parse("0k") == null);
        check("parse: too much is refused", Money.parse("2000000cr") == null);
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        if (failed > 0) System.exit(1);
    }
}
