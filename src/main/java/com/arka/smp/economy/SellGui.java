package com.arka.smp.economy;

import com.arka.smp.core.menu.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /sellgui: a chest you drop items into and sell with one button.
 *
 * Rows 1-5 are free slots, the bottom row is controls. Only the Sell button pays; anything that is not sold
 * (unsellable, over a sell limit) is handed back when the chest closes. All the normal price, limit and
 * play-time rules apply, because the sale goes through the same ShopManager code as /sell.
 */
public final class SellGui implements Listener {
    private static final int FREE = 45;      // slots 0-44 hold the player's items
    private static final int SELL_SLOT = 49;

    private final ArkaSMPEconomy plugin;

    SellGui(ArkaSMPEconomy plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    void open(Player p) {
        var msg = plugin.messages();
        String blocker = plugin.shop().sellBlocker(p);
        if (blocker != null) {
            msg.send(p, blocker, "hours", String.valueOf((long) plugin.shop().minPlayHours()));
            return;
        }
        Holder h = new Holder();
        String title = plugin.getConfig().getString("menus.shop.sellgui-title", "Sell items");
        h.inv = Bukkit.createInventory(h, 54, msg.mm(title));
        ItemStack pane = Items.filler();
        for (int i = FREE; i < 54; i++) h.inv.setItem(i, pane);
        refresh(p, h.inv);
        p.openInventory(h.inv);
    }

    private ShopManager.Plan plan(Player p, Inventory inv) {
        return plugin.shop().plan(p.getUniqueId(), inv.getContents(), s -> s < FREE, null, Integer.MAX_VALUE);
    }

    private void refresh(Player p, Inventory inv) {
        var msg = plugin.messages();
        ShopManager.Plan plan = plan(p, inv);
        List<Component> lore = new ArrayList<>();
        lore.add(msg.mm("<gray>You get: <green>" + msg.price(plan.total()) + " <gray>for <white>" + plan.items() + " <gray>items"));
        int shown = 0;
        for (var e : plan.byMaterial().entrySet()) {
            if (shown++ >= 6) break;
            lore.add(msg.mm("<dark_gray>- <white>" + e.getValue() + "x " + ShopManager.pretty(e.getKey())));
        }
        if (plan.hourLimited()) lore.add(msg.mm("<yellow>Some items are over their hourly limit."));
        if (plan.dayLimited()) lore.add(msg.mm("<yellow>Daily sell limit reached."));
        if (plan.refused() > 0) lore.add(msg.mm("<gray>" + plan.refused() + " stack(s) cannot be sold (enchanted, named or damaged)."));
        lore.add(msg.mm("<gray>Unsold items are returned when you close."));
        inv.setItem(SELL_SLOT, Items.of(plan.total() > 0 ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                msg.mm(plan.total() > 0 ? "<green><bold>SELL <white>" + msg.price(plan.total()) : "<gray><bold>Nothing to sell"), lore));
    }

    private void sell(Player p, Inventory inv) {
        String blocker = plugin.shop().sellBlocker(p);
        if (blocker != null) {
            plugin.messages().send(p, blocker, "hours", String.valueOf((long) plugin.shop().minPlayHours()));
            return;
        }
        ShopManager.Plan plan = plan(p, inv);          // planned and executed in the same tick, so limits are exact
        var msg = plugin.messages();
        var shop = plugin.shop();
        if (plan.total() <= 0) {
            ShopMenus.finishSale(plugin, p, plan, () -> plan, () -> refresh(p, inv));
            return;
        }
        if (!shop.execute(p, inv, plan)) {
            msg.send(p, "shop-sell-failed");
        } else {
            msg.send(p, "shop-sold", "items", String.valueOf(plan.items()), "amount", msg.price(plan.total()));
            if (plan.dayLimited()) msg.send(p, "shop-limit-day", "cap", msg.money(shop.dayRemaining(p.getUniqueId())));
            else if (plan.hourLimited()) msg.send(p, "shop-limit-hour");
        }
        refresh(p, inv);
    }

    private static boolean ours(Inventory inv) {
        return inv != null && inv.getHolder() instanceof Holder;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!ours(top) || !(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClick() == ClickType.DOUBLE_CLICK || e.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);                         // would pull the control icons onto the cursor
            return;
        }
        if (e.getClickedInventory() == top && e.getRawSlot() >= FREE) {
            e.setCancelled(true);
            if (e.getRawSlot() == SELL_SLOT) sell(p, top);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.getOpenInventory().getTopInventory() == top) refresh(p, top);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!ours(top) || !(e.getWhoClicked() instanceof Player p)) return;
        for (int raw : e.getRawSlots()) {
            if (raw >= FREE && raw < 54) {
                e.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.getOpenInventory().getTopInventory() == top) refresh(p, top);
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (ours(top) && e.getPlayer() instanceof Player p) giveBack(p, top);
    }

    /** Hands every item still in the chest back to the player (dropping what does not fit). */
    private void giveBack(Player p, Inventory inv) {
        for (int i = 0; i < FREE; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            inv.setItem(i, null);
            for (ItemStack rest : p.getInventory().addItem(it).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
        }
    }

    void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (ours(top)) {
                giveBack(p, top);
                p.closeInventory();
            }
        }
    }
}
