package com.arka.smp.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Pays an hourly reward for ACTIVE play time that grows exponentially with lifetime active hours (up to a cap). AFK players (as decided by the
 * strict AFK system in ArkaSMPCore) earn nothing. Sampled every 10 seconds, so being active only
 * at one predictable moment does not work. Progress is stored per account, so relogging neither
 * loses time nor speeds anything up.
 */
final class RewardManager {
    private static final long SAMPLE_SECONDS = 10;
    private final ArkaSMPEconomy plugin;
    private BukkitTask task;

    RewardManager(ArkaSMPEconomy plugin) {
        this.plugin = plugin;
    }

    void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, SAMPLE_SECONDS * 20L, SAMPLE_SECONDS * 20L);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * The reward for one interval after a player has been active for playTotalSecs in their life. It grows
     * exponentially: base x (1 + growthPercent/100) ^ activeHours, but never above maxAmount (and never below
     * base). With the defaults (100, 3%, cap 1,000) a new player earns 100, after 25 hours 209, after 50 hours 438
     * and the cap of 1,000 from about 78 hours. Everything is in paise. Bad numbers can only give the base amount.
     */
    static long amountFor(long baseAmount, double growthPercent, long maxAmount, long playTotalSecs) {
        if (baseAmount <= 0) return 0;
        long ceiling = Math.max(baseAmount, maxAmount);
        if (!(growthPercent > 0) || Double.isNaN(growthPercent) || Double.isInfinite(growthPercent) || playTotalSecs <= 0) {
            return baseAmount;
        }
        double hours = playTotalSecs / 3600.0;
        double value = baseAmount * Math.pow(1.0 + Math.min(growthPercent, 100.0) / 100.0, hours);
        if (Double.isNaN(value) || value >= ceiling) return ceiling;
        return Math.max(baseAmount, Math.round(value));
    }

    private void tick() {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("rewards.playtime.enabled", true)) return;
        long interval = Math.max(1, cfg.getInt("rewards.playtime.interval-minutes", 60)) * 60L;
        long amount = plugin.economy().configMoney("rewards.playtime.amount", 100);
        double growth = cfg.getDouble("rewards.playtime.growth-percent-per-hour", 3.0);
        long maxAmount = plugin.economy().configMoney("rewards.playtime.max-amount", 1000);
        int cap = cfg.getInt("rewards.playtime.max-per-day", 24);
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (plugin.core().afk().isAfk(id)) continue;
            long paid = plugin.economy().rewardTick(id, SAMPLE_SECONDS, interval, cap, amount, growth, maxAmount);
            if (paid > 0) {
                plugin.messages().send(p, "reward-earned", "amount", plugin.messages().money(paid));
            }
        }
    }
}
