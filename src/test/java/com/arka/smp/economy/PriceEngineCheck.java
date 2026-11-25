package com.arka.smp.economy;

import java.util.List;
import java.util.Map;

/** Plain checks (no test framework): run with  java -cp target/classes:target/test-classes com.arka.smp.economy.PriceEngineCheck */
public final class PriceEngineCheck {
    static int failed = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (!ok) failed++;
    }

    static PriceEngine.Recipe craft(String out, int n, String... slots) {
        return new PriceEngine.Recipe(out, n, java.util.Arrays.stream(slots).map(s -> List.of(s.split("\\|"))).toList(), false);
    }

    static PriceEngine.Recipe smelt(String out, String in) {
        return new PriceEngine.Recipe(out, 1, List.of(List.of(in)), true);
    }

    public static void main(String[] args) {
        List<String> items = List.of("IRON_INGOT", "IRON_BLOCK", "RAW_IRON", "BONE", "BONE_MEAL", "BONE_BLOCK",
                "BUCKET", "MILK_BUCKET", "CAKE", "SUGAR", "SUGAR_CANE", "NETHERITE_SCRAP", "GOLD_INGOT",
                "NETHERITE_INGOT", "NETHERITE_BLOCK", "OAK_LOG", "OAK_PLANKS", "STICK", "DIAMOND", "DIAMOND_PICKAXE",
                "COPPER_INGOT", "COPPER_BLOCK", "CUT_COPPER", "DIRT", "TNT");
        Map<String, Double> base = new java.util.LinkedHashMap<>();
        base.put("IRON_INGOT", 2.0);
        base.put("RAW_IRON", 2.0);
        base.put("BONE", 0.40);
        base.put("MILK_BUCKET", 6.0);
        base.put("SUGAR_CANE", 0.15);
        base.put("NETHERITE_SCRAP", 100.0);
        base.put("GOLD_INGOT", 4.0);
        base.put("*_LOG", 0.40);
        base.put("DIAMOND", 30.0);
        base.put("COPPER_INGOT", 0.40);
        List<PriceEngine.Recipe> recipes = List.of(
                craft("IRON_BLOCK", 1, "IRON_INGOT", "IRON_INGOT", "IRON_INGOT", "IRON_INGOT", "IRON_INGOT", "IRON_INGOT", "IRON_INGOT", "IRON_INGOT", "IRON_INGOT"),
                craft("IRON_INGOT", 9, "IRON_BLOCK"),                       // un-compress: must never change a base price
                smelt("IRON_INGOT", "RAW_IRON"),
                craft("BONE_MEAL", 3, "BONE"),
                craft("BONE_BLOCK", 1, "BONE_MEAL", "BONE_MEAL", "BONE_MEAL", "BONE_MEAL", "BONE_MEAL", "BONE_MEAL", "BONE_MEAL", "BONE_MEAL", "BONE_MEAL"),
                craft("BONE_MEAL", 9, "BONE_BLOCK"),
                craft("BUCKET", 1, "IRON_INGOT", "IRON_INGOT", "IRON_INGOT"),
                craft("SUGAR", 1, "SUGAR_CANE"),
                craft("CAKE", 1, "MILK_BUCKET", "MILK_BUCKET", "MILK_BUCKET", "SUGAR", "SUGAR", "BONE_MEAL"),
                craft("NETHERITE_INGOT", 1, "NETHERITE_SCRAP", "NETHERITE_SCRAP", "NETHERITE_SCRAP", "NETHERITE_SCRAP", "GOLD_INGOT", "GOLD_INGOT", "GOLD_INGOT", "GOLD_INGOT"),
                craft("NETHERITE_BLOCK", 1, "NETHERITE_INGOT", "NETHERITE_INGOT", "NETHERITE_INGOT", "NETHERITE_INGOT", "NETHERITE_INGOT", "NETHERITE_INGOT", "NETHERITE_INGOT", "NETHERITE_INGOT", "NETHERITE_INGOT"),
                craft("OAK_PLANKS", 4, "OAK_LOG"),
                craft("STICK", 4, "OAK_PLANKS", "OAK_PLANKS"),
                craft("DIAMOND_PICKAXE", 1, "DIAMOND", "DIAMOND", "DIAMOND", "STICK", "STICK"),
                craft("COPPER_BLOCK", 1, "COPPER_INGOT", "COPPER_INGOT", "COPPER_INGOT", "COPPER_INGOT", "COPPER_INGOT", "COPPER_INGOT", "COPPER_INGOT", "COPPER_INGOT", "COPPER_INGOT"),
                craft("CUT_COPPER", 4, "COPPER_BLOCK", "COPPER_BLOCK", "COPPER_BLOCK", "COPPER_BLOCK"),
                craft("COPPER_INGOT", 4, "CUT_COPPER"));                    // a loop through a base item
        PriceEngine.Rules rules = new PriceEngine.Rules(3.0, 0.95, 1.0);
        var r = PriceEngine.compute(items, base, List.of("MILK_BUCKET", "DIRT"), List.of("NETHERITE_SCRAP"), recipes,
                Map.of("MILK_BUCKET", "BUCKET"), rules);

        check("base price is fixed (iron ingot stays 200 paise)", r.priced().get("IRON_INGOT") == 200);
        check("iron block = 9 x 200 x 0.95 = 1710", r.priced().get("IRON_BLOCK") == 1710);
        check("un-compress recipe cannot change anything", r.priced().get("IRON_INGOT") == 200);
        check("bone meal = floor(40 x 0.95 / 3) = 12", r.priced().get("BONE_MEAL") == 12);
        check("bone block = 9 x 12 x 0.95 = 102 (cycle did not drift)", r.priced().get("BONE_BLOCK") == 102);
        check("bone meal stays 12 despite the block recipe", r.priced().get("BONE_MEAL") == 12);
        long bucket = r.priced().get("BUCKET");
        check("bucket = 3 x 200 x 0.95 = 570", bucket == 570);
        // milk costs 600 but returns a 570 bucket: net 30 each
        long cake = r.priced().get("CAKE");
        long expected = (long) Math.floor((3 * 30 + 2 * r.priced().get("SUGAR") + 12) * 0.95);
        check("cake counts the bucket it gives back (" + cake + " = " + expected + ")", cake == expected);
        check("cake is far cheaper than 3 full milk buckets (no bucket profit)", cake < 300);
        check("milk bucket is priced for recipes but never sold", !r.sell().containsKey("MILK_BUCKET"));
        check("milk bucket can be bought at 3x", r.buy().get("MILK_BUCKET") == 1800);
        check("dirt is not sold", !r.sell().containsKey("DIRT") && !r.buy().containsKey("DIRT"));
        check("unpriced item stays off (TNT has no recipe here)", !r.sell().containsKey("TNT"));
        check("netherite scrap cannot be bought", !r.buy().containsKey("NETHERITE_SCRAP"));
        check("netherite ingot inherits no-buy", !r.buy().containsKey("NETHERITE_INGOT"));
        check("netherite block inherits no-buy", !r.buy().containsKey("NETHERITE_BLOCK"));
        check("netherite ingot can still be sold", r.sell().containsKey("NETHERITE_INGOT"));
        check("block buy price = its 9 ingots bought one by one (5400), never cheaper", r.buy().get("IRON_BLOCK") == 5400);
        check("log pattern priced", r.priced().get("OAK_LOG") == 40);
        check("planks = floor(40 x 0.95 / 4) = 9", r.priced().get("OAK_PLANKS") == 9);
        check("stick = floor(2 x 9 x 0.95 / 4) = 4", r.priced().get("STICK") == 4);
        long pick = r.priced().get("DIAMOND_PICKAXE");
        check("diamond pickaxe = floor((3x3000 + 2x4) x 0.95) = " + pick, pick == (long) Math.floor((9000 + 8) * 0.95));
        check("smelt recipe cannot undercut base raw iron", r.priced().get("RAW_IRON") == 200);
        // loop through a base item: copper -> block -> cut copper -> ingot
        long cut = r.priced().get("CUT_COPPER");
        check("cut copper (1 block -> 1 cut) = floor(342 x 0.95) = 324 and below the block", cut == 324 && cut < r.priced().get("COPPER_BLOCK"));
        check("copper ingot stays at its base price", r.priced().get("COPPER_INGOT") == 40);

        // exhaustive loop check: for every recipe whose output was derived, selling the output never exceeds
        // buying... i.e. the sum of its ingredients' sell prices x craft factor (rounded down)
        boolean allOk = true;
        for (var rec : recipes) {
            Integer d = r.depth().get(rec.out());
            if (d == null || d == 0) continue;          // base prices are hand-set
            long cost = 0;
            boolean computable = true;
            int maxIngDepth = 0;
            for (var slot : rec.slots()) {
                long best = Long.MAX_VALUE;
                for (String c : slot) {
                    Long p = r.priced().get(c);
                    if (p != null) {
                        long net = Math.max(0, p - ("MILK_BUCKET".equals(c) ? bucket : 0));
                        best = Math.min(best, net);
                        maxIngDepth = Math.max(maxIngDepth, r.depth().get(c));
                    }
                }
                if (best == Long.MAX_VALUE) computable = false;
                else cost += best;
            }
            if (!computable || maxIngDepth >= d) continue;   // reverse (un-compress) recipes: allowed, see PRICING.md
            double limit = (rec.smelt() ? 1.0 : 0.95) * cost / rec.amount();
            if (r.priced().get(rec.out()) > limit + 1e-9) {
                allOk = false;
                System.out.println("  violation: " + rec.out());
            }
        }
        check("every forward recipe sells for no more than its ingredients x factor", allOk);
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        if (failed > 0) System.exit(1);
    }
}
