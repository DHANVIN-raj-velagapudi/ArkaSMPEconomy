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
 *  - "Premium" items carry their own sell price and a (much higher) explicit buy price; anything crafted from them
 *    can never be bought for less than its ingredients cost.
 *  - Finally an arbitrage check runs over EVERY recipe (including un-crafting, smelting, stonecutting, smithing and
 *    mining yields): an item's sell price is lowered until buying the cheapest way to get it and selling it can no
 *    longer make money, however many steps lie in between.
 */
final class PriceEngine {
    private PriceEngine() {
    }

    /** One recipe: out x amount made from slots (each slot = the items that may fill it). */
    record Recipe(String out, int amount, List<List<String>> slots, boolean smelt) {
    }

    record Rules(double buyMultiplier, double craftFactor, double smeltFactor) {
    }

    /** Hand-set sell price and explicit (high) buy price in rupees; buy <= 0 means "sell x multiplier". */
    record Premium(double sell, double buy) {
    }

    /** What mining or harvesting one block gives at best (e.g. an ore with Fortune III): in -> amount x out. */
    record Drop(String in, String out, double amount) {
    }

    private record Edge(String out, double amount, List<List<String>> slots) {
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
        return compute(items, baseRupees, Map.of(), noSell, noBuy, recipes, List.of(), remainders, rules);
    }

    static Result compute(Collection<String> items, Map<String, Double> baseRupees, Map<String, Premium> premium,
                          Collection<String> noSell, Collection<String> noBuy, List<Recipe> recipes, List<Drop> drops,
                          Map<String, String> remainders, Rules rules) {
        return compute(items, baseRupees, Map.of(), premium, noSell, noBuy, recipes, drops, remainders, rules);
    }

    /**
     * @param baseRupees    hand-set prices of raw items (exact names or * patterns)
     * @param craftedRupees fixed prices of items made from recipes; these items are never sold for less than they cost
     *                      to build and are not for sale when every way of making them needs something not for sale.
     *                      Items priced by neither list are derived from their recipes.
     */
    static Result compute(Collection<String> items, Map<String, Double> baseRupees, Map<String, Double> craftedRupees,
                          Map<String, Premium> premium, Collection<String> noSell, Collection<String> noBuy,
                          List<Recipe> recipes, List<Drop> drops, Map<String, String> remainders, Rules rules) {
        List<String> notes = new ArrayList<>();
        Map<String, Long> priced = new HashMap<>();
        Map<String, Integer> depth = new HashMap<>();
        Set<String> craftedSet = new HashSet<>();

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
        for (var e : craftedRupees.entrySet()) {
            if (!items.contains(e.getKey())) {
                notes.add("base price for unknown item ignored: " + e.getKey());
                continue;
            }
            priced.put(e.getKey(), toPaise(e.getValue()));
            craftedSet.add(e.getKey());
        }
        Map<String, Long> premiumBuy = new HashMap<>();
        for (int pass = 0; pass < 2; pass++) {               // patterns first, exact names override
            for (var e : premium.entrySet()) {
                boolean isPattern = e.getKey().indexOf('*') >= 0;
                if (isPattern != (pass == 0)) continue;
                Set<String> hit = expand(List.of(e.getKey()), items);
                if (hit.isEmpty() && !isPattern) notes.add("premium price for unknown item ignored: " + e.getKey());
                for (String item : hit) {
                    craftedSet.remove(item);                  // a premium price is a hand-set price
                    if (e.getValue().sell() > 0) priced.put(item, toPaise(e.getValue().sell()));
                    if (e.getValue().buy() > 0) premiumBuy.put(item, toPaise(e.getValue().buy()));
                }
            }
        }
        priced.values().removeIf(v -> v <= 0);
        for (String k : priced.keySet()) depth.put(k, 0);

        // 2. derive in rounds
        for (int round = 1; round <= 200; round++) {
            Map<String, Long> found = new HashMap<>();
            for (Recipe r : recipes) {
                if (priced.containsKey(r.out()) || r.amount() <= 0) continue;
                long cost = 0;
                boolean ok = true;
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
                }
                if (!ok) continue;
                double factor = r.smelt() ? rules.smeltFactor() : rules.craftFactor();
                long candidate = (long) Math.floor(cost * factor / r.amount());
                if (candidate < 1) continue;
                Long cur = found.get(r.out());
                if (cur == null || candidate < cur) {
                    found.put(r.out(), candidate);
                }
            }
            if (found.isEmpty()) break;
            for (var e : found.entrySet()) {
                priced.put(e.getKey(), e.getValue());
                craftedSet.add(e.getKey());
                depth.put(e.getKey(), round);
            }
        }

