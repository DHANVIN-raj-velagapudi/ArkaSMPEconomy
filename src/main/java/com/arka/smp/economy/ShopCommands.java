package com.arka.smp.economy;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /shop, /sell, /worth and /shopadmin. */
final class ShopCommands implements CommandExecutor, TabCompleter {
    private final ArkaSMPEconomy plugin;

    ShopCommands(ArkaSMPEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        var msg = plugin.messages();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("shopadmin")) {
            admin(sender, a);
            return true;
        }
        if (!(sender instanceof Player p)) {
            msg.send(sender, "player-only");
            return true;
        }
        if (!plugin.getConfig().getBoolean("shop.enabled", true)) {
            msg.send(p, "shop-disabled");
            return true;
        }
        switch (name) {
            case "shop" -> ShopMenus.categories(plugin, p);
            case "sell" -> sell(p, a);
            case "sellgui" -> plugin.sellGui().open(p);
            case "worth" -> worth(p, a);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void sell(Player p, String[] a) {
        var msg = plugin.messages();
        var shop = plugin.shop();
        String mode = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "all" -> ShopMenus.sellAll(plugin, p);
            case "hand" -> {
                String blocker = shop.sellBlocker(p);
                if (blocker != null) {
                    msg.send(p, blocker, "hours", String.valueOf((long) shop.minPlayHours()));
                    return;
                }
                final int slot = p.getInventory().getHeldItemSlot();
                ItemStack held = p.getInventory().getItem(slot);
                if (held == null || held.getType().isAir()) {
                    msg.send(p, "worth-hold");
                    return;
                }
                ShopManager.Plan plan = shop.plan(p, s -> s == slot, null, Integer.MAX_VALUE);
                ShopMenus.finishSale(plugin, p, plan, () -> shop.plan(p, s -> s == slot, null, Integer.MAX_VALUE), () -> { });
            }
            default -> msg.send(p, "shop-sell-usage");
        }
    }

    private void worth(Player p, String[] a) {
        var msg = plugin.messages();
        var shop = plugin.shop();
        Material m;
        if (a.length > 0) {
            m = Material.matchMaterial(a[0]);
        } else {
            ItemStack held = p.getInventory().getItemInMainHand();
            m = held.getType().isAir() ? null : held.getType();
            if (m == null) {
                msg.send(p, "worth-hold");
                return;
            }
        }
        if (m == null) {
            msg.send(p, "worth-unknown");
            return;
        }
        long s = shop.sellPrice(m), b = shop.buyPrice(m);
        if (s <= 0 && b <= 0) {
            msg.send(p, "worth-none", "item", ShopManager.pretty(m));
            return;
        }
        msg.send(p, "worth-line", "item", ShopManager.pretty(m),
                "sell", s > 0 ? msg.money(s) : "-", "buy", b > 0 ? msg.money(b) : "-");
        if (s > 0) {
            msg.send(p, "worth-hour-left", "amount", msg.money(shop.hourRemaining(p.getUniqueId(), m)));
        }
    }

    // ---- admin ------------------------------------------------------------------------------------

