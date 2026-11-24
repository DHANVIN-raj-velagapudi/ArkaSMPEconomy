package com.arka.smp.economy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /arkaeco: give, take, set, reset, balance, freeze, log, import, reload. Every change is logged. */
final class EcoAdmin implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("give", "take", "set", "reset", "balance", "freeze",
            "unfreeze", "log", "import", "reload", "flush", "resume", "help");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());

    private final ArkaSMPEconomy plugin;

    EcoAdmin(ArkaSMPEconomy plugin) {
        this.plugin = plugin;
    }

    private EconomyManager.Account find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? plugin.economy().get(online.getUniqueId()) : plugin.economy().findByName(name);
    }

    private static String actor(CommandSender s) {
        return s instanceof Player p ? p.getName() : "console";
    }

    private String explain(EconomyManager.Result r) {
        return switch (r) {
            case OK -> "OK";
            case INSUFFICIENT -> "the player does not have that much";
            case TOO_RICH -> "that would go over the maximum balance";
            case FROZEN -> "the account is frozen";
            case NO_ACCOUNT -> "the player has no account";
            default -> "invalid amount";
        };
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        var msg = plugin.messages();
        var eco = plugin.economy();
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        if (!SUBS.contains(sub)) sub = "help";
        String perm = sub.equals("unfreeze") ? "freeze" : (sub.equals("flush") || sub.equals("resume")) ? "reload" : sub;
        if (!sub.equals("help") && !s.hasPermission("arkaeco.admin." + perm)) {
            msg.send(s, "no-permission");
            return true;
        }
        switch (sub) {
            case "give", "take", "set" -> {
                if (a.length < 3) {
                    msg.raw(s, "<red>/arkaeco {sub} <player> <amount>", "sub", sub);
                    return true;
                }
                var acc = find(a[1]);
                if (acc == null) {
                    msg.send(s, "unknown-player");
                    return true;
                }
                Long amt = a[2].trim().equals("0") && sub.equals("set") ? Long.valueOf(0) : Money.parse(a[2]);
                if (amt == null) {
                    msg.send(s, "invalid-amount");
                    return true;
                }
                String note = "by " + actor(s);
                var r = switch (sub) {
                    case "give" -> eco.deposit(acc.id, amt, "admin-give", note, true);
                    case "take" -> eco.withdraw(acc.id, amt, "admin-take", note, true);
                    default -> eco.set(acc.id, amt, note);
                };
                if (r == EconomyManager.Result.OK) {
                    plugin.getLogger().info(actor(s) + " used " + sub + " " + acc.name + " " + Money.plain(amt));
                    msg.raw(s, "<green>Done. <white>{p} <gray>now has <white>{b}", "p", acc.name, "b", msg.money(acc.balance));
                } else {
                    msg.raw(s, "<red>Failed: {why}", "why", explain(r));
                }
            }
            case "reset" -> {
                if (a.length < 2 || find(a[1]) == null) {
                    msg.send(s, "unknown-player");
                    return true;
                }
                var acc = find(a[1]);
                eco.set(acc.id, eco.startBalance(), "reset by " + actor(s));
                msg.raw(s, "<green>{p} reset to {b}", "p", acc.name, "b", msg.money(acc.balance));
            }
            case "balance" -> {
                var acc = a.length < 2 ? null : find(a[1]);
                if (acc == null) {
                    msg.send(s, "unknown-player");
                    return true;
                }
                msg.raw(s, "<gold>{p}<gray>: <white>{b} <gray>| frozen: <white>{f} <gray>| accepts pay: <white>{ap} <gray>| baltop rank: <white>{r}",
                        "p", acc.name, "b", msg.money(acc.balance), "f", String.valueOf(acc.frozen),
                        "ap", String.valueOf(acc.acceptPay), "r", String.valueOf(eco.rankOf(acc.id)));
            }
            case "freeze", "unfreeze" -> {
                var acc = a.length < 2 ? null : find(a[1]);
                if (acc == null) {
                    msg.send(s, "unknown-player");
                    return true;
                }
                eco.setFrozen(acc.id, sub.equals("freeze"));
                plugin.getLogger().info(actor(s) + " " + sub + " " + acc.name);
                msg.raw(s, sub.equals("freeze") ? "<yellow>{p} is now frozen: no paying, trading or rewards." : "<green>{p} is unfrozen.", "p", acc.name);
            }
            case "log" -> log(s, a);
            case "import" -> {
                if (a.length < 2 || !a[1].equalsIgnoreCase("essentials")) {
                    msg.raw(s, "<red>/arkaeco import essentials [confirm|force]");
                    return true;
                }
                String mode = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
                importEssentials(s, mode.equals("confirm") || mode.equals("force"), mode.equals("force"));
            }
            case "flush" -> {          // used by McBackup before it copies economy.db
                plugin.db().checkpoint(true);
                msg.raw(s, "<green>Economy database flushed for backup.");
            }
            case "resume" -> {         // used by McBackup after the copy
                plugin.db().checkpoint(false);
                msg.raw(s, "<green>Economy database back to normal.");
            }
            case "reload" -> {
                plugin.reload();
                msg.raw(s, "<green>ArkaSMPEconomy reloaded.");
            }
            default -> {
                msg.raw(s, "<gold>Economy admin <gray>(/arkaeco)");
                msg.raw(s, "<white>give|take|set <player> <amount> <gray>| <white>reset <player> <gray>| <white>balance <player>");
                msg.raw(s, "<white>freeze|unfreeze <player> <gray>| <white>log [player] [count]");
                msg.raw(s, "<white>import essentials [confirm|force] <gray>| <white>reload");
            }
        }
        return true;
    }

    private void log(CommandSender s, String[] a) {
        var msg = plugin.messages();
        UUID who = null;
        int count = 15;
        if (a.length > 1) {
            var acc = find(a[1]);
            if (acc != null) who = acc.id;
            else {
                try {
                    count = Integer.parseInt(a[1]);
                } catch (NumberFormatException e) {
                    msg.send(s, "unknown-player");
                    return;
                }
            }
        }
        if (a.length > 2) {
            try {
                count = Integer.parseInt(a[2]);
            } catch (NumberFormatException ignored) {
            }
        }
        final UUID target = who;
        final int n = count;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Database.Tx> rows = plugin.db().recent(target, null, n);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    msg.raw(s, "<gold>Last {n} transactions", "n", String.valueOf(rows.size()));
                    for (Database.Tx t : rows) {
                        msg.raw(s, "<gray>{time} <white>{type} <gray>{from} -> {to} <green>{amt} <dark_gray>{note}",
                                "time", TIME.format(Instant.ofEpochMilli(t.ts())), "type", t.type(),
                                "from", name(t.from()), "to", name(t.to()), "amt", msg.money(t.amount()),
                                "note", t.note() == null ? "" : t.note());
                    }
                });
            } catch (SQLException e) {
                Bukkit.getScheduler().runTask(plugin, () -> msg.raw(s, "<red>Could not read the log."));
            }
        });
    }

    private String name(String uuid) {
        if (uuid == null) return "system";
        try {
            var acc = plugin.economy().get(UUID.fromString(uuid));
            return acc == null ? "?" : acc.name;
        } catch (IllegalArgumentException e) {
            return "?";
        }
    }

    // ---- Essentials import ------------------------------------------------------------------

    private record Entry(UUID id, String name, long paise) {
    }

    private void importEssentials(CommandSender s, boolean apply, boolean force) {
        var msg = plugin.messages();
        File dir = new File(plugin.getDataFolder().getParentFile(), "Essentials/userdata");
        if (!dir.isDirectory()) {
            msg.raw(s, "<red>Essentials userdata folder not found.");
            return;
        }
        if (apply && plugin.essentialsImported() && !force) {
            msg.raw(s, "<red>Already imported. Run '/arkaeco import essentials force' to import again (overwrites balances).");
            return;
        }
        final long cap = plugin.economy().maxBalance();
        msg.raw(s, "<yellow>Reading Essentials data...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Entry> entries = new ArrayList<>();
            File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
            int skipped = 0;
            if (files != null) {
                for (File f : files) {
                    try {
                        UUID id = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                        Object raw = y.get("money");
                        BigDecimal bd = new BigDecimal(raw == null ? "0" : raw.toString().trim());
                        if (bd.signum() < 0) bd = BigDecimal.ZERO;
                        long paise = bd.setScale(2, RoundingMode.DOWN).movePointRight(2).min(BigDecimal.valueOf(cap)).longValueExact();
                        String name = y.getString("last-account-name", y.getString("lastAccountName", "unknown"));
                        entries.add(new Entry(id, name, paise));
                    } catch (RuntimeException e) {
                        skipped++;
                    }
                }
            }
            final int bad = skipped;
            Bukkit.getScheduler().runTask(plugin, () -> {
                BigDecimal total = BigDecimal.ZERO;
                long largest = 0;
                for (Entry e : entries) {
                    total = total.add(BigDecimal.valueOf(e.paise()));
                    largest = Math.max(largest, e.paise());
                }
                msg.raw(s, "<gold>Essentials: <white>{n} <gray>accounts, total <white>{t}<gray>, largest <white>{l}<gray>, unreadable <white>{b}",
                        "n", String.valueOf(entries.size()), "t", msg.money(total.min(BigDecimal.valueOf(Long.MAX_VALUE)).longValue()),
                        "l", msg.money(largest), "b", String.valueOf(bad));
                if (!apply) {
                    msg.raw(s, "<yellow>Dry run only. Run <white>/arkaeco import essentials confirm <yellow>to apply.");
                    return;
                }
                for (Entry e : entries) plugin.economy().importBalance(e.id(), e.name(), e.paise());
                plugin.markEssentialsImported();
                plugin.economy().rebuildTop();
                plugin.getLogger().info(actor(s) + " imported " + entries.size() + " Essentials balances.");
                msg.raw(s, "<green>Imported {n} balances.", "n", String.valueOf(entries.size()));
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(SUBS);
        else if (a.length == 2 && a[0].equalsIgnoreCase("import")) out.add("essentials");
        else if (a.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
