package com.arka.smp.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a small hand-made table of base prices into a price for every craftable / smeltable item,
 * in a way that makes money loops impossible.
 *
 * Pure logic on plain item ids (strings) so it can be tested without a server.
 *
 * Rules (all prices in paise, 1 rupee = 100):
 *  - Base prices are fixed and never lowered by recipes.
 *  - A derived item is priced in ROUNDS. In round N an item may only use ingredients that were priced in
 *    an EARLIER round. That one-way order is what stops "compress then un-compress" cycles from creeping
 *    prices down or up.
 *  - Crafted: floor(cost of cheapest ingredients x craftFactor / amount). Smelted/cooked: x smeltFactor.
 *  - An ingredient that comes back after crafting (bucket, bottle) only costs its price minus the leftover's price.
 *  - Among several recipes the cheapest result wins. Anything that rounds to 0 paise stays unpriced.
 *  - An item is not buyable if it, or any ingredient it was priced from, is on the no-buy list.
 */
final class PriceEngine {
    private PriceEngine() {
    }

    /** One recipe: out x amount made from slots (each slot = the items that may fill it). */
    record Recipe(String out, int amount, List<List<String>> slots, boolean smelt) {
    }

    record Rules(double buyMultiplier, double craftFactor, double smeltFactor) {
    }

    record Result(Map<String, Long> priced, Map<String, Long> sell, Map<String, Long> buy,
                  Map<String, Integer> depth, List<String> notes) {
    }

    static Pattern glob(String g) {
        StringBuilder sb = new StringBuilder();
        for (char c : g.toCharArray()) {
            if (c == '*') sb.append(".*");
            else sb.append(Pattern.quote(String.valueOf(c)));
        }
        return Pattern.compile(sb.toString());
    }

    /** All items matched by exact names or * patterns. */
    static Set<String> expand(Collection<String> patterns, Collection<String> items) {
        Set<String> out = new HashSet<>();
        for (String p : patterns) {
            if (p.indexOf('*') < 0) {
                if (items.contains(p)) out.add(p);
            } else {
                Pattern r = glob(p);
                for (String i : items) if (r.matcher(i).matches()) out.add(i);
            }
        }
        return out;
    }

    static Result compute(Collection<String> items, Map<String, Double> baseRupees, Collection<String> noSell,
                          Collection<String> noBuy, List<Recipe> recipes, Map<String, String> remainders, Rules rules) {
        List<String> notes = new ArrayList<>();
        Map<String, Long> priced = new HashMap<>();
        Map<String, Integer> depth = new HashMap<>();

        // 1. base prices: patterns first, exact names override
        for (var e : baseRupees.entrySet()) {
            if (e.getKey().indexOf('*') < 0) continue;
            for (String item : expand(List.of(e.getKey()), items)) priced.put(item, toPaise(e.getValue()));
        }
        for (var e : baseRupees.entrySet()) {
            if (e.getKey().indexOf('*') >= 0) continue;
            if (!items.contains(e.getKey())) {
                notes.add("base price for unknown item ignored: " + e.getKey());
                continue;
            }
            priced.put(e.getKey(), toPaise(e.getValue()));
        }
        priced.values().removeIf(v -> v <= 0);
        for (String k : priced.keySet()) depth.put(k, 0);

        // 2. derive in rounds
        Map<String, List<String>> via = new HashMap<>();   // item -> the ingredients its price came from
        for (int round = 1; round <= 200; round++) {
            Map<String, Long> found = new HashMap<>();
            Map<String, List<String>> foundVia = new HashMap<>();
            for (Recipe r : recipes) {
                if (priced.containsKey(r.out()) || r.amount() <= 0) continue;
                long cost = 0;
                boolean ok = true;
                List<String> used = new ArrayList<>();
                for (List<String> slot : r.slots()) {
                    String best = null;
                    long bestCost = Long.MAX_VALUE;
                    for (String choice : slot) {
                        Long p = priced.get(choice);
                        if (p == null) continue;
                        String left = remainders.get(choice);
                        long net = p - (left == null ? 0 : priced.getOrDefault(left, 0L));
                        if (net < 0) net = 0;
                        if (net < bestCost) {
                            bestCost = net;
                            best = choice;
                        }
                    }
                    if (best == null) {
                        ok = false;
                        break;
                    }
                    cost += bestCost;
                    used.add(best);
                }
                if (!ok) continue;
                double factor = r.smelt() ? rules.smeltFactor() : rules.craftFactor();
                long candidate = (long) Math.floor(cost * factor / r.amount());
                if (candidate < 1) continue;
                Long cur = found.get(r.out());
                if (cur == null || candidate < cur) {
                    found.put(r.out(), candidate);
                    foundVia.put(r.out(), used);
                }
            }
            if (found.isEmpty()) break;
            for (var e : found.entrySet()) {
                priced.put(e.getKey(), e.getValue());
                depth.put(e.getKey(), round);
                via.put(e.getKey(), foundVia.get(e.getKey()));
            }
        }

        // 3. what may be sold / bought
        Set<String> noSellSet = expand(noSell, items);
        Set<String> blocked = new HashSet<>(expand(noBuy, items));
        List<String> order = new ArrayList<>(via.keySet());
        order.sort((a, b) -> Integer.compare(depth.get(a), depth.get(b)));
        for (String item : order) {                       // inherit no-buy through the ingredients
            for (String ing : via.get(item)) {
                if (blocked.contains(ing)) {
                    blocked.add(item);
                    break;
                }
            }
        }
        Map<String, Long> sell = new LinkedHashMap<>();
        Map<String, Long> buy = new LinkedHashMap<>();
        List<String> sorted = new ArrayList<>(priced.keySet());
        sorted.sort(String::compareTo);
        for (String item : sorted) {
            long p = priced.get(item);
            if (!noSellSet.contains(item)) sell.put(item, p);
            if (!blocked.contains(item)) buy.put(item, (long) Math.ceil(p * rules.buyMultiplier()));
        }
        return new Result(priced, sell, buy, depth, notes);
    }

    static long toPaise(double rupees) {
        return Math.round(rupees * 100.0);
    }
}
