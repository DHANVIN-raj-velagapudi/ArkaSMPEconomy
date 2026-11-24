package com.arka.smp.economy;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

/**
 * %arkaeco_balance%            plain number, e.g. 1250.5
 * %arkaeco_balance_formatted%  e.g. ₹1,250.50
 * %arkaeco_rank%               your /baltop position
 * %arkaeco_top_<n>_name%       name at rank n
 * %arkaeco_top_<n>_value%      formatted balance at rank n
 * %arkaeco_top_<n>_raw%        plain number at rank n
 */
final class EcoExpansion extends PlaceholderExpansion {
    private final ArkaSMPEconomy plugin;

    EcoExpansion(ArkaSMPEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "arkaeco";
    }

    @Override
    public String getAuthor() {
        return "Arka";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        try {
            return compute(player, params.toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return "";   // read from other threads (TAB): never throw into the caller
        }
    }

    private String compute(OfflinePlayer player, String p) {
        var eco = plugin.economy();
        if (p.startsWith("top_")) {
            String[] parts = p.split("_");
            if (parts.length != 3) return null;
            int n;
            try {
                n = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            List<EconomyManager.TopEntry> list = eco.topEntries();
            boolean has = n >= 1 && n <= list.size();
            return switch (parts[2]) {
                case "name" -> has ? list.get(n - 1).name() : "-";
                case "value" -> has ? plugin.messages().money(list.get(n - 1).balance()) : "-";
                case "raw" -> has ? String.valueOf(list.get(n - 1).balance() / Money.SCALE) : "0";
                default -> null;
            };
        }
        if (player == null) return "";
        long bal = eco.balance(player.getUniqueId());
        return switch (p) {
            case "balance" -> String.valueOf(Money.toRupees(bal));
            case "balance_formatted" -> plugin.messages().money(bal);
            case "rank" -> {
                int r = eco.rankOf(player.getUniqueId());
                yield r == 0 ? "-" : String.valueOf(r);
            }
            default -> null;
        };
    }
}