        // 3. what may be sold / bought
        Set<String> noSellSet = expand(noSell, items);
        Set<String> blocked = new HashSet<>(expand(noBuy, items));
        blocked.removeAll(premiumBuy.keySet());           // an explicit premium buy price beats the no-buy list
        Map<String, List<Recipe>> making = new HashMap<>();
        for (Recipe r : recipes) if (r.amount() > 0) making.computeIfAbsent(r.out(), k -> new ArrayList<>()).add(r);
        // a crafted item is not for sale when EVERY way of making it needs something that is not for sale
        for (int pass = 0; pass < 200; pass++) {
            boolean changed = false;
            for (var e : making.entrySet()) {
                String item = e.getKey();
                if (blocked.contains(item) || premiumBuy.containsKey(item) || !craftedSet.contains(item)) continue;
                boolean everyWayBlocked = true;
                for (Recipe r : e.getValue()) {
                    boolean dead = false;
                    for (List<String> slot : r.slots()) {
                        boolean allBlocked = true;
                        for (String c : slot) {
                            if (!blocked.contains(c)) {
                                allBlocked = false;
                                break;
                            }
                        }
                        if (allBlocked) {
                            dead = true;
                            break;
                        }
                    }
                    if (!dead) {
                        everyWayBlocked = false;
                        break;
                    }
                }
                if (everyWayBlocked) {
                    blocked.add(item);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        Map<String, Long> sellMap = new HashMap<>();
        Map<String, Long> buyMap = new HashMap<>();
        List<String> byDepth = new ArrayList<>(priced.keySet());
        byDepth.sort((a, b) -> {
            int c = Integer.compare(depth.getOrDefault(a, 0), depth.getOrDefault(b, 0));
            return c != 0 ? c : a.compareTo(b);
        });
        for (String item : byDepth) {
            long p = priced.get(item);
            if (!noSellSet.contains(item)) sellMap.put(item, p);
            if (blocked.contains(item)) continue;
            long b = (long) Math.ceil(p * rules.buyMultiplier());
            Long pb = premiumBuy.get(item);
            if (pb != null) b = Math.max(b, pb);
            buyMap.put(item, b);
        }
        // never cheaper to buy than to build: a crafted item costs at least what its cheapest recipe costs when
        // everything is bought from the raw (hand-priced) items. The cost is worked out from the raw items upwards,
        // so un-crafting recipes (block back to ingots) cannot be used to make a rare item look cheap.
        Map<String, Double> cost = new HashMap<>();
        for (int pass = 0; pass < 200; pass++) {
            boolean changed = false;
            for (var e : making.entrySet()) {
                String item = e.getKey();
                if (!craftedSet.contains(item) || blocked.contains(item)) continue;
                for (Recipe r : e.getValue()) {
                    double sum = 0;
                    boolean ok = true;
                    for (List<String> slot : r.slots()) {
                        double best = Double.MAX_VALUE;
                        for (String c : slot) {
                            double v;
                            if (craftedSet.contains(c)) {
                                Double cv = blocked.contains(c) ? null : cost.get(c);
                                if (cv == null) continue;
                                v = cv;
                            } else {
                                Long bv = buyMap.get(c);
                                if (bv == null) continue;
                                v = bv;
                            }
                            String left = remainders.get(c);
                            if (left != null) {
                                double lv = craftedSet.contains(left) ? cost.getOrDefault(left, 0.0) : buyMap.getOrDefault(left, 0L);
                                v = Math.max(0, v - lv);
                            }
                            if (v < best) best = v;
                        }
                        if (best == Double.MAX_VALUE) {
                            ok = false;
                            break;
                        }
                        sum += best;
                    }
                    if (!ok) continue;
                    double per = sum / r.amount();
                    Double cur = cost.get(item);
                    if (cur == null || per < cur - 1e-9) {
                        cost.put(item, per);
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        for (var e : cost.entrySet()) {
            Long cur = buyMap.get(e.getKey());
            if (cur == null) continue;
            long floor = (long) Math.ceil(e.getValue() - 1e-9);
            if (floor > cur) buyMap.put(e.getKey(), floor);
        }

        // 4. arbitrage check over every recipe, smelt, cut, smithing step and mining yield
        List<Edge> edges = new ArrayList<>();
        for (Recipe r : recipes) if (r.amount() > 0) edges.add(new Edge(r.out(), r.amount(), r.slots()));
        for (Drop d : drops) if (d.amount() > 0) edges.add(new Edge(d.out(), d.amount(), List.of(List.of(d.in()))));
        int lowered = 0;
        List<String> loweredNotes = new ArrayList<>();
        for (int outer = 0; outer < 60; outer++) {
            Map<String, Double> acq = new HashMap<>();
            for (var e : buyMap.entrySet()) acq.put(e.getKey(), (double) e.getValue());
            boolean changed = true;
            for (int pass = 0; changed && pass < 400; pass++) {
                changed = false;
                for (Edge e : edges) {
                    double total = 0;
                    boolean ok = true;
                    for (List<String> slot : e.slots()) {
                        double best = Double.MAX_VALUE;
                        for (String c : slot) {
                            // only things that can really be bought (or made from bought things) count as a way in:
                            // an item that cannot be bought has no purchase price, so it cannot start a buy-process-sell loop
                            Double v = acq.get(c);
                            if (v == null) continue;
                            String left = remainders.get(c);
                            if (left != null) {
                                Double lv = acq.get(left);
                                if (lv == null) lv = 0.0;
                                v = Math.max(0, v - lv);
                            }
                            if (v < best) best = v;
                        }
                        if (best == Double.MAX_VALUE) {
                            ok = false;
                            break;
                        }
                        total += best;
                    }
                    if (!ok) continue;
                    double per = total / e.amount();
                    Double cur = acq.get(e.out());
                    if (cur == null || per < cur - 1e-9) {
                        acq.put(e.out(), per);
                        changed = true;
                    }
                }
            }
            boolean clamped = false;
            for (String item : new ArrayList<>(sellMap.keySet())) {
                Double a = acq.get(item);
                if (a == null) continue;
                long limit = (long) Math.floor(a * rules.craftFactor());
                long now = sellMap.get(item);
                if (now > limit) {
                    if (limit < 1) sellMap.remove(item);
                    else sellMap.put(item, limit);
                    lowered++;
                    if (loweredNotes.size() < 30) {
                        loweredNotes.add(item + " " + now + "->" + Math.max(limit, 0));
                    }
                    clamped = true;
                }
            }
            if (!clamped) break;
        }
        if (lowered > 0) {
            notes.add("sell price lowered for " + lowered + " item(s) so that buying and crafting can never beat selling (paise): "
                    + String.join(", ", loweredNotes.subList(0, Math.min(10, loweredNotes.size())))
                    + (loweredNotes.size() > 10 ? ", ..." : ""));
        }

        Map<String, Long> sell = new LinkedHashMap<>();
        Map<String, Long> buy = new LinkedHashMap<>();
        List<String> sorted = new ArrayList<>(priced.keySet());
        sorted.sort(String::compareTo);
        for (String item : sorted) {
            if (sellMap.containsKey(item)) sell.put(item, sellMap.get(item));
            if (buyMap.containsKey(item)) buy.put(item, buyMap.get(item));
        }
        return new Result(priced, sell, buy, depth, notes);
    }

    static long toPaise(double rupees) {
        return Math.round(rupees * 100.0);
    }
}
