package com.arka.smp.economy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /bal, /pay, /baltop, /paytoggle */
public final class EconCommands implements CommandExecutor, TabCompleter, Listener {
    private final ArkaSMPEconomy plugin;
    private final Map<UUID, Long> lastPay = new HashMap<>();

    EconCommands(ArkaSMPEconomy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastPay.remove(e.getPlayer().getUniqueId());
    }

    EconomyManager.Account resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return plugin.economy().get(online.getUniqueId());
        if (!plugin.getConfig().getBoolean("pay.allow-offline", true)) return null;
        return plugin.economy().findByName(name);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.messages();
        var eco = plugin.economy();
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "bal" -> {
                if (args.length == 0) {
                    if (!(sender instanceof Player p)) {
                        msg.send(sender, "player-only");
                        return true;
                    }
                    msg.send(p, "balance-self", "amount", msg.money(eco.balance(p.getUniqueId())));
                } else {
                    EconomyManager.Account a = eco.findByName(args[0]);
                    if (a == null) msg.send(sender, "no-account");
                    else msg.send(sender, "balance-other", "player", a.name, "amount", msg.money(a.balance));
                }
            }
            case "baltop" -> baltop(sender, args);
            case "paytoggle" -> {
                if (!(sender instanceof Player p)) {
                    msg.send(sender, "player-only");
                    return true;
                }
                msg.send(p, eco.toggleAcceptPay(p.getUniqueId()) ? "paytoggle-on" : "paytoggle-off");
            }
            case "pay" -> {
                if (!(sender instanceof Player p)) {
                    msg.send(sender, "player-only");
                    return true;
                }
                if (args.length == 0) {
                    PayMenu.pickPlayer(plugin, this, p, 0);
                    return true;
                }
                if (args.length < 2) {
                    msg.send(p, "pay-usage");
                    return true;
                }
                EconomyManager.Account target = resolve(args[0]);
                if (target == null) {
                    msg.send(p, "unknown-player");
                    return true;
                }
                Long amount = Money.parse(args[1]);
                if (amount == null) {
                    msg.send(p, "invalid-amount");
                    return true;
                }
                startPay(p, target, amount, false);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Validate and (for big amounts) ask for confirmation before paying. */
    void startPay(Player from, EconomyManager.Account target, long amount, boolean confirmed) {
        var msg = plugin.messages();
        var eco = plugin.economy();
        long min = eco.configMoney("pay.minimum", 1);
        long max = plugin.getConfig().getDouble("pay.maximum", 0) > 0 ? eco.configMoney("pay.maximum", 0) : 0;
        if (target.id.equals(from.getUniqueId())) {
            msg.send(from, "pay-self");
            return;
        }
        if (amount < min) {
            msg.send(from, "pay-min", "min", msg.money(min));
            return;
        }
        if (max > 0 && amount > max) {
            msg.send(from, "pay-max", "max", msg.money(max));
            return;
        }
        long confirmOver = eco.configMoney("pay.confirm-over", 10000);
        if (!confirmed && confirmOver > 0 && amount >= confirmOver) {
            PayMenu.confirm(plugin, this, from, target.id, amount);
            return;
        }
        executePay(from, target.id, amount);
    }

    /** The one place a /pay actually happens (command, menu and confirm all end here). */
    void executePay(Player from, UUID targetId, long amount) {
        var msg = plugin.messages();
        var eco = plugin.economy();
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getInt("pay.cooldown-seconds", 2) * 1000L;
        Long last = lastPay.get(from.getUniqueId());
        if (last != null && now - last < cooldown) {
            msg.send(from, "pay-cooldown");
            return;
        }
        EconomyManager.Account target = eco.get(targetId);
        if (target == null) {
            msg.send(from, "no-account");
            return;
        }
        var result = eco.transfer(from.getUniqueId(), targetId, amount, "pay", from.getName() + " -> " + target.name);
        switch (result) {
            case OK -> {
                lastPay.put(from.getUniqueId(), now);
                msg.send(from, "pay-sent", "amount", msg.money(amount), "player", target.name);
                Player online = Bukkit.getPlayer(targetId);
                if (online != null) msg.send(online, "pay-received", "amount", msg.money(amount), "player", from.getName());
            }
            case INSUFFICIENT -> msg.send(from, "pay-insufficient", "balance", msg.money(eco.balance(from.getUniqueId())));
            case FROZEN -> msg.send(from, "pay-frozen");
            case TOO_RICH -> msg.send(from, "pay-too-rich", "player", target.name);
            case NOT_ACCEPTING -> msg.send(from, "pay-not-accepting", "player", target.name);
            case SAME -> msg.send(from, "pay-self");
            case NO_ACCOUNT -> msg.send(from, "no-account");
            default -> msg.send(from, "invalid-amount");
        }
    }

    private void baltop(CommandSender sender, String[] args) {
        var msg = plugin.messages();
        var eco = plugin.economy();
        int size = Math.max(3, Math.min(25, plugin.getConfig().getInt("baltop.page-size", 10)));
        List<EconomyManager.TopEntry> list = eco.topEntries();
        int pages = Math.max(1, (list.size() + size - 1) / size);
        int page = 1;
        if (args.length > 0) {
            try {
                page = Math.max(1, Math.min(pages, Integer.parseInt(args[0])));
            } catch (NumberFormatException ignored) {
            }
        }
        msg.send(sender, "baltop-header", "page", String.valueOf(page), "pages", String.valueOf(pages));
        if (list.isEmpty()) {
            msg.send(sender, "baltop-empty");
            return;
        }
        for (int i = (page - 1) * size; i < Math.min(list.size(), page * size); i++) {
            var e = list.get(i);
            msg.send(sender, "baltop-line", "rank", String.valueOf(i + 1), "player", e.name(),
                    "amount", msg.money(e.balance()));
        }
        if (sender instanceof Player p) {
            int rank = eco.rankOf(p.getUniqueId());
            if (rank > 0) {
                msg.send(p, "baltop-you", "rank", String.valueOf(rank), "amount", msg.money(eco.balance(p.getUniqueId())));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1 && (name.equals("pay") || name.equals("bal"))) {
            Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
