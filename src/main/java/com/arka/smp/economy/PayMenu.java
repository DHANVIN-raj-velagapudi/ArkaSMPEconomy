package com.arka.smp.economy;

import com.arka.smp.core.menu.Items;
import com.arka.smp.core.menu.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Tap-only /pay: pick a player, pick an amount, confirm. Nothing to type. */
final class PayMenu {
    private static final int PER_PAGE = 28;

    private PayMenu() {
    }

    static void pickPlayer(ArkaSMPEconomy plugin, EconCommands cmds, Player viewer, int page) {
        var msg = plugin.messages();
        List<Player> list = Bukkit.getOnlinePlayers().stream()
                .filter(o -> !o.getUniqueId().equals(viewer.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).map(o -> (Player) o).toList();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        final int pg = Math.max(0, Math.min(page, pages - 1));
        Menu menu = new Menu(msg.mm(plugin.getConfig().getString("menus.pay.pick-title", "Pay who?")), 6);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = pg * PER_PAGE + i;
            if (idx >= list.size()) break;
            Player target = list.get(idx);
            int slot = (1 + i / 7) * 9 + 1 + i % 7;
            menu.set(slot, Items.head(target, msg.mm("<yellow>{name}", "name", target.getName()),
                    List.of(msg.mm("<gray>Click to pay this player"))),
                    p -> pickAmount(plugin, cmds, p, target.getUniqueId()));
        }
        if (list.isEmpty()) menu.set(22, Items.of(Material.GLASS_PANE, msg.mm("<gray>Nobody else is online"), null), null);
        if (pg > 0) menu.set(48, Items.of(Material.ARROW, msg.mm("<white>Previous page"), null),
                p -> pickPlayer(plugin, cmds, p, pg - 1));
        if (pg < pages - 1) menu.set(50, Items.of(Material.ARROW, msg.mm("<white>Next page"), null),
                p -> pickPlayer(plugin, cmds, p, pg + 1));
        menu.set(49, Items.of(Material.BARRIER, msg.mm("<white>Close"), null), Player::closeInventory);
        menu.fill(Items.filler());
        menu.open(viewer);
    }

    static void pickAmount(ArkaSMPEconomy plugin, EconCommands cmds, Player viewer, UUID targetId) {
        var msg = plugin.messages();
        var eco = plugin.economy();
        EconomyManager.Account target = eco.get(targetId);
        if (target == null) {
            msg.send(viewer, "no-account");
            return;
        }
        Menu menu = new Menu(msg.mm(plugin.getConfig().getString("menus.pay.amount-title", "Pay {player}"),
                "player", target.name), 3);
        menu.set(4, Items.of(Material.PAPER, msg.mm("<white>Your balance: <green>{b}", "b",
                msg.money(eco.balance(viewer.getUniqueId()))), null), null);
        List<Integer> amounts = plugin.getConfig().getIntegerList("pay.menu-amounts");
        for (int i = 0; i < Math.min(7, amounts.size()); i++) {
            long paise = Math.max(1, amounts.get(i)) * Money.SCALE;
            menu.set(10 + i, Items.of(Material.GOLD_INGOT, msg.mm("<gold>{a}", "a", msg.money(paise)),
                    List.of(msg.mm("<gray>Click to continue"))),
                    p -> confirm(plugin, cmds, p, targetId, paise));
        }
        menu.set(22, Items.of(Material.ARROW, msg.mm("<white>Back"), null), p -> pickPlayer(plugin, cmds, p, 0));
        menu.set(26, Items.of(Material.WRITABLE_BOOK, msg.mm("<gray>Other amount: <white>/pay {p} <amount>", "p", target.name), null), null);
        menu.fill(Items.filler());
        menu.open(viewer);
    }

    static void confirm(ArkaSMPEconomy plugin, EconCommands cmds, Player viewer, UUID targetId, long paise) {
        var msg = plugin.messages();
        EconomyManager.Account target = plugin.economy().get(targetId);
        if (target == null) {
            msg.send(viewer, "no-account");
            return;
        }
        Component title = msg.mm(plugin.getConfig().getString("menus.pay.confirm-title", "Pay {amount} to {player}?"),
                "amount", msg.money(paise), "player", target.name);
        Menu menu = new Menu(title, 3);
        menu.set(11, Items.of(Material.LIME_CONCRETE, msg.mm("<green><bold>Yes, pay {a}", "a", msg.money(paise)),
                List.of(msg.mm("<gray>To: <white>{p}", "p", target.name))), p -> {
            p.closeInventory();
            cmds.executePay(p, targetId, paise);   // re-validated atomically inside
        });
        menu.set(15, Items.of(Material.RED_CONCRETE, msg.mm("<red><bold>No"), null), Player::closeInventory);
        menu.fill(Items.filler());
        menu.open(viewer);
    }
}
