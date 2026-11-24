package com.arka.smp.economy;

import com.arka.smp.core.menu.Items;
import com.arka.smp.core.menu.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Safe player-to-player trading.
 *
 * How it stays safe:
 *  - Items never sit on the cursor. Tapping an item in your inventory moves it into your offer;
 *    tapping it in the offer moves it back. Every raw click, drag, hotbar swap or shift-click on the
 *    trade window is cancelled, so the vanilla inventory logic can never duplicate or lose anything.
 *  - Offered items are removed from the player's inventory and copied to the database (escrow) on
 *    every change, so a crash or restart returns them instead of destroying them.
 *  - ANY change resets both "ready" buttons and the countdown, so nobody can swap an item after
 *    the other player agreed.
 *  - The final swap re-checks everything on the main thread in one go: both inventories must have
 *    room, money is settled atomically under the economy lock, and only then are items handed over.
 *  - Closing the window, quitting, dying, a restart or a plugin reload all cancel and return items.
 */
public final class TradeManager implements Listener, CommandExecutor, TabCompleter {
    private static final int SLOTS = 16;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());

    private record Request(UUID from, UUID to, long expires) {
    }

    private final class Holder implements InventoryHolder {
        final Session session;
        final UUID owner;
        Inventory inv;

        Holder(Session session, UUID owner) {
            this.session = session;
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final class Session {
        final String id = UUID.randomUUID().toString();
        final UUID a, b;
        final ItemStack[] offerA = new ItemStack[SLOTS];
        final ItemStack[] offerB = new ItemStack[SLOTS];
        long moneyA, moneyB;
        boolean readyA, readyB, ended;
        boolean warnedA, warnedB;   // already warned that this trade looks lopsided
        int version;
        BukkitTask countdown;
        Holder holderA, holderB;

        Session(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }

        boolean isA(UUID id) { return id.equals(a); }
        UUID other(UUID id) { return isA(id) ? b : a; }
        ItemStack[] offer(UUID id) { return isA(id) ? offerA : offerB; }
        long money(UUID id) { return isA(id) ? moneyA : moneyB; }
        boolean ready(UUID id) { return isA(id) ? readyA : readyB; }
        Holder holder(UUID id) { return isA(id) ? holderA : holderB; }
    }

    private final ArkaSMPEconomy plugin;
    private final Map<UUID, Map<UUID, Request>> requests = new HashMap<>();   // target -> sender -> request
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();       // items waiting after a crash
    private final YamlStore prefs;
    private final Set<Material> blocked = EnumSet.noneOf(Material.class);

    TradeManager(ArkaSMPEconomy plugin, Map<UUID, List<byte[]>> escrow) {
        this.plugin = plugin;
        this.prefs = new YamlStore(plugin, "trade-prefs.yml");
        for (var e : escrow.entrySet()) {
            for (byte[] bytes : e.getValue()) {
                try {
                    pending.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(ItemStack.deserializeBytes(bytes));
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Could not restore an escrowed item for " + e.getKey());
                }
            }
        }
        if (!pending.isEmpty()) {
            plugin.getLogger().warning("Recovered items from interrupted trades for " + pending.size()
                    + " player(s); they get them back when they join.");
        }
        loadBlocked();
    }

    void loadBlocked() {
        blocked.clear();
        for (String s : plugin.getConfig().getStringList("trade.blocked-items")) {
            Material m = Material.matchMaterial(s);
            if (m != null) blocked.add(m);
        }
    }

    // ---- layout -----------------------------------------------------------------------------
    // 6 rows. Columns 0-3 rows 0-3: YOUR offer. Columns 5-8 rows 0-3: THEIR offer (read only).
    // Row 4: money buttons (36-39), money display (40, 42). Row 5: cancel 45, ready 47, status 49, theirs 51.

    private static int ownSlot(int i) { return (i / 4) * 9 + (i % 4); }
    private static int otherSlot(int i) { return (i / 4) * 9 + 5 + (i % 4); }

    private static int ownIndex(int slot) {
        int row = slot / 9, col = slot % 9;
        return (row < 4 && col < 4) ? row * 4 + col : -1;
    }

    private static boolean isOtherArea(int slot) {
        int row = slot / 9, col = slot % 9;
        return row < 4 && col > 4;
    }

    // ---- commands ---------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.messages();
        if (!(sender instanceof Player p)) {
            msg.send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            openPicker(p, 0);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> accept(p, args.length > 1 ? args[1] : null);
            case "deny" -> deny(p, args.length > 1 ? args[1] : null);
            case "cancel" -> cancelCommand(p);
            case "toggle" -> {
                List<String> off = prefs.yaml().getStringList("off");
                String id = p.getUniqueId().toString();
                boolean nowOff = !off.remove(id);
                if (nowOff) off.add(id);
                prefs.yaml().set("off", off);
                prefs.markDirty();
                msg.send(p, nowOff ? "trade-toggle-off" : "trade-toggle-on");
            }
            case "block", "unblock" -> blockCommand(p, args);
            case "history" -> history(p);
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) msg.send(p, "unknown-player");
                else request(p, target);
            }
        }
        return true;
    }

    private boolean isOff(UUID id) {
        return prefs.yaml().getStringList("off").contains(id.toString());
    }

    private boolean blocks(UUID owner, UUID other) {
        return prefs.yaml().getStringList("blocks." + owner).contains(other.toString());
    }

    private void blockCommand(Player p, String[] args) {
        var msg = plugin.messages();
        if (args.length < 2) {
            msg.send(p, "trade-usage");
            return;
        }
        var acc = Bukkit.getPlayerExact(args[1]) != null ? plugin.economy().get(Bukkit.getPlayerExact(args[1]).getUniqueId())
                : plugin.economy().findByName(args[1]);
        if (acc == null) {
            msg.send(p, "unknown-player");
            return;
        }
        String path = "blocks." + p.getUniqueId();
        List<String> list = prefs.yaml().getStringList(path);
        boolean add = args[0].equalsIgnoreCase("block");
        if (add && !list.contains(acc.id.toString())) list.add(acc.id.toString());
        if (!add) list.remove(acc.id.toString());
        prefs.yaml().set(path, list.isEmpty() ? null : list);
        prefs.markDirty();
        msg.send(p, add ? "trade-block-added" : "trade-block-removed", "player", acc.name);
    }

    private void history(Player p) {
        var msg = plugin.messages();
        final UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Database.Tx> rows = plugin.db().recent(id, "trade-items", 10);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (rows.isEmpty()) {
                        msg.send(p, "trade-history-empty");
                        return;
                    }
                    msg.send(p, "trade-history-header");
                    for (Database.Tx t : rows) {
                        msg.send(p, "trade-history-line", "time", TIME.format(Instant.ofEpochMilli(t.ts())),
                                "note", t.note() == null ? "" : t.note());
                    }
                });
            } catch (SQLException e) {
                Bukkit.getScheduler().runTask(plugin, () -> msg.send(p, "trade-history-empty"));
            }
        });
    }

    // ---- requests ---------------------------------------------------------------------------

    private String restriction(Player p, boolean self) {
        if (p.hasPermission("arkaeco.trade.bypass")) return null;
        var cfg = plugin.getConfig();
        var afk = plugin.core().afk();
        if (cfg.getBoolean("trade.block-while-afk", true) && afk.isAfk(p.getUniqueId())) {
            return self ? "trade-afk-you" : "trade-afk-target";
        }
        if (cfg.getBoolean("trade.block-in-combat", true) && afk.inCombat(p.getUniqueId())) {
            return self ? "trade-combat-you" : "trade-combat-target";
        }
        if (cfg.getBoolean("trade.block-creative", true)
                && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
            return "trade-creative";
        }
        return null;
    }

    /** Returns the message key explaining why these two cannot trade right now, or null. */
    private String check(Player a, Player b) {
        if (!plugin.getConfig().getBoolean("trade.enabled", true)) return "trade-disabled";
        if (a.getUniqueId().equals(b.getUniqueId())) return "trade-self";
        if (sessions.containsKey(a.getUniqueId())) return "trade-you-busy";
        if (sessions.containsKey(b.getUniqueId())) return "trade-target-busy";
        if (isOff(b.getUniqueId()) || blocks(b.getUniqueId(), a.getUniqueId())
                || blocks(a.getUniqueId(), b.getUniqueId())) return "trade-unavailable";
        var ea = plugin.economy().get(a.getUniqueId());
        var eb = plugin.economy().get(b.getUniqueId());
        if (ea == null || eb == null || ea.frozen || eb.frozen) return "trade-frozen";
        String r = restriction(a, true);
        return r != null ? r : restriction(b, false);
    }

    private void prune(Map<UUID, Request> map) {
        long now = System.currentTimeMillis();
        map.values().removeIf(r -> r.expires() < now);
    }

    void request(Player from, Player to) {
        var msg = plugin.messages();
        String err = check(from, to);
        if (err != null) {
            msg.send(from, err, "player", to.getName());
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getInt("trade.request-cooldown", 5) * 1000L;
        Long last = lastRequest.get(from.getUniqueId());
        if (last != null && now - last < cooldown) {
            msg.send(from, "trade-cooldown");
            return;
        }
        Map<UUID, Request> map = requests.computeIfAbsent(to.getUniqueId(), k -> new HashMap<>());
        prune(map);
        if (map.containsKey(from.getUniqueId())) {
            msg.send(from, "trade-pending", "player", to.getName());
            return;
        }
        if (map.size() >= 5) {   // a target cannot be flooded with requests
            msg.send(from, "trade-unavailable", "player", to.getName());
            return;
        }
        long timeout = plugin.getConfig().getInt("trade.request-timeout", 60) * 1000L;
        map.put(from.getUniqueId(), new Request(from.getUniqueId(), to.getUniqueId(), now + timeout));
        lastRequest.put(from.getUniqueId(), now);
        msg.send(from, "trade-request-sent", "player", to.getName());
        msg.send(to, "trade-request-received", "player", from.getName());
    }

    private Request findRequest(Player target, String nameOrNull) {
        Map<UUID, Request> map = requests.get(target.getUniqueId());
        if (map == null) return null;
        prune(map);
        if (nameOrNull != null) {
            Player from = Bukkit.getPlayerExact(nameOrNull);
            return from == null ? null : map.get(from.getUniqueId());
        }
        return map.values().stream().max(Comparator.comparingLong(Request::expires)).orElse(null);
    }

    private void removeRequest(Request r) {
        Map<UUID, Request> map = requests.get(r.to());
        if (map != null) {
            map.remove(r.from());
            if (map.isEmpty()) requests.remove(r.to());
        }
    }

    private void accept(Player target, String name) {
        var msg = plugin.messages();
        Request r = findRequest(target, name);
        if (r == null) {
            msg.send(target, "trade-none");
            return;
        }
        removeRequest(r);
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) {
            msg.send(target, "unknown-player");
            return;
        }
        String err = check(from, target);
        if (err != null) {
            msg.send(target, err, "player", from.getName());
            msg.send(from, err, "player", target.getName());
            return;
        }
        startSession(from, target);
    }

    private void deny(Player target, String name) {
        var msg = plugin.messages();
        Request r = findRequest(target, name);
        if (r == null) {
            msg.send(target, "trade-none");
            return;
        }
        removeRequest(r);
        msg.send(target, "trade-denied");
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) msg.send(from, "trade-denied-you", "player", target.getName());
    }

    private void cancelCommand(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            cancel(s, p);
            return;
        }
        boolean any = false;
        for (Map<UUID, Request> map : requests.values()) any |= map.remove(p.getUniqueId()) != null;
        requests.values().removeIf(Map::isEmpty);
        plugin.messages().send(p, any ? "trade-cancelled" : "trade-none");
    }

    private void openPicker(Player viewer, int page) {
        var msg = plugin.messages();
        List<Player> list = new ArrayList<>(Bukkit.getOnlinePlayers());
        list.removeIf(o -> o.getUniqueId().equals(viewer.getUniqueId()));
        list.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int per = 28;
        int pages = Math.max(1, (list.size() + per - 1) / per);
        final int pg = Math.max(0, Math.min(page, pages - 1));
        Menu menu = new Menu(msg.mm(plugin.getConfig().getString("menus.trade.pick-title", "Trade with who?")), 6);
        for (int i = 0; i < per; i++) {
            int idx = pg * per + i;
            if (idx >= list.size()) break;
            Player target = list.get(idx);
            int slot = (1 + i / 7) * 9 + 1 + i % 7;
            menu.set(slot, Items.head(target, msg.mm("<yellow>{n}", "n", target.getName()),
                    List.of(msg.mm("<gray>Click to send a trade request"))), p -> {
                p.closeInventory();
                Player t = Bukkit.getPlayer(target.getUniqueId());
                if (t == null) msg.send(p, "unknown-player");
                else request(p, t);
            });
        }
        if (list.isEmpty()) menu.set(22, Items.of(Material.GLASS_PANE, msg.mm("<gray>Nobody else is online"), null), null);
        if (pg > 0) menu.set(48, Items.of(Material.ARROW, msg.mm("<white>Previous page"), null), p -> openPicker(p, pg - 1));
        if (pg < pages - 1) menu.set(50, Items.of(Material.ARROW, msg.mm("<white>Next page"), null), p -> openPicker(p, pg + 1));
        menu.set(49, Items.of(Material.BARRIER, msg.mm("<white>Close"), null), Player::closeInventory);
        menu.fill(Items.filler());
        menu.open(viewer);
    }

    // ---- session ----------------------------------------------------------------------------

    private void startSession(Player a, Player b) {
        Session s = new Session(a.getUniqueId(), b.getUniqueId());
        s.holderA = new Holder(s, a.getUniqueId());
        s.holderB = new Holder(s, b.getUniqueId());
        var msg = plugin.messages();
        String titleKey = plugin.getConfig().getString("menus.trade.title", "Trade with {player}");
        s.holderA.inv = Bukkit.createInventory(s.holderA, 54, msg.mm(titleKey, "player", b.getName()));
        s.holderB.inv = Bukkit.createInventory(s.holderB, 54, msg.mm(titleKey, "player", a.getName()));
        sessions.put(s.a, s);
        sessions.put(s.b, s);
        render(s);
        a.openInventory(s.holderA.inv);
        b.openInventory(s.holderB.inv);
    }

    private ItemStack button(Material m, String key, String... kv) {
        var msg = plugin.messages();
        String name = plugin.getConfig().getString("menus.trade." + key, key);
        String lore = plugin.getConfig().getString("menus.trade." + key + "-lore");
        List<Component> lines = null;
        if (lore != null) {
            lines = new ArrayList<>();
            for (String line : lore.split("\\|")) lines.add(msg.mm(line, kv));
        }
        return Items.of(m, msg.mm(name, kv), lines);
    }

    private void render(Session s) {
        renderFor(s, s.a);
        renderFor(s, s.b);
    }

    private void renderFor(Session s, UUID who) {
        Player p = Bukkit.getPlayer(who);
        Holder h = s.holder(who);
        if (p == null || h == null || h.inv == null) return;
        var msg = plugin.messages();
        Inventory inv = h.inv;
        inv.clear();
        UUID otherId = s.other(who);
        Player other = Bukkit.getPlayer(otherId);
        String otherName = other != null ? other.getName() : "?";
        ItemStack[] mine = s.offer(who);
        ItemStack[] theirs = s.offer(otherId);

        ItemStack filler = Items.filler();
        for (int slot = 0; slot < 54; slot++) {
            if (ownIndex(slot) < 0 && !isOtherArea(slot)) inv.setItem(slot, filler);
        }
        for (int i = 0; i < SLOTS; i++) {
            if (mine[i] != null) inv.setItem(ownSlot(i), mine[i].clone());
            if (theirs[i] != null) inv.setItem(otherSlot(i), theirs[i].clone());   // exact copy: what you see is what you get
        }

        List<Integer> steps = plugin.getConfig().getIntegerList("trade.money-steps");
        long small = (steps.size() > 0 ? Math.max(1, steps.get(0)) : 100) * Money.SCALE;
        long big = (steps.size() > 1 ? Math.max(1, steps.get(1)) : 1000) * Money.SCALE;
        inv.setItem(36, button(Material.RED_CONCRETE, "money-minus", "amount", msg.money(big)));
        inv.setItem(37, button(Material.ORANGE_CONCRETE, "money-minus", "amount", msg.money(small)));
        inv.setItem(38, button(Material.LIME_CONCRETE, "money-plus", "amount", msg.money(small)));
        inv.setItem(39, button(Material.GREEN_CONCRETE, "money-plus", "amount", msg.money(big)));
        inv.setItem(40, button(Material.GOLD_INGOT, "my-money", "amount", msg.money(s.money(who))));
        inv.setItem(42, button(Material.GOLD_NUGGET, "their-money", "amount", msg.money(s.money(otherId)), "player", otherName));
        inv.setItem(45, button(Material.BARRIER, "cancel"));
        boolean myReady = s.ready(who);
        boolean theirReady = s.ready(otherId);
        inv.setItem(47, button(myReady ? Material.YELLOW_CONCRETE : Material.LIME_CONCRETE, myReady ? "ready-on" : "ready-off"));
        inv.setItem(49, button(Material.PAPER, "status"));
        inv.setItem(51, button(theirReady ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                theirReady ? "their-ready-on" : "their-ready-off", "player", otherName));
        p.updateInventory();
    }

    private boolean emptyTrade(Session s) {
        if (s.moneyA > 0 || s.moneyB > 0) return false;
        for (int i = 0; i < SLOTS; i++) if (s.offerA[i] != null || s.offerB[i] != null) return false;
        return true;
    }

    private void resetReady(Session s) {
        s.readyA = false;
        s.readyB = false;
        if (s.countdown != null) {
            s.countdown.cancel();
            s.countdown = null;
        }
    }

    /** Called after ANY change to an offer: resets both ready buttons, saves escrow, redraws. */
    private void changed(Session s, UUID changedBy) {
        boolean hadReady = s.readyA || s.readyB;
        s.warnedA = false;
        s.warnedB = false;
        s.version++;
        resetReady(s);
        persistEscrow(s, changedBy);
        render(s);
        if (hadReady) {
            for (UUID id : new UUID[]{s.a, s.b}) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.sendActionBar(plugin.messages().plain("trade-reset"));
            }
        }
    }

    private void persistEscrow(Session s, UUID owner) {
        List<byte[]> bytes = new ArrayList<>();
        for (ItemStack it : s.offer(owner)) if (it != null) bytes.add(it.serializeAsBytes());
        plugin.db().saveEscrow(s.id, owner, bytes);
    }

    // ---- clicks -----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Holder h)) return;
        e.setCancelled(true);                         // nothing is ever left to vanilla inventory logic
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Session s = h.session;
        if (s.ended || !p.getUniqueId().equals(h.owner)) return;
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;
        if (clicked == e.getView().getTopInventory()) {
            handleTop(p, s, e.getRawSlot());
        } else if (clicked.getType() == org.bukkit.event.inventory.InventoryType.PLAYER) {
            handleBottom(p, s, e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Holder) e.setCancelled(true);
    }

    private void handleBottom(Player p, Session s, InventoryClickEvent e) {
        var msg = plugin.messages();
        ItemStack current = e.getCurrentItem();
        if (current == null || current.getType().isAir()) return;
        if (blocked.contains(current.getType())) {
            msg.send(p, "trade-item-blocked");
            return;
        }
        ItemStack[] offer = s.offer(p.getUniqueId());
        int want = e.isRightClick() ? 1 : current.getAmount();
        int remaining = want;
        for (int i = 0; i < SLOTS && remaining > 0; i++) {            // top up matching stacks first
            ItemStack o = offer[i];
            if (o != null && o.isSimilar(current) && o.getAmount() < o.getMaxStackSize()) {
                int add = Math.min(remaining, o.getMaxStackSize() - o.getAmount());
                o.setAmount(o.getAmount() + add);
                remaining -= add;
            }
        }
        for (int i = 0; i < SLOTS && remaining > 0; i++) {            // then empty slots
            if (offer[i] == null) {
                int add = Math.min(remaining, current.getMaxStackSize());
                ItemStack n = current.clone();
                n.setAmount(add);
                offer[i] = n;
                remaining -= add;
            }
        }
        int moved = want - remaining;
        if (moved <= 0) {
            msg.send(p, "trade-offer-full");
            return;
        }
        int left = current.getAmount() - moved;
        Inventory inv = e.getClickedInventory();
        if (left <= 0) {
            inv.setItem(e.getSlot(), null);
        } else {
            ItemStack rest = current.clone();
            rest.setAmount(left);
            inv.setItem(e.getSlot(), rest);
        }
        changed(s, p.getUniqueId());
    }

    private void handleTop(Player p, Session s, int slot) {
        var msg = plugin.messages();
        UUID me = p.getUniqueId();
        int idx = ownIndex(slot);
        if (idx >= 0) {                                                 // take an offered item back
            ItemStack[] offer = s.offer(me);
            ItemStack it = offer[idx];
            if (it == null) return;
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(it.clone());
            if (leftover.isEmpty()) {
                offer[idx] = null;
            } else {
                int rem = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                if (rem >= it.getAmount()) {
                    msg.send(p, "trade-inv-full");
                    return;
                }
                it.setAmount(rem);
            }
            changed(s, me);
            return;
        }
        switch (slot) {
            case 36, 37, 38, 39 -> changeMoney(p, s, slot);
            case 45 -> cancel(s, p);
            case 47 -> toggleReady(p, s);
            default -> {
            }
        }
    }

    private void changeMoney(Player p, Session s, int slot) {
        List<Integer> steps = plugin.getConfig().getIntegerList("trade.money-steps");
        long small = (steps.size() > 0 ? Math.max(1, steps.get(0)) : 100) * Money.SCALE;
        long big = (steps.size() > 1 ? Math.max(1, steps.get(1)) : 1000) * Money.SCALE;
        long delta = switch (slot) {
            case 36 -> -big;
            case 37 -> -small;
            case 38 -> small;
            default -> big;
        };
        UUID me = p.getUniqueId();
        long current = s.money(me);
        long cap = Math.min(plugin.economy().balance(me), plugin.economy().maxBalance());
        long next = Math.max(0, Math.min(current + delta, cap));
        if (delta > 0 && current + delta > cap) plugin.messages().send(p, "trade-money-limit");
        if (next == current) return;
        if (s.isA(me)) s.moneyA = next;
        else s.moneyB = next;
        changed(s, me);
    }

    private void toggleReady(Player p, Session s) {
        UUID me = p.getUniqueId();
        boolean now = !s.ready(me);
        if (now && emptyTrade(s)) {
            plugin.messages().send(p, "trade-empty");
            return;
        }
        if (now && lopsided(p, s)) {
            render(s);
            return;
        }
        if (s.isA(me)) s.readyA = now;
        else s.readyB = now;
        s.version++;
        if (!now && s.countdown != null) {
            s.countdown.cancel();
            s.countdown = null;
        }
        render(s);
        if (s.readyA && s.readyB) startCountdown(s);
    }

    private long value(Session s, UUID id) {
        long total = s.money(id);
        var shop = plugin.shop();
        for (ItemStack it : s.offer(id)) if (it != null) total += shop.valueOf(it);
        return total;
    }

    /**
     * Warns a player once when they are giving away much more (by shop sell price) than they receive.
     * Pressing Ready again confirms. Changing anything resets the warning.
     */
    private boolean lopsided(Player p, Session s) {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("trade.lopsided-warning.enabled", true) || plugin.shop() == null) return false;
        UUID me = p.getUniqueId();
        if (s.isA(me) ? s.warnedA : s.warnedB) return false;           // second press = they confirmed
        long give = value(s, me);
        long get = value(s, s.other(me));
        long minDiff = plugin.economy().configMoney("trade.lopsided-warning.min-difference", 500);
        double ratio = Math.max(1.0, cfg.getDouble("trade.lopsided-warning.ratio", 3.0));
        if (give - get < minDiff) return false;
        if (get > 0 && give < get * ratio) return false;
        if (s.isA(me)) s.warnedA = true;
        else s.warnedB = true;
        plugin.messages().send(p, "trade-lopsided", "give", plugin.messages().money(give), "get", plugin.messages().money(get));
        return true;
    }

    private void startCountdown(Session s) {
        final int[] left = {Math.max(1, plugin.getConfig().getInt("trade.countdown", 3))};
        final int ver = s.version;
        s.countdown = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (s.ended || s.version != ver || !(s.readyA && s.readyB)) {
                if (s.countdown != null) s.countdown.cancel();
                s.countdown = null;
                return;
            }
            if (left[0] <= 0) {
                s.countdown.cancel();
                s.countdown = null;
                execute(s);
                return;
            }
            for (UUID id : new UUID[]{s.a, s.b}) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.sendActionBar(plugin.messages().plain("trade-countdown", "seconds", String.valueOf(left[0])));
            }
            left[0]--;
        }, 0L, 20L);
    }

    // ---- finishing --------------------------------------------------------------------------

    private boolean fits(Player p, ItemStack[] incoming) {
        Inventory tmp = Bukkit.createInventory(null, 36);
        tmp.setContents(p.getInventory().getStorageContents());
        for (ItemStack it : incoming) {
            if (it != null && !tmp.addItem(it.clone()).isEmpty()) return false;
        }
        return true;
    }

    private void give(Player p, ItemStack[] items) {
        for (ItemStack it : items) {
            if (it == null) continue;
            for (ItemStack left : p.getInventory().addItem(it).values()) {
                p.getWorld().dropItem(p.getLocation(), left);
            }
        }
    }

    private void fail(Session s, String key, String who) {
        resetReady(s);
        for (UUID id : new UUID[]{s.a, s.b}) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.messages().send(p, key, "player", who);
        }
        render(s);
    }

    private void execute(Session s) {
        if (s.ended || !(s.readyA && s.readyB)) return;
        Player pa = Bukkit.getPlayer(s.a);
        Player pb = Bukkit.getPlayer(s.b);
        if (pa == null || pb == null) {
            cancel(s, null);
            return;
        }
        // Everything below runs in one tick on the main thread: nothing can change in between.
        if (!fits(pa, s.offerB)) {
            fail(s, "trade-failed-space", pa.getName());
            return;
        }
        if (!fits(pb, s.offerA)) {
            fail(s, "trade-failed-space", pb.getName());
            return;
        }
        var result = plugin.economy().settle(s.a, s.moneyA, s.b, s.moneyB, "trade " + s.id);
        if (result != EconomyManager.Result.OK) {
            fail(s, "trade-failed-money", result.name().toLowerCase(Locale.ROOT));
            return;
        }
        // Point of no return: escrow rows are removed (and flushed) BEFORE items change hands,
        // so a crash can never hand the same items out twice.
        s.ended = true;
        plugin.db().deleteEscrowSession(s.id);
        plugin.db().flush();
        String note = (pa.getName() + " gave [" + describe(s.offerA, s.moneyA) + "] / " + pb.getName() + " gave ["
                + describe(s.offerB, s.moneyB) + "]");
        give(pa, s.offerB);
        give(pb, s.offerA);
        plugin.db().logTx("trade-items", s.a, s.b, 0, note.length() > 600 ? note.substring(0, 600) : note);
        sessions.remove(s.a);
        sessions.remove(s.b);
        for (Player p : new Player[]{pa, pb}) {
            p.closeInventory();
            plugin.messages().send(p, "trade-complete");
        }
    }

    private String describe(ItemStack[] items, long money) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack it : items) {
            if (it == null) continue;
            String key = it.getType().name();
            if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
                key += " \"" + PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().displayName()) + "\"";
            }
            counts.merge(key, it.getAmount(), Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        counts.forEach((k, v) -> sb.append(v).append("x ").append(k).append(", "));
        if (money > 0) sb.append(Money.plain(money)).append(" rupees");
        String out = sb.toString().replaceAll(", $", "");
        return out.isEmpty() ? "nothing" : out;
    }

    /** Cancel a session and give every offered item back to its owner. */
    private void cancel(Session s, Player by) {
        if (s.ended) return;
        s.ended = true;
        if (s.countdown != null) {
            s.countdown.cancel();
            s.countdown = null;
        }
        plugin.db().deleteEscrowSession(s.id);
        plugin.db().flush();
        sessions.remove(s.a);
        sessions.remove(s.b);
        for (UUID id : new UUID[]{s.a, s.b}) {
            Player p = Bukkit.getPlayer(id);
            ItemStack[] items = s.offer(id);
            if (p == null) {
                stash(id, items);
                continue;
            }
            give(p, items);
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Holder) p.closeInventory();
            if (by != null && !by.getUniqueId().equals(id)) {
                plugin.messages().send(p, "trade-cancelled-by", "player", by.getName());
            } else {
                plugin.messages().send(p, "trade-cancelled");
            }
        }
    }

    /** Owner is offline (should not happen): keep the items on disk until they join. */
    private void stash(UUID id, ItemStack[] items) {
        List<ItemStack> list = new ArrayList<>();
        List<byte[]> bytes = new ArrayList<>();
        for (ItemStack it : items) {
            if (it == null) continue;
            list.add(it);
            bytes.add(it.serializeAsBytes());
        }
        if (list.isEmpty()) return;
        pending.computeIfAbsent(id, k -> new ArrayList<>()).addAll(list);
        plugin.db().saveEscrow("returns-" + id, id, bytes);
    }

    void deliverPending(Player p) {
        List<ItemStack> items = pending.remove(p.getUniqueId());
        if (items == null) return;
        plugin.db().deleteEscrowOwner(p.getUniqueId());
        plugin.db().flush();
        give(p, items.toArray(new ItemStack[0]));
        plugin.messages().send(p, "trade-returned");
    }

    void shutdown() {
        for (Session s : new HashSet<>(sessions.values())) cancel(s, null);
        prefs.saveNow();
    }

    // ---- lifecycle events -------------------------------------------------------------------

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof Holder h && !h.session.ended
                && e.getPlayer() instanceof Player p) {
            cancel(h.session, p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        if (s != null) cancel(s, p);
        requests.remove(p.getUniqueId());
        for (Map<UUID, Request> map : requests.values()) map.remove(p.getUniqueId());
        requests.values().removeIf(Map::isEmpty);
        lastRequest.remove(p.getUniqueId());
    }

    /** Items offered by a player who dies drop with the rest of their loot (or stay if keepInventory). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.ended) return;
        if (!e.getKeepInventory()) {
            ItemStack[] mine = s.offer(p.getUniqueId());
            for (int i = 0; i < SLOTS; i++) {
                if (mine[i] != null) {
                    e.getDrops().add(mine[i]);
                    mine[i] = null;
                }
            }
        }
        cancel(s, p);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("accept", "deny", "cancel", "toggle", "block", "unblock", "history"));
            Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
        } else if (args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