    private void admin(CommandSender s, String[] a) {
        var msg = plugin.messages();
        var shop = plugin.shop();
        if (!s.hasPermission("arkaeco.shop.admin")) {
            msg.send(s, "no-permission");
            return;
        }
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                shop.reload();
                msg.raw(s, "<green>Shop prices rebuilt: <white>{sell} <gray>sellable, <white>{buy} <gray>buyable, <white>{d} <gray>derived, <white>{o} <gray>overrides.",
                        "sell", String.valueOf(shop.sellTable().size()), "buy", String.valueOf(shop.buyTable().size()),
                        "d", String.valueOf(shop.derivedCount()), "o", String.valueOf(shop.overrideCount()));
            }
            case "info" -> {
                Material m = a.length > 1 ? Material.matchMaterial(a[1]) : null;
                if (m == null) {
                    msg.raw(s, "<red>/shopadmin info <ITEM>");
                    return;
                }
                msg.raw(s, "<gold>{i}<gray>: sell <green>{sell}<gray>, buy <yellow>{buy}<gray>, category <white>{c}",
                        "i", m.name(), "sell", shop.sellPrice(m) > 0 ? msg.money(shop.sellPrice(m)) : "-",
                        "buy", shop.buyPrice(m) > 0 ? msg.money(shop.buyPrice(m)) : "-", "c", ShopCategories.of(m));
            }
            case "set" -> {
                Material m = a.length > 2 ? Material.matchMaterial(a[1]) : null;
                Long sell = a.length > 2 ? (a[2].equals("0") ? Long.valueOf(0) : Money.parse(a[2])) : null;
                Long buy = a.length > 3 ? Money.parse(a[3]) : null;
                if (m == null || !m.isItem() || sell == null || (a.length > 3 && buy == null)) {
                    msg.raw(s, "<red>/shopadmin set <ITEM> <sellPrice> [buyPrice]  (rupees, 0 = disable)");
                    return;
                }
                if (buy != null && sell > 0 && buy > 0 && buy < sell) {
                    msg.raw(s, "<red>The buy price must not be lower than the sell price (that would let people print money).");
                    return;
                }
                if (a.length == 2 + 1 && sell > 0) buy = null;   // default buy = sell x multiplier
                shop.setOverride(m, sell / (double) Money.SCALE, buy == null ? null : buy / (double) Money.SCALE);
                plugin.getLogger().info(s.getName() + " set the price of " + m.name() + " to sell " + sell + " buy " + buy + " paise");
                msg.raw(s, "<green>{i} now sells for {sell} and costs {buy}.", "i", m.name(),
                        "sell", shop.sellPrice(m) > 0 ? msg.money(shop.sellPrice(m)) : "-",
                        "buy", shop.buyPrice(m) > 0 ? msg.money(shop.buyPrice(m)) : "-");
            }
            case "disable" -> {
                Material m = a.length > 1 ? Material.matchMaterial(a[1]) : null;
                String what = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "both";
                if (m == null || !m.isItem() || !List.of("sell", "buy", "both").contains(what)) {
                    msg.raw(s, "<red>/shopadmin disable <ITEM> [sell|buy|both]");
                    return;
                }
                shop.setOverride(m, what.equals("buy") ? null : 0.0, what.equals("sell") ? null : 0.0);
                plugin.getLogger().info(s.getName() + " disabled " + what + " of " + m.name());
                msg.raw(s, "<green>{i}: {w} disabled.", "i", m.name(), "w", what);
            }
            case "reset" -> {
                Material m = a.length > 1 ? Material.matchMaterial(a[1]) : null;
                if (m == null) {
                    msg.raw(s, "<red>/shopadmin reset <ITEM>");
                    return;
                }
                msg.raw(s, shop.clearOverride(m) ? "<green>{i} is back to its calculated price." : "<yellow>{i} had no override.", "i", m.name());
            }
            case "audit" -> audit(s);
            default -> {
                msg.raw(s, "<gold>Shop admin <gray>(/shopadmin)");
                msg.raw(s, "<white>reload <gray>| <white>info <ITEM> <gray>| <white>set <ITEM> <sell> [buy] <gray>| <white>disable <ITEM> [sell|buy|both] <gray>| <white>reset <ITEM> <gray>| <white>audit");
            }
        }
    }

    private void audit(CommandSender s) {
        var msg = plugin.messages();
        var shop = plugin.shop();
        msg.raw(s, "<gold>Shop audit: <white>{a} <gray>sellable, <white>{b} <gray>buyable, <white>{d} <gray>derived, <white>{o} <gray>overrides",
                "a", String.valueOf(shop.sellTable().size()), "b", String.valueOf(shop.buyTable().size()),
                "d", String.valueOf(shop.derivedCount()), "o", String.valueOf(shop.overrideCount()));
        int loss = 0;
        for (var e : shop.sellTable().entrySet()) {
            long buy = shop.buyPrice(e.getKey());
            if (buy > 0 && buy < e.getValue()) {          // can only happen through an admin override
                loss++;
                msg.raw(s, "<red>MONEY LOOP: {i} sells for {sell} but costs only {buy}", "i", e.getKey().name(),
                        "sell", msg.money(e.getValue()), "buy", msg.money(buy));
            }
        }
        msg.raw(s, loss == 0 ? "<green>No item can be bought for less than it sells for." : "<red>{n} item(s) above can print money. Fix with /shopadmin set.", "n", String.valueOf(loss));
        List<Map.Entry<Material, Long>> top = new ArrayList<>(shop.sellTable().entrySet());
        top.sort(Map.Entry.<Material, Long>comparingByValue().reversed());
        for (int i = 0; i < Math.min(5, top.size()); i++) {
            msg.raw(s, "<gray>#{n} priciest: <white>{i} <green>{p}", "n", String.valueOf(i + 1),
                    "i", top.get(i).getKey().name(), "p", msg.money(top.get(i).getValue()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        if (name.equals("sell") && a.length == 1) out.addAll(List.of("hand", "all"));
        if (name.equals("shopadmin")) {
            if (a.length == 1) out.addAll(List.of("reload", "info", "set", "disable", "reset", "audit"));
            else if (a.length == 2 && !a[0].equalsIgnoreCase("reload") && !a[0].equalsIgnoreCase("audit")) {
                for (Material m : Material.values()) {
                    if (!m.isLegacy() && m.isItem() && m.name().toLowerCase(Locale.ROOT).startsWith(last)) out.add(m.name().toLowerCase(Locale.ROOT));
                    if (out.size() >= 40) break;
                }
                return out;
            } else if (a.length == 3 && a[0].equalsIgnoreCase("disable")) out.addAll(List.of("sell", "buy", "both"));
        }
        if (name.equals("worth") && a.length == 1) {
            for (Material m : plugin.shop().sellTable().keySet()) {
                if (m.name().toLowerCase(Locale.ROOT).startsWith(last)) out.add(m.name().toLowerCase(Locale.ROOT));
                if (out.size() >= 40) break;
            }
            return out;
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        out.sort(Comparator.naturalOrder());
        return out;
    }
}
