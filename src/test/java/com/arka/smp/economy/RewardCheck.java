package com.arka.smp.economy;

/** Checks the growing hourly reward. Run: java -cp target/classes;target/test-classes ...RewardCheck */
public final class RewardCheck {
    static int failed = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (!ok) failed++;
    }

    static long r(double hours) {
        return RewardManager.amountFor(10_000, 3.0, 100_000, Math.round(hours * 3600));   // base 100, 3%, cap 1,000 (paise)
    }

    public static void main(String[] args) {
        check("a new player gets the base reward (100)", r(0) == 10_000);
        check("after 1 hour the reward is 103", r(1) == 10_300);
        check("after 25 hours about 209", Math.abs(r(25) - 20_938) <= 1);
        check("after 50 hours about 438", Math.abs(r(50) - 43_839) <= 1);
        check("the reward only ever goes up with hours", r(10) >= r(5) && r(60) >= r(59) && r(77) >= r(76));
        check("it is capped at 1,000 from about 78 hours and stays there", r(77) < 100_000 && r(78) == 100_000 && r(1000) == 100_000 && r(10_000_000) == 100_000);
        check("growth is exponential: equal hours multiply the reward by the same factor",
                Math.abs((double) r(40) / r(20) - (double) r(20) / r(0)) < 0.01);
        check("0% growth means a constant reward", RewardManager.amountFor(10_000, 0, 100_000, 3600L * 500) == 10_000);
        check("negative and NaN growth mean the base amount", RewardManager.amountFor(10_000, -5, 100_000, 3600L * 500) == 10_000
                && RewardManager.amountFor(10_000, Double.NaN, 100_000, 3600L * 500) == 10_000);
        check("a cap below the base can never lower the reward", RewardManager.amountFor(10_000, 3, 5_000, 3600L * 500) == 10_000);
        check("a huge growth number is limited (no overflow, never above the cap)",
                RewardManager.amountFor(10_000, 1e9, 100_000, 3600L * 10_000) == 100_000);
        check("no base amount means no reward", RewardManager.amountFor(0, 3, 100_000, 3600L * 100) == 0);
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        if (failed > 0) System.exit(1);
    }
}
