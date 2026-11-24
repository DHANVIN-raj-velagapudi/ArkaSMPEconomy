package com.arka.smp.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Pays the same hourly reward to everyone for ACTIVE play time. AFK players (as decided by the
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

    private void tick() {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("rewards.playtime.enabled", true)) return;
        long interval = Math.max(1, cfg.getInt("rewards.playtime.interval-minutes", 60)) * 60L;
        long amount = plugin.economy().configMoney("rewards.playtime.amount", 100);
        int cap = cfg.getInt("rewards.playtime.max-per-day", 24);
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (plugin.core().afk().isAfk(id)) continue;
            long paid = plugin.economy().rewardTick(id, SAMPLE_SECONDS, interval, cap, amount);
            if (paid > 0) {
                plugin.messages().send(p, "reward-earned", "amount", plugin.messages().money(paid));
            }
        }
    }
}
