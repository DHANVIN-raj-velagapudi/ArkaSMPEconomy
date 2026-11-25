package com.arka.smp.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Random-graph check of the stability guarantee: whatever the base prices, premium prices, recipes (including
 * un-crafting and cycles), no-buy lists and mining yields look like, buying things, crafting/smelting/cutting
 * them any number of times and selling the result can never make money.
 * Run: java -cp target/classes:target/test-classes com.arka.smp.economy.PriceArbitrageCheck
 */
public final class PriceArbitrageCheck {
    static int failed = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (!ok) failed++;
    }

    /** Independent re-implementation: cheapest cost to obtain each item by buying and processing. */
    static Map<String, Double> cheapest(Map<String, Long> buy, Map<String, Long> sell, List<PriceEngine.Recipe> recipes,
                                        List<PriceEngine.Drop> drops, Map<String, String> rem) {
        Map<String, Double> cost = new HashMap<>();
        buy.forEach((k, v) -> cost.put(k, (double) v));
        for (int round = 0; round < 500; round++) {
            boolean changed = false;
            List<Object[]> edges = new ArrayList<>();
            for (var r : recipes) edges.add(new Object[]{r.out(), (double) r.amount(), r.slots()});
            for (var d : drops) edges.add(new Object[]{d.out(), d.amount(), List.of(List.of(d.in()))});
            for (Object[] e : edges) {
                @SuppressWarnings("unchecked") List<List<String>> slots = (List<List<String>>) e[2];
                double total = 0;
                boolean ok = true;
                for (var slot : slots) {
                    double best = Double.MAX_VALUE;
                    for (String c : slot) {
                        Double v = cost.get(c);
                        if (v == null) continue;                  // cannot be bought: no purchase price
                        String left = rem.get(c);
                        if (left != null) {
                            Double lv = cost.get(left);
                            if (lv == null) lv = 0.0;
                            v = Math.max(0, v - lv);
                        }
                        best = Math.min(best, v);
                    }
                    if (best == Double.MAX_VALUE) {
                        ok = false;
                        break;
                    }
                    total += best;
                }
                if (!ok) continue;
                double per = total / (double) e[1];
                String out = (String) e[0];
                if (!cost.containsKey(out) || per < cost.get(out) - 1e-9) {
                    cost.put(out, per);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return cost;
    }

    public static void main(String[] args) {
        int worst = 0;
        long loweredTotal = 0;
        for (int seed = 1; seed <= 300; seed++) {
            Random rnd = new Random(seed);
            int n = 25 + rnd.nextInt(40);
            List<String> items = new ArrayList<>();
            for (int i = 0; i < n; i++) items.add("ITEM_" + i);
            Map<String, Double> base = new java.util.LinkedHashMap<>();
            for (String it : items) if (rnd.nextInt(4) == 0) base.put(it, Math.round((0.02 + rnd.nextDouble() * 60) * 100) / 100.0);
            Map<String, PriceEngine.Premium> premium = new java.util.LinkedHashMap<>();
            for (String it : items) if (rnd.nextInt(10) == 0) premium.put(it, new PriceEngine.Premium(1 + rnd.nextInt(100), rnd.nextInt(3) == 0 ? 0 : 500 + rnd.nextInt(90000)));
            List<PriceEngine.Recipe> recipes = new ArrayList<>();
            int rc = n * 2;
            for (int i = 0; i < rc; i++) {
                int slotsN = 1 + rnd.nextInt(9);
                List<List<String>> slots = new ArrayList<>();
                for (int s = 0; s < slotsN; s++) {
                    int choices = 1 + (rnd.nextInt(4) == 0 ? rnd.nextInt(4) : 0);
                    List<String> ch = new ArrayList<>();
                    for (int c = 0; c < choices; c++) ch.add(items.get(rnd.nextInt(n)));
                    slots.add(ch);
                }
                recipes.add(new PriceEngine.Recipe(items.get(rnd.nextInt(n)), 1 + rnd.nextInt(9), slots, rnd.nextInt(5) == 0));
            }
            List<PriceEngine.Drop> drops = new ArrayList<>();
            for (int i = 0; i < 4; i++) drops.add(new PriceEngine.Drop(items.get(rnd.nextInt(n)), items.get(rnd.nextInt(n)), 1 + rnd.nextDouble() * 7));
            List<String> noSell = new ArrayList<>();
            List<String> noBuy = new ArrayList<>();
            for (String it : items) {
                if (rnd.nextInt(12) == 0) noSell.add(it);
                if (rnd.nextInt(10) == 0) noBuy.add(it);
            }
            Map<String, String> rem = new HashMap<>();
            if (rnd.nextBoolean()) rem.put(items.get(0), items.get(1));
            var r = PriceEngine.compute(items, base, premium, noSell, noBuy, recipes, drops, rem, new PriceEngine.Rules(3.0, 0.95, 1.0));

            var cost = cheapest(r.buy(), r.sell(), recipes, drops, rem);
            for (var e : r.sell().entrySet()) {
                Double c = cost.get(e.getKey());
                if (c != null && e.getValue() > c * 0.95 + 1e-6) {
                    failed++;
                    System.out.println("  FAIL seed " + seed + ": " + e.getKey() + " sells " + e.getValue() + " but can be had for " + c);
                    break;
                }
            }
            for (var e : r.sell().entrySet()) {
                Long b = r.buy().get(e.getKey());
                if (b != null && b < e.getValue()) {
                    failed++;
                    System.out.println("  FAIL seed " + seed + ": " + e.getKey() + " costs less than it sells for");
                    break;
                }
            }
            for (var e : premium.entrySet()) {
                if (e.getValue().buy() > 0 && r.buy().containsKey(e.getKey())
                        && r.buy().get(e.getKey()) < PriceEngine.toPaise(e.getValue().buy())) {
                    failed++;
                    System.out.println("  FAIL seed " + seed + ": premium buy price of " + e.getKey() + " was lowered");
                    break;
                }
            }
            loweredTotal += r.notes().stream().filter(x -> x.startsWith("sell price lowered")).count();
        }
        check("300 random price tables: no way to profit by buying, processing and selling", failed == 0);
        System.out.println("  (" + loweredTotal + " tables needed the safety clamp)");

        // concrete cases
        List<String> items = List.of("GHAST_TEAR", "ENDER_EYE", "GLASS", "END_CRYSTAL", "NETHER_STAR", "BEACON", "OBSIDIAN",
                "NETHERITE_SCRAP", "GOLD_INGOT", "NETHERITE_INGOT", "COPPER_ORE", "RAW_COPPER", "STONE", "COBBLESTONE");
        Map<String, Double> base = new java.util.LinkedHashMap<>();
        base.put("GLASS", 0.1);
        base.put("ENDER_EYE", 8.0);
        base.put("OBSIDIAN", 1.0);
        base.put("GOLD_INGOT", 4.0);
        base.put("RAW_COPPER", 0.4);
        base.put("COPPER_ORE", 0.2);                                // too cheap: mining it at Fortune III would print money
        base.put("COBBLESTONE", 0.05);
        Map<String, PriceEngine.Premium> prem = new java.util.LinkedHashMap<>();
        prem.put("GHAST_TEAR", new PriceEngine.Premium(5, 3500));
        prem.put("NETHER_STAR", new PriceEngine.Premium(120, 100000));
        prem.put("NETHERITE_SCRAP", new PriceEngine.Premium(25, 4500));
        prem.put("END_CRYSTAL", new PriceEngine.Premium(20, 20000));
        List<PriceEngine.Recipe> rec = List.of(
                PriceEngine.Recipe.class.cast(new PriceEngine.Recipe("BEACON", 1, List.of(List.of("GLASS"), List.of("GLASS"), List.of("GLASS"), List.of("GLASS"), List.of("GLASS"),
                        List.of("OBSIDIAN"), List.of("OBSIDIAN"), List.of("OBSIDIAN"), List.of("NETHER_STAR")), false)),
                new PriceEngine.Recipe("NETHERITE_INGOT", 1, List.of(List.of("NETHERITE_SCRAP"), List.of("NETHERITE_SCRAP"), List.of("NETHERITE_SCRAP"),
                        List.of("NETHERITE_SCRAP"), List.of("GOLD_INGOT"), List.of("GOLD_INGOT"), List.of("GOLD_INGOT"), List.of("GOLD_INGOT")), false),
                new PriceEngine.Recipe("STONE", 1, List.of(List.of("COBBLESTONE")), true));
        var r = PriceEngine.compute(items, base, prem, List.of(), List.of("GHAST_TEAR", "NETHERITE_SCRAP", "NETHER_STAR"), rec,
                List.of(new PriceEngine.Drop("COPPER_ORE", "RAW_COPPER", 7.7)), Map.of(), new PriceEngine.Rules(3.0, 0.95, 1.0));
        check("premium buy price beats the no-buy list (nether star buyable)", r.buy().get("NETHER_STAR") == 10_000_000L);
        check("premium sell price is low next to its buy price", r.sell().get("NETHER_STAR") == 12_000L);
        check("beacon can never be bought for less than its nether star", r.buy().get("BEACON") >= 10_000_000L);
        check("netherite ingot costs at least 4 scrap + 4 gold", r.buy().get("NETHERITE_INGOT") >= 4 * 450_000L + 4 * 1200L);
        check("mining yield clamp: raw copper no longer pays more than the ore costs",
                r.sell().get("RAW_COPPER") * 7.7 <= r.buy().get("COPPER_ORE") * 0.95 + 1);
        check("a premium item without a sell price in the table stays sellable only where allowed", r.sell().containsKey("GHAST_TEAR"));
        // prices written out one by one ("crafted:" section): un-crafting must not make a rare item cheap,
        // and the emerald pair must neither cascade down nor become buyable
        {
            List<String> its = List.of("NETHERITE_SCRAP", "GOLD_INGOT", "NETHERITE_INGOT", "NETHERITE_BLOCK", "EMERALD", "EMERALD_BLOCK");
            Map<String, Double> raw = Map.of("GOLD_INGOT", 4.0, "EMERALD", 4.0);
            Map<String, Double> made = Map.of("NETHERITE_INGOT", 1.10, "NETHERITE_BLOCK", 9.42, "EMERALD_BLOCK", 34.2);
            Map<String, PriceEngine.Premium> pm = Map.of("NETHERITE_SCRAP", new PriceEngine.Premium(25, 4500));
            String[] four = {"NETHERITE_SCRAP", "NETHERITE_SCRAP", "NETHERITE_SCRAP", "NETHERITE_SCRAP"};
            List<List<String>> ingotSlots = new ArrayList<>();
            for (String s : four) ingotSlots.add(List.of(s));
            for (int i = 0; i < 4; i++) ingotSlots.add(List.of("GOLD_INGOT"));
            List<List<String>> blockSlots = new ArrayList<>();
            List<List<String>> emSlots = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                blockSlots.add(List.of("NETHERITE_INGOT"));
                emSlots.add(List.of("EMERALD"));
            }
            List<PriceEngine.Recipe> rs = List.of(
                    new PriceEngine.Recipe("NETHERITE_INGOT", 1, ingotSlots, false),
                    new PriceEngine.Recipe("NETHERITE_BLOCK", 1, blockSlots, false),
                    new PriceEngine.Recipe("NETHERITE_INGOT", 9, List.of(List.of("NETHERITE_BLOCK")), false),
                    new PriceEngine.Recipe("EMERALD_BLOCK", 1, emSlots, false),
                    new PriceEngine.Recipe("EMERALD", 9, List.of(List.of("EMERALD_BLOCK")), false));
            var rr = PriceEngine.compute(its, raw, made, pm, List.of(), List.of("EMERALD"), rs, List.of(), Map.of(),
                    new PriceEngine.Rules(3.0, 0.95, 1.0));
            long scrapBuy = 450_000L, goldBuy = 1_200L;
            check("explicit crafted prices: netherite ingot still costs 4 scrap + 4 gold",
                    rr.buy().get("NETHERITE_INGOT") >= 4 * scrapBuy + 4 * goldBuy);
            check("explicit crafted prices: netherite block can not be bought below 9 ingots",
                    rr.buy().get("NETHERITE_BLOCK") >= 9 * (4 * scrapBuy + 4 * goldBuy));
            check("explicit crafted prices: emerald block stays not for sale, but can be sold", !rr.buy().containsKey("EMERALD_BLOCK")
                    && rr.sell().containsKey("EMERALD_BLOCK"));
            check("explicit crafted prices: emerald does not slide down through the block", rr.sell().get("EMERALD") == 400L
                    && rr.sell().get("EMERALD_BLOCK") == 3420L);
        }
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        if (failed > 0) System.exit(1);
    }
}
