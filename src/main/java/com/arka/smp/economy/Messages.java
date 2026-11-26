package com.arka.smp.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** Config-driven MiniMessage text. Placeholder values are escaped so players cannot inject tags. */
public final class Messages {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final ArkaSMPEconomy plugin;

    public Messages(ArkaSMPEconomy plugin) {
        this.plugin = plugin;
    }

    private static String fill(String raw, String[] kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) raw = raw.replace("{" + kv[i] + "}", MM.escapeTags(kv[i + 1]));
        return raw;
    }

    private String template(String key, String[] kv) {
        String raw = plugin.getConfig().getString("messages." + key);
        return fill(raw == null ? "<red>Missing message: " + key : raw, kv);
    }

    public Component mm(String raw, String... kv) {
        return MM.deserialize(fill(raw, kv));
    }

    public Component plain(String key, String... kv) {
        return MM.deserialize(template(key, kv));
    }

    public Component get(String key, String... kv) {
        return MM.deserialize(plugin.getConfig().getString("messages.prefix", "") + template(key, kv));
    }

    public void send(CommandSender to, String key, String... kv) {
        to.sendMessage(get(key, kv));
    }

    /** Literal MiniMessage with prefix (admin output). */
    public void raw(CommandSender to, String miniMessage, String... kv) {
        to.sendMessage(MM.deserialize(plugin.getConfig().getString("messages.prefix", "") + fill(miniMessage, kv)));
    }

    /**
     * Money as shown everywhere except buy and sell prices: "₹1.25k" (or "₹1,250" when currency.compact is false) with
     * the configured symbol, and no paise ("₹1,250", never "₹1,250.50") unless currency.show-paise is true.
     */
    public String money(long paise) {
        return plugin.getConfig().getBoolean("currency.show-paise", false) ? price(paise) : whole(paise);
    }

    /** The same text, always without paise. This is what the MoneyFormat API and %arkaeco_balance_formatted% give. */
    public String whole(long paise) {
        return text(Money.noPaise(paise));
    }

    /** Shop buy and sell prices and totals: paise are shown when there are any (₹0.15, ₹9.60), so cheap items stay readable. */
    public String price(long paise) {
        return text(paise);
    }

    private String text(long paise) {
        String symbol = plugin.getConfig().getString("currency.symbol", "₹");
        return symbol + (plugin.getConfig().getBoolean("currency.compact", true) ? Money.compact(paise) : Money.plain(paise));
    }

    /** The exact amount, always written out: "₹1,250.50". */
    public String moneyExact(long paise) {
        return plugin.getConfig().getString("currency.symbol", "₹") + Money.plain(paise);
    }
}
