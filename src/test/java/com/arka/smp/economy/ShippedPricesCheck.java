package com.arka.smp.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks the shipped base-prices.yml for the rules the server owner asked for (no server needed).
 * Run from the project folder: java -cp target/classes;target/test-classes;... com.arka.smp.economy.ShippedPricesCheck
 */
public final class ShippedPricesCheck {
    static int failed = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (!ok) failed++;
    }

    static boolean matches(List<String> patterns, String name) {
        for (String p : patterns) {
            if (Pattern.matches(p.replace("*", ".*"), name)) return true;
        }
        return false;
    }

    static double buy(ConfigurationSection prem, String name) {
        return prem.getDouble(name + ".buy", 0);
    }

    public static void main(String[] args) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File("src/main/resources/base-prices.yml"));
        ConfigurationSection prem = y.getConfigurationSection("premium");
        List<String> noBuy = y.getStringList("no-buy");
        List<String> noSell = y.getStringList("no-sell");
        check("premium section exists", prem != null);
        if (prem == null) System.exit(1);

        // 1. tools: every tier costs more than the one below, for every tool type
        String[] tiers = {"WOODEN", "STONE", "COPPER", "IRON", "GOLDEN", "DIAMOND"};
        String[] types = {"PICKAXE", "AXE", "HOE", "SWORD", "SHOVEL", "SPEAR"};
        boolean ladder = true;
        for (String t : types) {
            for (int i = 0; i < tiers.length; i++) {
                double b = buy(prem, tiers[i] + "_" + t);
                if (b <= 0) ladder = false;
                if (i > 0 && b <= buy(prem, tiers[i - 1] + "_" + t)) ladder = false;
            }
        }
        check("tool buy prices rise with every tier, wood to diamond", ladder);
        check("a diamond pickaxe costs at least 1,000 and a diamond shovel less than a pickaxe",
                buy(prem, "DIAMOND_PICKAXE") >= 1000 && buy(prem, "DIAMOND_SHOVEL") < buy(prem, "DIAMOND_PICKAXE"));
        check("netherite scrap and ancient debris cost the same (so no cheaper way into netherite)",
                buy(prem, "NETHERITE_SCRAP") == buy(prem, "ANCIENT_DEBRIS") && buy(prem, "NETHERITE_SCRAP") >= 20000);
        check("a netherite ingot (4 scrap) costs far more than any diamond tool",
                4 * buy(prem, "NETHERITE_SCRAP") > 10 * buy(prem, "DIAMOND_PICKAXE"));

        // 2. rare items: sellable, not buyable
        List<String> rare = new ArrayList<>(List.of("TOTEM_OF_UNDYING", "ELYTRA", "DRAGON_HEAD", "DRAGON_EGG", "END_CRYSTAL",
                "HEAVY_CORE", "TRIDENT", "BOLT_ARMOR_TRIM_SMITHING_TEMPLATE", "SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE",
                "SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE"));
        boolean notBuyable = true, sellable = true, hasPrice = true;
        for (String n : rare) {
            if (!matches(noBuy, n)) notBuyable = false;
            if (matches(noSell, n)) sellable = false;
            double sell = prem.getDouble(n + ".sell", 0);
            if (!prem.contains(n) && !matches(new ArrayList<>(prem.getKeys(false)), n)) hasPrice = false;
            if (prem.contains(n) && buy(prem, n) > 0) notBuyable = false;      // an explicit buy price would beat no-buy
            if (prem.contains(n) && sell <= 0) hasPrice = false;
        }
        check("totem, elytra, dragon head and egg, end crystal, heavy core, trident and trims are in no-buy", notBuyable);
        check("those items are not in no-sell (they can be sold)", sellable);
        check("those items have a sell price", hasPrice);
        check("the mace and the ghast tear (end crystals) cannot be bought", matches(noBuy, "MACE") && matches(noBuy, "GHAST_TEAR"));
        check("no generic *_SMITHING_TEMPLATE buy price is left for trims", !prem.contains("*_SMITHING_TEMPLATE")
                && buy(prem, "*_ARMOR_TRIM_SMITHING_TEMPLATE") == 0);
        check("netherite upgrade template can still be bought", buy(prem, "NETHERITE_UPGRADE_SMITHING_TEMPLATE") > 0
                && !matches(noBuy, "NETHERITE_UPGRADE_SMITHING_TEMPLATE"));

        // 3. "good money, not too high": a sell price never reaches the lowest price of a diamond tool tier
        double maxRare = 0;
        for (String n : rare) maxRare = Math.max(maxRare, prem.getDouble(n + ".sell", prem.getDouble("*_ARMOR_TRIM_SMITHING_TEMPLATE.sell", 0)));
        check("rare sell prices stay at or below 5,000 (best sale less than ~1/4 of the daily limit)",
                maxRare > 0 && maxRare <= 5000 && maxRare * 4 <= y.getDouble("rules.daily-cap-rupees", 18000) + 1);

        // 4. beacon and mob heads: cannot be bought, no buy price, can be sold
        boolean headsOk = true;
        for (String n : List.of("BEACON", "ZOMBIE_HEAD", "CREEPER_HEAD", "SKELETON_SKULL", "PIGLIN_HEAD", "WITHER_SKELETON_SKULL")) {
            if (!matches(noBuy, n) || matches(noSell, n) || buy(prem, n) > 0) headsOk = false;
        }
        check("beacon and mob heads are no-buy, sellable and have no premium buy price", headsOk);
        check("ancient debris is at least as dear as scrap and both are at least 40,000",
                buy(prem, "ANCIENT_DEBRIS") >= buy(prem, "NETHERITE_SCRAP") && buy(prem, "NETHERITE_SCRAP") >= 40000);

        check("blaze rod, breeze rod, leather and slime ball have high buy prices and low sell prices",
                buy(prem, "BLAZE_ROD") >= 1000 && buy(prem, "BREEZE_ROD") > buy(prem, "BLAZE_ROD")
                        && buy(prem, "LEATHER") >= 100 && buy(prem, "SLIME_BALL") >= 100
                        && prem.getDouble("BLAZE_ROD.sell") <= 10 && prem.getDouble("LEATHER.sell") <= 5);
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        if (failed > 0) System.exit(1);
    }
}
