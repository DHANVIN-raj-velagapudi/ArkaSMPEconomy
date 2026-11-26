package com.arka.smp.economy;

import com.arka.smp.core.ArkaSMPCore;
import com.arka.smp.core.api.MoneyCharger;
import com.arka.smp.core.api.MoneyProvider;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArkaSMPEconomy extends JavaPlugin implements Listener {
    private ArkaSMPCore core;
    private Messages messages;
    private Database db;
    private EconomyManager economy;
    private RewardManager rewards;
    private TradeManager trades;
    private ShopManager shop;
    private SellGui sellGui;
    private EconCommands commands;
    private boolean essentialsImported;
    private boolean started;
    private ErrorLog errors;

    @Override
    public void onEnable() {
        // The error log comes first, so even a failure while starting is written to logs/errors.log.
        errors = ErrorLog.create(this, "com.arka.smp.economy.");
        errors.start();
        try {
            enable();
        } catch (RuntimeException | Error t) {
            errors.error("startup", "ArkaSMPEconomy could not start", t);
            throw t;
        }
    }

    private void enable() {
        // adds options that are new in this version to config.yml and keeps the admin's own values
        try {
            if (!com.arka.smp.core.DataGuard.check(getDataFolder(), getPluginMeta().getVersion(), 1, getLogger()).proceed()) {
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            com.arka.smp.core.ConfigUpdater.update(getDataFolder(), () -> getResource("config.yml"),
                    getPluginMeta().getVersion(), getLogger(), java.util.Set.of());
        } catch (LinkageError e) {
            getLogger().severe("ArkaSMPCore is too old for this ArkaSMPEconomy. Update both plugins to the same version.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        core = JavaPlugin.getPlugin(ArkaSMPCore.class);
        messages = new Messages(this);
        db = new Database(this);
        if (!db.open()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = new EconomyManager(this, db);
        Map<UUID, List<byte[]>> escrow;
        List<Database.Sale> recentSales;
        try {
            economy.load(db.loadAccounts());
            int days = getConfig().getInt("log.keep-days", 90);
            if (days > 0) db.pruneTransactions(System.currentTimeMillis() - days * 86_400_000L);
            essentialsImported = "true".equals(db.meta("essentials_imported"));
            escrow = db.loadEscrow();
            recentSales = db.loadSales(System.currentTimeMillis() - 86_400_000L);
        } catch (SQLException e) {
            getLogger().severe("Could not load economy data: " + e.getMessage());
            db.startWriter();
            db.close();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        db.startWriter();
        started = true;

        trades = new TradeManager(this, escrow);
        shop = new ShopManager(this, recentSales);
        rewards = new RewardManager(this);
        commands = new EconCommands(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(trades, this);
        pm.registerEvents(commands, this);
        sellGui = new SellGui(this);
        pm.registerEvents(sellGui, this);

        for (String name : new String[]{"bal", "pay", "baltop", "paytoggle"}) bind(name, commands, commands);
        bind("trade", trades, trades);
        ShopCommands shopCommands = new ShopCommands(this);
        for (String name : new String[]{"shop", "sell", "sellgui", "worth", "shopadmin"}) bind(name, shopCommands, shopCommands);
        EcoAdmin admin = new EcoAdmin(this);
        bind("arkaeco", admin, admin);

        // Other Arka plugins (the money leaderboard) read balances through this service.
        getServer().getServicesManager().register(MoneyProvider.class,
                id -> Money.toRupees(economy.balance(id)), this, ServicePriority.Normal);

        // ArkaSMPCore charges rupees through this (player warps).
        getServer().getServicesManager().register(MoneyCharger.class, (id, rupees, reason) -> {
            long paise = Math.round(rupees * Money.SCALE);
            if (paise <= 0) return MoneyCharger.Result.OK;
            return switch (economy.withdraw(id, paise, "charge", reason, false)) {
                case OK -> MoneyCharger.Result.OK;
                case INSUFFICIENT -> MoneyCharger.Result.INSUFFICIENT;
                case FROZEN -> MoneyCharger.Result.FROZEN;
                default -> MoneyCharger.Result.ERROR;
            };
        }, this, ServicePriority.Normal);

        rewards.start();
        long topEvery = Math.max(5, getConfig().getInt("baltop.refresh-seconds", 30)) * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, economy::rebuildTop, 40L, topEvery);
        // Save online accounts every 5 minutes (play-time progress); cheap, off the hot path.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) economy.save(p.getUniqueId());
        }, 6000L, 6000L);

        for (Player p : Bukkit.getOnlinePlayers()) setup(p);
        if (pm.getPlugin("PlaceholderAPI") != null) {
            new EcoExpansion(this).register();
            getLogger().info("PlaceholderAPI found: %arkaeco_...% placeholders registered.");
        }
        getLogger().info("ArkaSMPEconomy enabled with " + economy.accountCount() + " accounts.");
    }

    private void bind(String name, org.bukkit.command.CommandExecutor ex, org.bukkit.command.TabCompleter tab) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            // a crash inside a command is logged with its details and the player gets an error number
            cmd.setExecutor(errors.guard(ex, (who, id) -> messages.send(who, "error-internal", "id", id)));
            cmd.setTabCompleter(errors.guard(tab));
        }
    }

    @Override
    public void onDisable() {
        if (!started) {
            if (errors != null) errors.stop();
            return;
        }
        if (trades != null) trades.shutdown();
        if (sellGui != null) sellGui.shutdown();
        if (shop != null) shop.shutdown();      // returns every item held in open trades
        if (rewards != null) rewards.stop();
        for (Player p : Bukkit.getOnlinePlayers()) economy.save(p.getUniqueId());
        getServer().getServicesManager().unregisterAll(this);
        HandlerList.unregisterAll((Listener) this);
        db.close();                                  // flushes every queued write before exit
        errors.stop();
    }

    public void reload() {
        reloadConfig();
        rewards.start();
        trades.loadBlocked();
        shop.reload();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        setup(e.getPlayer());
    }

    private void setup(Player p) {
        economy.ensure(p.getUniqueId(), p.getName());
        economy.setHideTop(p.getUniqueId(), p.hasPermission("arkaeco.baltop.hide"));
        trades.deliverPending(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        economy.save(e.getPlayer().getUniqueId());
    }

    public boolean essentialsImported() { return essentialsImported; }
    public void markEssentialsImported() {
        essentialsImported = true;
        db.setMeta("essentials_imported", "true");
    }

    public ErrorLog errors() { return errors; }
    public ArkaSMPCore core() { return core; }
    public Messages messages() { return messages; }
    public Database db() { return db; }
    public EconomyManager economy() { return economy; }
    public TradeManager trades() { return trades; }
    public ShopManager shop() { return shop; }
    public SellGui sellGui() { return sellGui; }
}
