package com.arka.smp.economy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The only place balances change. Every mutation:
 *  - validates the amount (positive, within the hard ceiling),
 *  - runs under one global lock, so check-and-change is atomic (no double spending),
 *  - never lets a balance go negative or above the configured maximum,
 *  - is written to the database and the transaction log in the same ordered batch.
 */
public final class EconomyManager {
    public enum Result { OK, INVALID, INSUFFICIENT, FROZEN, TOO_RICH, SAME, NO_ACCOUNT, NOT_ACCEPTING }

    public static final class Account {
        public final UUID id;
        public volatile String name;
        public volatile long balance;
        public volatile boolean frozen;
        public volatile boolean acceptPay = true;
        public volatile boolean hideTop;
        long playSecs;
        long playTotal;          // lifetime ACTIVE play seconds (drives the growing hourly reward)
        int rewardDay;
        int rewardToday;
        final long created;
        volatile long seen;

        Account(UUID id, String name, long balance, long created) {
            this.id = id;
            this.name = name;
            this.balance = balance;
            this.created = created;
            this.seen = created;
        }

        Database.Row row() {
            return new Database.Row(id, name, balance, frozen, acceptPay, hideTop, playSecs, rewardDay,
                    rewardToday, created, seen, playTotal);
        }
    }

    public record TopEntry(UUID id, String name, long balance) {
    }

    private record Top(List<TopEntry> entries, Map<UUID, Integer> rank) {
    }

    private final ArkaSMPEconomy plugin;
    private final Database db;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();
    private volatile Top top = new Top(List.of(), Map.of());

