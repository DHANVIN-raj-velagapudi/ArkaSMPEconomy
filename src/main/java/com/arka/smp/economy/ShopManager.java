package com.arka.smp.economy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntPredicate;

/**
 * The store: prices, buying and selling.
 *
 * Prices come from {@link PriceEngine}: a small hand-made table (base-prices.yml) plus every recipe on the
 * server. Admin overrides (price-overrides.yml) are applied last.
 *
 * Everything here runs on the main thread, so a purchase or sale is atomic with respect to the inventory:
 * items are removed first, then money is paid; if the payment fails the items are put back.
 */
public final class ShopManager {
    public enum Buy { OK, NOT_FOR_SALE, NO_SPACE, INSUFFICIENT, FROZEN, ERROR }

    record Take(int slot, Material material, int qty, long paise) {
    }

    /** What a sale would do right now. */
    record Plan(List<Take> takes, long total, int items, Map<Material, Integer> byMaterial,
                boolean hourLimited, boolean dayLimited, int refused) {
    }

    private record Sale(long ts, Material material, long paise) {
    }

    private final ArkaSMPEconomy plugin;
    private final YamlStore overrides;
    private final Map<UUID, ArrayDeque<Sale>> sales = new HashMap<>();
    private volatile Map<Material, Long> sell = new EnumMap<>(Material.class);
    private volatile Map<Material, Long> buy = new EnumMap<>(Material.class);

    // rules (base-prices.yml -> rules:)
    private long hourCapPaise = 40_000;
    private long dayCapPaise = 1_200_000;
    private double minPlayHours = 2;
    private long bigSalePaise = 200_000;
    private int overridesApplied;
    private int derived;
    private List<String> notes = List.of();
    private volatile Map<String, List<Material>> catalogCache = Map.of();

    ShopManager(ArkaSMPEconomy plugin, List<Database.Sale> recentSales) {
        this.plugin = plugin;
        this.overrides = new YamlStore(plugin, "price-overrides.yml");
        for (Database.Sale s : recentSales) {
            Material m = Material.matchMaterial(s.item());
            if (m != null) sales.computeIfAbsent(s.id(), k -> new ArrayDeque<>()).add(new Sale(s.ts(), m, s.paise()));
        }
        reload();
    }

    // ---- prices -----------------------------------------------------------------------------

    private static boolean isItem(Material m) {
        return !m.isLegacy() && m.isItem() && !m.isAir();
    }

