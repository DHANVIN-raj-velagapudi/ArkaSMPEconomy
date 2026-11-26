package com.arka.smp.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** args: base-prices.yml items.txt recipes.tsv outDir [edible.txt]  -> runs the real engine on the real 26.3 recipes. */
public class GenPrices {
    public static void main(String[] a) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.load(new File(a[0]));
        List<String> items = Files.readAllLines(Path.of(a[1]));
        items.removeIf(String::isBlank);
        List<PriceEngine.Recipe> recipes = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(a[2]))) {
            if (line.isBlank()) continue;
            String[] p = line.split("\t");
            List<List<String>> slots = new ArrayList<>();
            for (String s : p[3].split(",")) slots.add(List.of(s.split("\\|")));
            recipes.add(new PriceEngine.Recipe(p[0], Integer.parseInt(p[1]), slots, p[2].equals("s")));
        }
        Map<String, Double> base = new LinkedHashMap<>();
        ConfigurationSection bs = y.getConfigurationSection("base");
        if (bs != null) for (String k : bs.getKeys(false)) base.put(k, bs.getDouble(k));
        Map<String, Double> crafted = new LinkedHashMap<>();
        ConfigurationSection cs = y.getConfigurationSection("crafted");
        if (cs != null) for (String k : cs.getKeys(false)) crafted.put(k, cs.getDouble(k));
        Map<String, PriceEngine.Premium> prem = new LinkedHashMap<>();
        ConfigurationSection ps = y.getConfigurationSection("premium");
        if (ps != null) for (String k : ps.getKeys(false))
            prem.put(k, new PriceEngine.Premium(ps.getDouble(k + ".sell"), ps.getDouble(k + ".buy")));
        List<PriceEngine.Drop> drops = new ArrayList<>();
        ConfigurationSection ds = y.getConfigurationSection("drops");
        if (ds != null) for (String in : ds.getKeys(false)) {
            ConfigurationSection o = ds.getConfigurationSection(in);
            for (String out : o.getKeys(false)) drops.add(new PriceEngine.Drop(in, out, o.getDouble(out)));
        }
        Map<String, String> rem = new HashMap<>();
        for (String it : items) {
            if (it.endsWith("_BUCKET") && !it.equals("BUCKET")) rem.put(it, "BUCKET");
        }
        rem.put("HONEY_BOTTLE", "GLASS_BOTTLE");
        double bm = y.getDouble("rules.buy-multiplier", 3.0);
        var r = PriceEngine.compute(items, base, crafted, prem, y.getStringList("no-sell"), y.getStringList("no-buy"), recipes, drops, rem,
                new PriceEngine.Rules(bm, y.getDouble("rules.craft-factor", 0.95), y.getDouble("rules.smelt-factor", 1.0)));
        System.out.println("items " + items.size() + ", recipes " + recipes.size() + ", sellable " + r.sell().size() + ", buyable " + r.buy().size());
        for (String n : r.notes()) System.out.println("NOTE " + n);
        Files.writeString(Path.of(a[3], "notes.txt"), String.join(System.lineSeparator(), r.notes()));

        StringBuilder cl = new StringBuilder();
        for (String it : items) {
            Double bp = base.get(it);
            if (bp == null) for (var e : base.entrySet()) if (e.getKey().contains("*") && it.matches(e.getKey().replace("*", ".*"))) bp = e.getValue();
            Long s2 = r.sell().get(it);
            if (bp != null && !prem.containsKey(it) && s2 != null && s2 < PriceEngine.toPaise(bp))
                cl.append(it).append(' ').append(PriceEngine.toPaise(bp)).append("->").append(s2).append(System.lineSeparator());
        }
        Files.writeString(Path.of(a[3], "clamped.txt"), cl.toString());
        List<String> un = new ArrayList<>();
        for (String it : items) if (!r.sell().containsKey(it) && !r.buy().containsKey(it)) un.add(it);
        Files.writeString(Path.of(a[3], "unpriced.txt"), String.join("\n", un) + "\n");
        System.out.println("unpriced: " + un.size());
        StringBuilder sb = new StringBuilder();
        for (String it : items) {
            Long s = r.sell().get(it), b = r.buy().get(it);
            sb.append(it).append('\t').append(s == null ? "" : s).append('\t').append(b == null ? "" : b)
                    .append('\t').append(r.depth().getOrDefault(it, -1)).append('\n');
        }
        Files.writeString(Path.of(a[3], "prices.tsv"), sb.toString());
        StringBuilder pb = new StringBuilder();
        for (var e : r.priced().entrySet()) pb.append(e.getKey()).append('\t').append(e.getValue()).append(System.lineSeparator());
        Files.writeString(Path.of(a[3], "priced.tsv"), pb.toString());
        // the shop category of every item (the same rules the shop menu uses), for make_full.py and the price list
        Set<String> edible = new HashSet<>();
        if (a.length > 4) for (String l : Files.readAllLines(Path.of(a[4]))) if (!l.isBlank()) edible.add(l.trim());
        ShopCategories.learnWoods(items);
        StringBuilder cb = new StringBuilder();
        for (String it : items) cb.append(it).append((char) 9).append(ShopCategories.of(it, edible.contains(it))).append((char) 10);
        Files.writeString(Path.of(a[3], "cats.tsv"), cb.toString());
    }
}
