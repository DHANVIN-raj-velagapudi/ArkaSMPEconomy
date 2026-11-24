package com.arka.smp.economy;

import com.arka.smp.core.menu.Items;
import com.arka.smp.core.menu.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tap-only shop screens: categories -> items -> quantity. Nothing to type. */
final class ShopMenus {
    private static final int PER_PAGE = 28;

    private ShopMenus() {
    }

    private static String text(ArkaSMPEconomy plugin, String key, String def) {
        return plugin.getConfig().getString("menus.shop." + key, def);
    }

    private static Component mm(ArkaSMPEconomy plugin, String key, String def, String... kv) {
        return plugin.messages().mm(text(plugin, key, def), kv);
    }

    private static ItemStack button(ArkaSMPEconomy plugin, Material m, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(plugin.messages().mm(l));
        return Items.of(m, plugin.messages().mm(name), lines);
    }

    // ---- categories -------------------------------------------------------------------------

    static void categories(ArkaSMPEconomy plugin, Player p) {
        var shop = plugin.shop();
        var msg = plugin.messages();
        Map<String, List<Material>> catalog = shop.catalog();
        Menu menu = new Menu(mm(plugin, "title", "Shop"), 4);
        menu.set(4, button(plugin, Material.PAPER, "<white>Your balance: <green>" + msg.money(plugin.economy().balance(p.getUniqueId())),
                "<gray>Tap a category, then an item."), null);
        int i = 0;
        for (ShopCategories.Cat c : ShopCategories.ALL) {
            List<Material> list = catalog.get(c.id());
            if (list == null || list.isEmpty()) continue;
            int slot = 10 + i + (i / 7) * 2;
            i++;
            String name = plugin.getConfig().getString("menus.shop.categories." + c.id(), c.name());
            menu.set(slot, button(plugin, c.icon(), "<yellow><bold>" + name, "<gray>" + list.size() + " items"),
                    pl -> items(plugin, pl, c.id(), 0));
        }
        menu.set(29, button(plugin, Material.HOPPER, "<gold><bold>Sell everything sellable",
                "<gray>Sells every sellable item in your inventory", "<gray>(plain items only, within the limits)"),
                pl -> sellAll(plugin, pl));
        menu.set(31, button(plugin, Material.BARRIER, "<white>Close"), Player::closeInventory);
        menu.fill(Items.filler());
        menu.open(p);
    }

    // ---- item list ---------------------------------------------------------------------------

