package com.arka.smp.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/** Checks the /baltop order. Run: java -cp target/classes;target/test-classes com.arka.smp.economy.TopCheck */
public final class TopCheck {
    static int failed = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (!ok) failed++;
    }

    static EconomyManager.TopEntry e(String name, long balance) {
        return new EconomyManager.TopEntry(UUID.nameUUIDFromBytes(name.getBytes()), name, balance);
    }

    /** The order before this was made faster: it built a new lower-case string inside every comparison. */
    static List<EconomyManager.TopEntry> oldOrder(List<EconomyManager.TopEntry> in) {
        List<EconomyManager.TopEntry> list = new ArrayList<>(in);
        list.sort(Comparator.comparingLong(EconomyManager.TopEntry::balance).reversed()
                .thenComparing(x -> x.name().toLowerCase(Locale.ROOT)));
        return list;
    }

    public static void main(String[] args) {
        List<EconomyManager.TopEntry> in = List.of(e("bob", 500), e("Alice", 500), e("carol", 900), e("Dave", 0), e("alex", 500));
        List<String> names = EconomyManager.ranked(in).stream().map(EconomyManager.TopEntry::name).toList();
        check("richest first, ties by name ignoring case: " + names, names.equals(List.of("carol", "Alice", "alex", "bob", "Dave"))
                || names.equals(List.of("carol", "alex", "Alice", "bob", "Dave")));
        check("an empty list is fine", EconomyManager.ranked(List.of()).isEmpty());

        // same order as the old code on a few thousand random accounts, with many ties and mixed case
        Random r = new Random(7);
        List<EconomyManager.TopEntry> big = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            String n = (r.nextBoolean() ? "Player" : "player") + r.nextInt(1500);
            big.add(new EconomyManager.TopEntry(new UUID(r.nextLong(), r.nextLong()), n, r.nextInt(40) * 100L));
        }
        List<EconomyManager.TopEntry> a = EconomyManager.ranked(big), b = oldOrder(big);
        check("same order as before on 5,000 random accounts", a.equals(b));

        long t0 = System.nanoTime();
        for (int i = 0; i < 20; i++) EconomyManager.ranked(big);
        long fast = (System.nanoTime() - t0) / 1_000_000L;
        t0 = System.nanoTime();
        for (int i = 0; i < 20; i++) oldOrder(big);
        long slow = (System.nanoTime() - t0) / 1_000_000L;
        System.out.println("  INFO  20 sorts of 5,000 accounts: new " + fast + " ms, old " + slow + " ms");
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        if (failed != 0) System.exit(1);
    }
}