    /** (Re)builds every price. Must run on the main thread (reads the server's recipes). */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "base-prices.yml");
        if (!file.exists()) plugin.saveResource("base-prices.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        double buyMult = Math.max(1.0, y.getDouble("rules.buy-multiplier", 3.0));
        double craft = Math.min(1.0, Math.max(0.1, y.getDouble("rules.craft-factor", 0.95)));
        double smelt = Math.min(1.0, Math.max(0.1, y.getDouble("rules.smelt-factor", 1.0)));
        hourCapPaise = Money.SCALE * (long) Math.max(1, y.getDouble("rules.item-hour-cap-rupees", 400));
        dayCapPaise = Money.SCALE * (long) Math.max(1, y.getDouble("rules.daily-cap-rupees", 12000));
        minPlayHours = Math.max(0, y.getDouble("rules.min-play-hours", 2));
        bigSalePaise = Money.SCALE * (long) Math.max(1, y.getDouble("rules.big-sale-confirm", 2000));

        List<String> names = new ArrayList<>();
        Map<String, Material> byName = new HashMap<>();
        for (Material m : Material.values()) {
            if (isItem(m)) {
                names.add(m.name());
                byName.put(m.name(), m);
            }
        }
        Map<String, Double> base = new LinkedHashMap<>();
        var section = y.getConfigurationSection("base");
        if (section != null) {
            for (String k : section.getKeys(false)) base.put(k, section.getDouble(k));
        }
        List<PriceEngine.Recipe> recipes = readRecipes();
        Map<String, String> remainders = new HashMap<>();
        for (Material m : byName.values()) {
            Material left = m.getCraftingRemainingItem();
            if (left != null && isItem(left)) remainders.put(m.name(), left.name());
        }
        var result = PriceEngine.compute(names, base, y.getStringList("no-sell"), y.getStringList("no-buy"),
                recipes, remainders, new PriceEngine.Rules(buyMult, craft, smelt));

        Map<Material, Long> s = new EnumMap<>(Material.class);
        Map<Material, Long> b = new EnumMap<>(Material.class);
        result.sell().forEach((k, v) -> s.put(byName.get(k), v));
        result.buy().forEach((k, v) -> b.put(byName.get(k), v));
        derived = (int) result.depth().values().stream().filter(d -> d > 0).count();
        notes = result.notes();
        applyOverrides(s, b, buyMult);
        sell = s;
        buy = b;
        catalogCache = buildCatalog();
        plugin.getLogger().info("Shop prices ready: " + s.size() + " items can be sold, " + b.size()
                + " can be bought (" + derived + " derived from recipes, " + overridesApplied + " admin overrides).");
        for (String n : notes) plugin.getLogger().warning("Price table: " + n);
    }

    private List<PriceEngine.Recipe> readRecipes() {
        List<PriceEngine.Recipe> out = new ArrayList<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            try {
                Recipe r = it.next();
                ItemStack res = r.getResult();
                if (res == null || !isItem(res.getType()) || res.hasItemMeta() || res.getAmount() <= 0) continue;
                String name = res.getType().name();
                int amount = res.getAmount();
                List<List<String>> slots = new ArrayList<>();
                boolean smelt = false;
                if (r instanceof ShapedRecipe sr) {
                    Map<Character, RecipeChoice> map = sr.getChoiceMap();
                    for (String row : sr.getShape()) {
                        for (char c : row.toCharArray()) {
                            if (c == ' ') continue;
                            List<String> ch = names(map.get(c));
                            if (ch == null) {
                                slots = null;
                                break;
                            }
                            slots.add(ch);
                        }
                        if (slots == null) break;
                    }
                } else if (r instanceof ShapelessRecipe sl) {
                    for (RecipeChoice rc : sl.getChoiceList()) {
                        List<String> ch = names(rc);
                        if (ch == null) {
                            slots = null;
                            break;
                        }
                        slots.add(ch);
                    }
                } else if (r instanceof CookingRecipe<?> cr) {
                    List<String> ch = names(cr.getInputChoice());
                    if (ch == null) continue;
                    slots.add(ch);
                    smelt = true;
                } else if (r instanceof StonecuttingRecipe sc) {
                    List<String> ch = names(sc.getInputChoice());
                    if (ch == null) continue;
                    slots.add(ch);
                } else {
                    continue;   // smithing, transmute, special recipes: not priced from recipes
                }
                if (slots == null || slots.isEmpty()) continue;
                out.add(new PriceEngine.Recipe(name, amount, slots, smelt));
            } catch (RuntimeException ignored) {
                // a broken third-party recipe must never stop the shop from loading
            }
        }
        return out;
    }

    /** Material names a recipe slot accepts; null if the slot needs a special item (then the recipe is skipped). */
    private static List<String> names(RecipeChoice rc) {
        if (rc == null) return null;
        List<String> out = new ArrayList<>();
        if (rc instanceof RecipeChoice.MaterialChoice mc) {
            for (Material m : mc.getChoices()) if (isItem(m)) out.add(m.name());
        } else if (rc instanceof RecipeChoice.ExactChoice ec) {
            for (ItemStack it : ec.getChoices()) {
                if (it == null || it.hasItemMeta() || !isItem(it.getType())) return null;
                out.add(it.getType().name());
            }
        } else {
            return null;
        }
        return out.isEmpty() ? null : out;
    }

    private void applyOverrides(Map<Material, Long> s, Map<Material, Long> b, double buyMult) {
        overridesApplied = 0;
        var items = overrides.yaml().getConfigurationSection("items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            if (m == null || !isItem(m)) continue;
            var o = items.getConfigurationSection(key);
            if (o == null) continue;
            overridesApplied++;
            if (o.isSet("sell")) {
                if (o.isBoolean("sell") || o.getDouble("sell") <= 0) s.remove(m);
                else s.put(m, PriceEngine.toPaise(o.getDouble("sell")));
            }
            if (o.isSet("buy")) {
                if (o.isBoolean("buy") || o.getDouble("buy") <= 0) b.remove(m);
                else b.put(m, PriceEngine.toPaise(o.getDouble("buy")));
            } else if (o.isSet("sell") && s.containsKey(m)) {
                b.put(m, (long) Math.ceil(s.get(m) * buyMult));
            }
        }
    }

    // ---- admin overrides ----------------------------------------------------------------------

    /** sell/buy in rupees; null = leave, 0 = disable. */
    public void setOverride(Material m, Double sellRupees, Double buyRupees) {
        var y = overrides.yaml();
        String path = "items." + m.name();
        if (sellRupees != null) y.set(path + ".sell", sellRupees <= 0 ? false : sellRupees);
        if (buyRupees != null) y.set(path + ".buy", buyRupees <= 0 ? false : buyRupees);
        overrides.markDirty();
        reload();
    }

    public boolean clearOverride(Material m) {
        var y = overrides.yaml();
        boolean had = y.isSet("items." + m.name());
        y.set("items." + m.name(), null);
        overrides.markDirty();
        reload();
        return had;
    }

    public void shutdown() {
        overrides.saveNow();
    }

    // ---- lookups ------------------------------------------------------------------------------

    public long sellPrice(Material m) {
        return sell.getOrDefault(m, 0L);
    }

    public long buyPrice(Material m) {
        return buy.getOrDefault(m, 0L);
    }

    public Map<Material, Long> sellTable() {
        return sell;
    }

    public Map<Material, Long> buyTable() {
        return buy;
    }

    /** Sell value (paise) of this exact stack; 0 for anything that cannot be sold. Used by the trade warning. */
    public long valueOf(ItemStack item) {
        if (item == null || item.hasItemMeta()) return 0;
        return sellPrice(item.getType()) * item.getAmount();
    }

    public int derivedCount() {
        return derived;
    }

    public int overrideCount() {
        return overridesApplied;
    }

    public long bigSalePaise() {
        return bigSalePaise;
    }

    public static String pretty(Material m) {
        String[] parts = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    // ---- sell limits (rebuilt from the database, so restarting does not reset them) ------------

    private ArrayDeque<Sale> history(UUID id) {
        ArrayDeque<Sale> q = sales.computeIfAbsent(id, k -> new ArrayDeque<>());
        long cutoff = System.currentTimeMillis() - 86_400_000L;
        while (!q.isEmpty() && q.peekFirst().ts() < cutoff) q.pollFirst();
        return q;
    }

    private long soldDay(UUID id) {
        long sum = 0;
        for (Sale s : history(id)) sum += s.paise();
        return sum;
    }

    private long soldHour(UUID id, Material m) {
        long cutoff = System.currentTimeMillis() - 3_600_000L;
        long sum = 0;
        for (Sale s : history(id)) if (s.material() == m && s.ts() >= cutoff) sum += s.paise();
        return sum;
    }

    /** One pass over the last hour of sales: what each item already earned (used by plan()). */
    private Map<Material, Long> soldHourAll(UUID id) {
        long cutoff = System.currentTimeMillis() - 3_600_000L;
        Map<Material, Long> out = new EnumMap<>(Material.class);
        for (Sale s : history(id)) if (s.ts() >= cutoff) out.merge(s.material(), s.paise(), Long::sum);
        return out;
    }

    /** Most one item type can earn per hour: the configured cap, or twice the price for very rare items. */
    private long hourCap(long price) {
        return Math.max(hourCapPaise, price * 2);
    }

    public long dayRemaining(UUID id) {
        return Math.max(0, dayCapPaise - soldDay(id));
    }

    public long hourRemaining(UUID id, Material m) {
        return Math.max(0, hourCap(sellPrice(m)) - soldHour(id, m));
    }

    public double activeHours(Player p) {
        return plugin.core().players().playTicks(p.getUniqueId()) / 72000.0;
    }

    public double minPlayHours() {
        return minPlayHours;
    }

    // ---- selling ------------------------------------------------------------------------------

    private boolean sellable(ItemStack it) {
        return it != null && !it.hasItemMeta() && sell.containsKey(it.getType());
    }

    /**
     * Works out what could be sold right now from the main inventory (never armour or off-hand), within the
     * limits. slotFilter picks slots; only (when not null) restricts to one material; maxQty caps the total.
     */
    Plan plan(Player p, IntPredicate slotFilter, Material only, int maxQty) {
        return plan(p.getUniqueId(), p.getInventory().getStorageContents(), slotFilter, only, maxQty);
    }

    /** Same, for any item array (the sell chest uses this). */
    Plan plan(UUID id, ItemStack[] inv, IntPredicate slotFilter, Material only, int maxQty) {
        long dayLeft = dayRemaining(id);
        Map<Material, Long> hourLeft = new HashMap<>();
        Map<Material, Long> soldNow = soldHourAll(id);
        Map<Material, Integer> byMat = new LinkedHashMap<>();
        List<Take> takes = new ArrayList<>();
        long total = 0;
        int items = 0, refused = 0;
        boolean hourLimited = false, dayLimited = false;
        int left = maxQty;
        for (int slot = 0; slot < inv.length && left > 0; slot++) {
            ItemStack it = inv[slot];
            if (it == null || !slotFilter.test(slot)) continue;
            if (only != null && it.getType() != only) continue;
            if (!sellable(it)) {
                if (sell.containsKey(it.getType())) refused++;   // priced item with meta (enchanted, named, damaged...)
                continue;
            }
            Material m = it.getType();
            long price = sell.get(m);
            long h = hourLeft.computeIfAbsent(m, k -> Math.max(0, hourCap(sell.get(k)) - soldNow.getOrDefault(k, 0L)));
            long byHour = h / price;
            long byDay = dayLeft / price;
            long qty = Math.min(Math.min(it.getAmount(), left), Math.min(byHour, byDay));
            if (qty < Math.min(it.getAmount(), left)) {
                if (byHour <= byDay) hourLimited = true;
                else dayLimited = true;
            }
            if (qty <= 0) continue;
            takes.add(new Take(slot, m, (int) qty, qty * price));
            hourLeft.put(m, h - qty * price);
            dayLeft -= qty * price;
            total += qty * price;
            items += (int) qty;
            left -= (int) qty;
            byMat.merge(m, (int) qty, Integer::sum);
        }
        return new Plan(takes, total, items, byMat, hourLimited, dayLimited, refused);
    }

    /** Removes the items, pays the player, records the sale. Returns false (and changes nothing) on any problem. */
    boolean execute(Player p, Plan plan) {
        return execute(p, p.getInventory(), plan);
    }

    boolean execute(Player p, Inventory inv, Plan plan) {
        if (plan.total() <= 0 || plan.takes().isEmpty()) return false;
        List<ItemStack> backup = new ArrayList<>();
        for (Take t : plan.takes()) {                        // verify everything still matches before touching it
            ItemStack cur = inv.getItem(t.slot());
            if (cur == null || cur.getType() != t.material() || cur.hasItemMeta() || cur.getAmount() < t.qty()) return false;
            backup.add(cur.clone());
        }
        for (Take t : plan.takes()) {
            ItemStack cur = inv.getItem(t.slot());
            if (cur.getAmount() == t.qty()) inv.setItem(t.slot(), null);
            else {
                cur.setAmount(cur.getAmount() - t.qty());
                inv.setItem(t.slot(), cur);
            }
        }
        var res = plugin.economy().deposit(p.getUniqueId(), plan.total(), "shop-sell", describe(plan.byMaterial()), false);
        if (res != EconomyManager.Result.OK) {
            for (int i = 0; i < plan.takes().size(); i++) inv.setItem(plan.takes().get(i).slot(), backup.get(i));   // put it all back
            return false;
        }
        long now = System.currentTimeMillis();
        var q = history(p.getUniqueId());
        Map<Material, Long> perMat = new LinkedHashMap<>();
        for (Take t : plan.takes()) perMat.merge(t.material(), t.paise(), Long::sum);
        perMat.forEach((m, paise) -> {
            q.addLast(new Sale(now, m, paise));
            plugin.db().logSale(p.getUniqueId(), m.name(), now, paise);
        });
        p.updateInventory();
        return true;
    }

    /** Why a sale cannot start at all, as a message key, or null. */
    public String sellBlocker(Player p) {
        if (!plugin.getConfig().getBoolean("shop.enabled", true)) return "shop-disabled";
        var acc = plugin.economy().get(p.getUniqueId());
        if (acc == null || acc.frozen) return "pay-frozen";
        if (activeHours(p) < minPlayHours && !p.hasPermission("arkaeco.shop.bypass")) return "shop-no-playhours";
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return "shop-creative";
        return null;
    }

    static String describe(Map<Material, Integer> byMaterial) {
        StringBuilder sb = new StringBuilder();
        for (var e : byMaterial.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getValue()).append('x').append(e.getKey().name());
            if (sb.length() > 400) break;
        }
        return sb.toString();
    }

    // ---- buying -------------------------------------------------------------------------------

    private boolean fits(Player p, Material m, int qty) {
        Inventory tmp = Bukkit.createInventory(null, 36);
        tmp.setContents(p.getInventory().getStorageContents());
        return tmp.addItem(new ItemStack(m, qty)).isEmpty();
    }

    public int maxBuy() {
        return Math.max(1, Math.min(2304, plugin.getConfig().getInt("shop.max-buy", 2304)));
    }

    public long costOf(Material m, int qty) {
        long price = buyPrice(m);
        try {
            return Math.multiplyExact(price, (long) qty);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    public Buy buy(Player p, Material m, int qty) {
        if (!plugin.getConfig().getBoolean("shop.enabled", true)) return Buy.ERROR;
        if (!buy.containsKey(m)) return Buy.NOT_FOR_SALE;
        if (qty < 1 || qty > maxBuy()) return Buy.ERROR;
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return Buy.ERROR;
        if (!fits(p, m, qty)) return Buy.NO_SPACE;
        long cost = costOf(m, qty);
        if (cost <= 0 || cost > Money.MAX) return Buy.ERROR;
        var res = plugin.economy().withdraw(p.getUniqueId(), cost, "shop-buy", qty + "x" + m.name(), false);
        switch (res) {
            case OK -> {
            }
            case INSUFFICIENT -> {
                return Buy.INSUFFICIENT;
            }
            case FROZEN -> {
                return Buy.FROZEN;
            }
            default -> {
                return Buy.ERROR;
            }
        }
        for (ItemStack left : p.getInventory().addItem(new ItemStack(m, qty)).values()) {   // cannot happen: space was checked
            p.getWorld().dropItem(p.getLocation(), left);
        }
        p.updateInventory();
        return Buy.OK;
    }

    // ---- category listing (for the menus) ------------------------------------------------------

    /** Items that can be bought or sold, grouped by category id, each list sorted by name. */
    public Map<String, List<Material>> catalog() {
        return catalogCache;
    }

    private Map<String, List<Material>> buildCatalog() {
        Map<String, List<Material>> out = new LinkedHashMap<>();
        java.util.Set<Material> all = new java.util.TreeSet<>(Comparator.comparing(Material::name));
        all.addAll(sell.keySet());
        all.addAll(buy.keySet());
        for (Material m : all) out.computeIfAbsent(ShopCategories.of(m), k -> new ArrayList<>()).add(m);
        return out;
    }
}