    static void items(ArkaSMPEconomy plugin, Player p, String catId, int page) {
        var shop = plugin.shop();
        var msg = plugin.messages();
        List<Material> list = shop.catalog().getOrDefault(catId, List.of());
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        final int pg = Math.max(0, Math.min(page, pages - 1));
        ShopCategories.Cat cat = ShopCategories.find(catId);
        String catName = plugin.getConfig().getString("menus.shop.categories." + catId, cat.name());
        Menu menu = new Menu(mm(plugin, "title-category", "{category}", "category", catName), 6);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = pg * PER_PAGE + i;
            if (idx >= list.size()) break;
            Material m = list.get(idx);
            int slot = (1 + i / 7) * 9 + 1 + i % 7;
            menu.set(slot, priceIcon(plugin, m), pl -> item(plugin, pl, m, 1, catId, pg));
        }
        menu.set(4, button(plugin, Material.PAPER, "<white>Balance: <green>" + msg.money(plugin.economy().balance(p.getUniqueId())),
                "<gray>Page " + (pg + 1) + "/" + pages), null);
        menu.set(45, button(plugin, Material.ARROW, "<white>Back to categories"), pl -> categories(plugin, pl));
        if (pg > 0) menu.set(48, button(plugin, Material.ARROW, "<white>Previous page"), pl -> items(plugin, pl, catId, pg - 1));
        if (pg < pages - 1) menu.set(50, button(plugin, Material.ARROW, "<white>Next page"), pl -> items(plugin, pl, catId, pg + 1));
        menu.set(49, button(plugin, Material.BARRIER, "<white>Close"), Player::closeInventory);
        menu.fill(Items.filler());
        menu.open(p);
    }

    private static ItemStack priceIcon(ArkaSMPEconomy plugin, Material m) {
        var shop = plugin.shop();
        var msg = plugin.messages();
        List<Component> lore = new ArrayList<>();
        long b = shop.buyPrice(m), s = shop.sellPrice(m);
        lore.add(msg.mm(b > 0 ? "<gray>Buy: <yellow>" + msg.money(b) : "<dark_gray>Cannot be bought"));
        lore.add(msg.mm(s > 0 ? "<gray>Sell: <green>" + msg.money(s) : "<dark_gray>Cannot be sold"));
        lore.add(Component.empty());
        lore.add(msg.mm("<yellow>Tap to buy or sell"));
        return Items.of(m, null, lore);
    }

    // ---- quantity screen ---------------------------------------------------------------------

    static void item(ArkaSMPEconomy plugin, Player p, Material m, int qty, String backCat, int backPage) {
        var shop = plugin.shop();
        var msg = plugin.messages();
        int max = Math.max(1, shop.maxBuy());
        final int q = Math.max(1, Math.min(qty, max));
        long buy = shop.buyPrice(m), sell = shop.sellPrice(m);
        Menu menu = new Menu(mm(plugin, "title-item", "{item}", "item", ShopManager.pretty(m)), 4);
        menu.set(4, priceIcon(plugin, m), null);

        int[] steps = {-64, -16, -8, -1};
        for (int i = 0; i < 4; i++) {
            final int d = steps[i];
            menu.set(9 + i, button(plugin, Material.RED_STAINED_GLASS_PANE, "<red>" + d), pl -> item(plugin, pl, m, q + d, backCat, backPage));
        }
        menu.set(13, Items.of(Material.PAPER, msg.mm("<white>Amount: <aqua>" + q), List.of(msg.mm("<gray>Tap + or - to change"))), null);
        int[] ups = {1, 8, 16, 64};
        for (int i = 0; i < 4; i++) {
            final int d = ups[i];
            menu.set(14 + i, button(plugin, Material.LIME_STAINED_GLASS_PANE, "<green>+" + d), pl -> item(plugin, pl, m, q + d, backCat, backPage));
        }
        Runnable again = () -> { };
        if (buy > 0) {
            long cost = shop.costOf(m, q);
            menu.set(20, button(plugin, Material.LIME_CONCRETE, "<green><bold>Buy " + q,
                    "<gray>Total: <yellow>" + msg.money(cost),
                    "<gray>You have: <white>" + msg.money(plugin.economy().balance(p.getUniqueId()))),
                    pl -> buy(plugin, pl, m, q, backCat, backPage));
        }
        if (sell > 0) {
            menu.set(22, button(plugin, Material.GOLD_BLOCK, "<gold><bold>Sell all you carry",
                    "<gray>Sells every plain " + ShopManager.pretty(m) + " in your inventory"),
                    pl -> sell(plugin, pl, m, Integer.MAX_VALUE, backCat, backPage));
            menu.set(24, button(plugin, Material.GOLD_INGOT, "<gold><bold>Sell " + q,
                    "<gray>Pays: <green>" + msg.money(sell * q)),
                    pl -> sell(plugin, pl, m, q, backCat, backPage));
        }
        menu.set(31, button(plugin, Material.ARROW, "<white>Back"), pl -> items(plugin, pl, backCat, backPage));
        menu.fill(Items.filler());
        menu.open(p);
    }

    // ---- buying / selling flows --------------------------------------------------------------

    private static void buy(ArkaSMPEconomy plugin, Player p, Material m, int qty, String cat, int page) {
        var shop = plugin.shop();
        var msg = plugin.messages();
        long cost = shop.costOf(m, qty);
        long confirmAt = plugin.economy().configMoney("shop.big-buy-confirm", 5000);
        Runnable reopen = () -> item(plugin, p, m, qty, cat, page);
        if (cost >= confirmAt) {
            confirm(plugin, p, "<dark_gray>Buy " + qty + "x " + ShopManager.pretty(m) + "?",
                    List.of("<gray>Total: <yellow>" + msg.money(cost)), () -> performBuy(plugin, p, m, qty, reopen), reopen);
        } else {
            performBuy(plugin, p, m, qty, reopen);
        }
    }

    private static void performBuy(ArkaSMPEconomy plugin, Player p, Material m, int qty, Runnable reopen) {
        var shop = plugin.shop();
        var msg = plugin.messages();
        long cost = shop.costOf(m, qty);
        switch (shop.buy(p, m, qty)) {
            case OK -> msg.send(p, "shop-bought", "qty", String.valueOf(qty), "item", ShopManager.pretty(m), "amount", msg.money(cost));
            case NO_SPACE -> msg.send(p, "shop-no-space");
            case INSUFFICIENT -> msg.send(p, "shop-insufficient", "cost", msg.money(cost),
                    "balance", msg.money(plugin.economy().balance(p.getUniqueId())));
            case FROZEN -> msg.send(p, "pay-frozen");
            case NOT_FOR_SALE -> msg.send(p, "shop-not-for-sale");
            default -> msg.send(p, "shop-error");
        }
        reopen.run();
    }

    private static void sell(ArkaSMPEconomy plugin, Player p, Material m, int qty, String cat, int page) {
        Runnable reopen = () -> item(plugin, p, m, 1, cat, page);
        runSale(plugin, p, m, qty, reopen);
    }

    /** Shared by the menus and /sell hand: plan, maybe confirm, execute, tell the player. */
    static void runSale(ArkaSMPEconomy plugin, Player p, Material only, int qty, Runnable after) {
        var shop = plugin.shop();
        String blocker = shop.sellBlocker(p);
        if (blocker != null) {
            plugin.messages().send(p, blocker, "hours", trimHours(shop.minPlayHours()));
            after.run();
            return;
        }
        ShopManager.Plan plan = shop.plan(p, s -> true, only, qty);
        finishSale(plugin, p, plan, () -> shop.plan(p, s -> true, only, qty), after);
    }

    static void sellAll(ArkaSMPEconomy plugin, Player p) {
        var shop = plugin.shop();
        p.closeInventory();
        String blocker = shop.sellBlocker(p);
        if (blocker != null) {
            plugin.messages().send(p, blocker, "hours", trimHours(shop.minPlayHours()));
            return;
        }
        ShopManager.Plan plan = shop.plan(p, s -> true, null, Integer.MAX_VALUE);
        finishSale(plugin, p, plan, () -> shop.plan(p, s -> true, null, Integer.MAX_VALUE), () -> { });
    }

    static void finishSale(ArkaSMPEconomy plugin, Player p, ShopManager.Plan plan,
                                   java.util.function.Supplier<ShopManager.Plan> replan, Runnable after) {
        var shop = plugin.shop();
        var msg = plugin.messages();
        if (plan.total() <= 0) {
            if (plan.dayLimited()) msg.send(p, "shop-limit-day", "cap", msg.money(shop.dayRemaining(p.getUniqueId())));
            else if (plan.hourLimited()) msg.send(p, "shop-limit-hour");
            else if (plan.refused() > 0) msg.send(p, "shop-refused", "n", String.valueOf(plan.refused()));
            else msg.send(p, "shop-nothing");
            after.run();
            return;
        }
        if (plan.total() >= shop.bigSalePaise()) {
            List<String> lines = new ArrayList<>();
            lines.add("<gray>You get: <green>" + msg.money(plan.total()) + " <gray>for <white>" + plan.items() + " <gray>items");
            int shown = 0;
            for (var e : plan.byMaterial().entrySet()) {
                if (shown++ >= 6) break;
                lines.add("<dark_gray>- <white>" + e.getValue() + "x " + ShopManager.pretty(e.getKey()));
            }
            confirm(plugin, p, "<dark_gray>Sell for " + msg.money(plan.total()) + "?", lines,
                    () -> complete(plugin, p, replan.get(), after), after);
            return;
        }
        complete(plugin, p, plan, after);
    }

    static void complete(ArkaSMPEconomy plugin, Player p, ShopManager.Plan plan, Runnable after) {
        var shop = plugin.shop();
        var msg = plugin.messages();
        if (plan.total() <= 0 || !shop.execute(p, plan)) {
            msg.send(p, "shop-sell-failed");
        } else {
            msg.send(p, "shop-sold", "items", String.valueOf(plan.items()), "amount", msg.money(plan.total()));
            if (plan.dayLimited()) msg.send(p, "shop-limit-day", "cap", msg.money(shop.dayRemaining(p.getUniqueId())));
            else if (plan.hourLimited()) msg.send(p, "shop-limit-hour");
            if (plan.refused() > 0) msg.send(p, "shop-refused", "n", String.valueOf(plan.refused()));
        }
        after.run();
    }

    private static String trimHours(double h) {
        return h == Math.floor(h) ? String.valueOf((long) h) : String.valueOf(h);
    }

    // ---- confirm ------------------------------------------------------------------------------

    static void confirm(ArkaSMPEconomy plugin, Player p, String title, List<String> lines, Runnable yes, Runnable no) {
        var msg = plugin.messages();
        Menu menu = new Menu(msg.mm(title), 3);
        List<Component> lore = new ArrayList<>();
        for (String l : lines) lore.add(msg.mm(l));
        menu.set(4, Items.of(Material.PAPER, msg.mm("<white>Please confirm"), lore), null);
        menu.set(11, button(plugin, Material.LIME_CONCRETE, "<green><bold>Yes"), pl -> {
            pl.closeInventory();
            yes.run();
        });
        menu.set(15, button(plugin, Material.RED_CONCRETE, "<red><bold>No"), pl -> {
            pl.closeInventory();
            no.run();
        });
        menu.fill(Items.filler());
        menu.open(p);
    }
}