    EconomyManager(ArkaSMPEconomy plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    void load(List<Database.Row> rows) {
        for (Database.Row r : rows) {
            Account a = new Account(r.id(), r.name(), Math.max(0, r.balance()), r.created());
            a.frozen = r.frozen();
            a.acceptPay = r.acceptPay();
            a.hideTop = r.hideTop();
            a.playSecs = r.playSecs();
            a.playTotal = r.playTotal();
            a.rewardDay = r.rewardDay();
            a.rewardToday = r.rewardToday();
            a.seen = r.seen();
            accounts.put(a.id, a);
            byName.put(a.name.toLowerCase(Locale.ROOT), a.id);
        }
        rebuildTop();
    }

    // ---- config helpers ---------------------------------------------------------------------

    public long configMoney(String path, long defaultRupees) {
        Object o = plugin.getConfig().get(path);
        if (o != null) {
            String s = String.valueOf(o);
            if (s.equals("0") || s.equals("0.0")) return 0;
            Long v = Money.parse(s);
            if (v != null) return v;
        }
        return defaultRupees * Money.SCALE;
    }

    public long startBalance() {
        return Math.min(configMoney("economy.start-balance", 1000), Money.MAX);
    }

    public long maxBalance() {
        return Math.min(configMoney("economy.max-balance", 1_000_000_000L), Money.MAX);
    }

    // ---- accounts ---------------------------------------------------------------------------

    public Account get(UUID id) {
        return accounts.get(id);
    }

    public Account findByName(String name) {
        if (name == null) return null;
        UUID id = byName.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : accounts.get(id);
    }

    public long balance(UUID id) {
        Account a = accounts.get(id);
        return a == null ? 0 : a.balance;
    }

    public int accountCount() {
        return accounts.size();
    }

    /** Create the account on first join (with the starting balance) and keep the name index fresh. */
    public Account ensure(UUID id, String name) {
        lock.lock();
        try {
            Account a = accounts.get(id);
            long now = System.currentTimeMillis();
            if (a == null) {
                a = new Account(id, name, startBalance(), now);
                accounts.put(id, a);
                db.logTx("start", null, id, a.balance, "starting balance");
            } else {
                String old = a.name;
                if (old != null && !old.equalsIgnoreCase(name)) byName.remove(old.toLowerCase(Locale.ROOT), id);
                a.name = name;
                a.seen = now;
            }
            byName.put(name.toLowerCase(Locale.ROOT), id);
            db.saveAccount(a.row());
            return a;
        } finally {
            lock.unlock();
        }
    }

    public void save(UUID id) {
        lock.lock();
        try {
            Account a = accounts.get(id);
            if (a != null) db.saveAccount(a.row());
        } finally {
            lock.unlock();
        }
    }

    // ---- balance changes --------------------------------------------------------------------

    private static boolean badAmount(long amt) {
        return amt <= 0 || amt > Money.MAX;
    }

    /** Add money. "admin" lets staff deposit into frozen accounts. */
    public Result deposit(UUID id, long amt, String type, String note, boolean admin) {
        if (badAmount(amt)) return Result.INVALID;
        lock.lock();
        try {
            Account a = accounts.get(id);
            if (a == null) return Result.NO_ACCOUNT;
            if (a.frozen && !admin) return Result.FROZEN;
            long nb = a.balance + amt;                       // both <= 1e14, cannot overflow
            if (nb > maxBalance()) return Result.TOO_RICH;
            a.balance = nb;
            db.saveAccount(a.row());
            db.logTx(type, null, id, amt, note);
            return Result.OK;
        } finally {
            lock.unlock();
        }
    }

    public Result withdraw(UUID id, long amt, String type, String note, boolean admin) {
        if (badAmount(amt)) return Result.INVALID;
        lock.lock();
        try {
            Account a = accounts.get(id);
            if (a == null) return Result.NO_ACCOUNT;
            if (a.frozen && !admin) return Result.FROZEN;
            if (a.balance < amt) return Result.INSUFFICIENT;
            a.balance -= amt;
            db.saveAccount(a.row());
            db.logTx(type, id, null, amt, note);
            return Result.OK;
        } finally {
            lock.unlock();
        }
    }

    /** Admin: set an exact balance (0 allowed). */
    public Result set(UUID id, long amt, String note) {
        if (amt < 0 || amt > maxBalance()) return Result.INVALID;
        lock.lock();
        try {
            Account a = accounts.get(id);
            if (a == null) return Result.NO_ACCOUNT;
            long old = a.balance;
            a.balance = amt;
            db.saveAccount(a.row());
            db.logTx("set", null, id, amt, note + " (was " + old + ")");
            return Result.OK;
        } finally {
            lock.unlock();
        }
    }

    /** Player to player payment. Atomic: either both sides change or nothing does. */
    public Result transfer(UUID from, UUID to, long amt, String type, String note) {
        if (badAmount(amt)) return Result.INVALID;
        if (from.equals(to)) return Result.SAME;
        lock.lock();
        try {
            Account a = accounts.get(from);
            Account b = accounts.get(to);
            if (a == null || b == null) return Result.NO_ACCOUNT;
            if (a.frozen || b.frozen) return Result.FROZEN;
            if (!b.acceptPay && type.equals("pay")) return Result.NOT_ACCEPTING;
            if (a.balance < amt) return Result.INSUFFICIENT;
            long nb = b.balance + amt;
            if (nb > maxBalance()) return Result.TOO_RICH;
            a.balance -= amt;
            b.balance = nb;
            db.saveAccount(a.row());
            db.saveAccount(b.row());
            db.logTx(type, from, to, amt, note);
            return Result.OK;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Settle the money part of a trade in one atomic step: A gives aToB, B gives bToA.
     * All checks run before anything changes.
     */
    public Result settle(UUID a, long aToB, UUID b, long bToA, String note) {
        if (aToB < 0 || bToA < 0 || aToB > Money.MAX || bToA > Money.MAX) return Result.INVALID;
        if (a.equals(b)) return Result.SAME;
        if (aToB == 0 && bToA == 0) return Result.OK;
        lock.lock();
        try {
            Account x = accounts.get(a);
            Account y = accounts.get(b);
            if (x == null || y == null) return Result.NO_ACCOUNT;
            if (x.frozen || y.frozen) return Result.FROZEN;
            if (x.balance < aToB || y.balance < bToA) return Result.INSUFFICIENT;
            long nx = x.balance - aToB + bToA;
            long ny = y.balance - bToA + aToB;
            if (nx > maxBalance() || ny > maxBalance()) return Result.TOO_RICH;
            x.balance = nx;
            y.balance = ny;
            db.saveAccount(x.row());
            db.saveAccount(y.row());
            if (aToB > 0) db.logTx("trade", a, b, aToB, note);
            if (bToA > 0) db.logTx("trade", b, a, bToA, note);
            return Result.OK;
        } finally {
            lock.unlock();
        }
    }

    /** Hourly reward: adds active seconds, pays full intervals. Returns paise paid. */
    public long rewardTick(UUID id, long secs, long intervalSecs, int dailyCap, long baseAmount, double growthPercent,
                           long maxAmount) {
        lock.lock();
        try {
            Account a = accounts.get(id);
            if (a == null || a.frozen || baseAmount <= 0 || intervalSecs <= 0) return 0;
            a.playSecs += secs;
            a.playTotal += secs;
            long paid = 0;
            while (a.playSecs >= intervalSecs) {
                a.playSecs -= intervalSecs;
                // the reward grows with lifetime active hours (see RewardManager.amountFor)
                long amount = RewardManager.amountFor(baseAmount, growthPercent, maxAmount, a.playTotal);
                int day = (int) LocalDate.now().toEpochDay();
                if (a.rewardDay != day) {
                    a.rewardDay = day;
                    a.rewardToday = 0;
                }
                if (dailyCap > 0 && a.rewardToday >= dailyCap) continue;
                long nb = a.balance + amount;
                if (nb > maxBalance()) continue;
                a.balance = nb;
                a.rewardToday++;
                paid += amount;
                db.logTx("reward", null, id, amount, "active play time");
            }
            if (paid > 0) db.saveAccount(a.row());
            return paid;
        } finally {
            lock.unlock();
        }
    }

    public void setFrozen(UUID id, boolean frozen) {
        lock.lock();
        try {
            Account a = accounts.get(id);
            if (a == null) return;
            a.frozen = frozen;
            db.saveAccount(a.row());
            db.logTx(frozen ? "freeze" : "unfreeze", null, id, 0, "admin");
        } finally {
            lock.unlock();
        }
    }

    public boolean toggleAcceptPay(UUID id) {
        lock.lock();
        try {
            Account a = accounts.get(id);
            if (a == null) return true;
            a.acceptPay = !a.acceptPay;
            db.saveAccount(a.row());
            return a.acceptPay;
        } finally {
            lock.unlock();
        }
    }

    public void setHideTop(UUID id, boolean hide) {
        Account a = accounts.get(id);
        if (a != null && a.hideTop != hide) {
            a.hideTop = hide;
            save(id);
        }
    }

    /** Import one balance (Essentials migration). Creates the account if needed. */
    public void importBalance(UUID id, String name, long paise) {
        lock.lock();
        try {
            long value = Math.max(0, Math.min(paise, maxBalance()));
            Account a = accounts.get(id);
            long now = System.currentTimeMillis();
            if (a == null) {
                a = new Account(id, name, value, now);
                accounts.put(id, a);
                byName.putIfAbsent(name.toLowerCase(Locale.ROOT), id);
            } else {
                a.balance = value;
            }
            db.saveAccount(a.row());
            db.logTx("import", null, id, value, "Essentials");
        } finally {
            lock.unlock();
        }
    }

    // ---- baltop -----------------------------------------------------------------------------

    public void rebuildTop() {
        List<TopEntry> all = new ArrayList<>();
        for (Account a : accounts.values()) {
            if (!a.hideTop) all.add(new TopEntry(a.id, a.name, a.balance));
        }
        List<TopEntry> list = ranked(all);
        Map<UUID, Integer> rank = new HashMap<>();
        for (int i = 0; i < list.size(); i++) rank.put(list.get(i).id(), i + 1);
        top = new Top(List.copyOf(list), rank);
    }

    /** Richest first; equal balances by name (ignoring case). The lower-case name is made once per account, not once per comparison. */
    static List<TopEntry> ranked(java.util.Collection<TopEntry> entries) {
        record Keyed(TopEntry entry, String lower) {
        }
        List<Keyed> keyed = new ArrayList<>(entries.size());
        for (TopEntry e : entries) keyed.add(new Keyed(e, e.name() == null ? "" : e.name().toLowerCase(Locale.ROOT)));
        keyed.sort(Comparator.<Keyed>comparingLong(k -> k.entry().balance()).reversed().thenComparing(Keyed::lower));
        List<TopEntry> out = new ArrayList<>(keyed.size());
        for (Keyed k : keyed) out.add(k.entry());
        return out;
    }

    public List<TopEntry> topEntries() {
        return top.entries();
    }

    public int rankOf(UUID id) {
        Integer r = top.rank().get(id);
        return r == null ? 0 : r;
    }
}
