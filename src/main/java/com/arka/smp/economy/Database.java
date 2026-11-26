package com.arka.smp.economy;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * SQLite storage. All writes go through one background thread in order, in batched transactions,
 * so the main thread never waits for disk and writes can never be reordered.
 */
final class Database {
    interface Task {
        void run(Connection c) throws SQLException;
    }

    record Row(UUID id, String name, long balance, boolean frozen, boolean acceptPay, boolean hideTop,
               long playSecs, int rewardDay, int rewardToday, long created, long seen, long playTotal) {
    }

    record Sale(UUID id, String item, long ts, long paise) {
    }

    record Tx(long ts, String type, String from, String to, long amount, String note) {
    }

    /** Database format. Raise it (and add an upgrade step in open()) when a release changes the tables. */
    static final int SCHEMA_VERSION = 2;

    private static final Task STOP = c -> {
    };

    private final ArkaSMPEconomy plugin;
    private final File file;
    private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
    private Connection conn;
    private Thread writer;

    Database(ArkaSMPEconomy plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.db");
    }

    boolean open() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("SQLite driver not found on this server.");
            return false;
        }
        try {
            plugin.getDataFolder().mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement st = conn.createStatement()) {
                int have = 0;
                try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                    if (rs.next()) have = rs.getInt(1);
                }
                if (have > SCHEMA_VERSION) {
                    plugin.getLogger().severe("economy.db was written by a newer ArkaSMPEconomy (database format " + have
                            + ", this jar understands " + SCHEMA_VERSION + "). To protect your balances this plugin will NOT "
                            + "start. Install the newer jar again, or restore economy.db from data-backups/.");
                    conn.close();
                    return false;
                }
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA busy_timeout=5000");
                st.execute("CREATE TABLE IF NOT EXISTS accounts(uuid TEXT PRIMARY KEY, name TEXT NOT NULL,"
                        + " balance INTEGER NOT NULL CHECK(balance>=0), frozen INTEGER NOT NULL DEFAULT 0,"
                        + " accept_pay INTEGER NOT NULL DEFAULT 1, hide_top INTEGER NOT NULL DEFAULT 0,"
                        + " play_secs INTEGER NOT NULL DEFAULT 0, reward_day INTEGER NOT NULL DEFAULT 0,"
                        + " reward_today INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, seen INTEGER NOT NULL,"
                        + " play_total INTEGER NOT NULL DEFAULT 0)");
                st.execute("CREATE TABLE IF NOT EXISTS transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + " ts INTEGER NOT NULL, type TEXT NOT NULL, from_uuid TEXT, to_uuid TEXT,"
                        + " amount INTEGER NOT NULL, note TEXT)");
                st.execute("CREATE INDEX IF NOT EXISTS tx_from ON transactions(from_uuid)");
                st.execute("CREATE INDEX IF NOT EXISTS tx_to ON transactions(to_uuid)");
                st.execute("CREATE INDEX IF NOT EXISTS tx_ts ON transactions(ts)");
                st.execute("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT)");
                st.execute("CREATE TABLE IF NOT EXISTS sales(uuid TEXT NOT NULL, item TEXT NOT NULL,"
                        + " ts INTEGER NOT NULL, paise INTEGER NOT NULL)");
                st.execute("CREATE INDEX IF NOT EXISTS sales_ts ON sales(ts)");
                st.execute("CREATE TABLE IF NOT EXISTS escrow(session TEXT NOT NULL, owner TEXT NOT NULL,"
                        + " slot INTEGER NOT NULL, item BLOB NOT NULL)");
                // Upgrade steps, keyed on 'have' (the database format that was found on disk).
                if (have < 2) {
                    // 1 -> 2: lifetime active play seconds, used by the growing hourly reward. A brand new database
                    // already has the column (see CREATE TABLE above), so adding it may fail: that is fine.
                    boolean added = true;
                    try {
                        st.execute("ALTER TABLE accounts ADD COLUMN play_total INTEGER NOT NULL DEFAULT 0");
                    } catch (SQLException alreadyThere) {
                        added = false;
                    }
                    // players who already earned rewards start from the number of rewards they were paid (one hour each)
                    if (added) {
                        st.execute("UPDATE accounts SET play_total = 3600 * (SELECT COUNT(*) FROM transactions"
                                + " WHERE type='reward' AND to_uuid = accounts.uuid)");
                    }
                }
                if (have < SCHEMA_VERSION) st.execute("PRAGMA user_version=" + SCHEMA_VERSION);
            }
            conn.setAutoCommit(false);
            conn.commit();
            return true;
        } catch (SQLException e) {
            plugin.errors().error("database.open", "Could not open the economy database", e);
            return false;
        }
    }

    void startWriter() {
        writer = new Thread(() -> {
            boolean stop = false;
            try {
                while (!stop) {
                    List<Task> batch = new ArrayList<>();
                    batch.add(queue.take());
                    queue.drainTo(batch, 199);
                    for (Task t : batch) {
                        if (t == STOP) {
                            stop = true;
                            break;
                        }
                        try {
                            t.run(conn);
                        } catch (SQLException | RuntimeException e) {
                            // one bad task must never kill the writer: every later balance change would be lost
                            plugin.errors().error("database.write", "A database write failed (a balance or shop change may be lost)", e);
                        }
                    }
                    try {
                        conn.commit();
                    } catch (SQLException e) {
                        plugin.errors().error("database.commit", "Database commit failed (recent changes may be lost)", e);
                    }
                }
            } catch (InterruptedException ignored) {
            } finally {
                try {
                    conn.commit();
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }, "ArkaEconomy-DB");
        writer.setDaemon(false);
        writer.start();
    }

    void submit(Task t) {
        queue.add(t);
    }

    /** Wait until everything queued so far is on disk (used right before irreversible steps). */
    void flush() {
        CountDownLatch latch = new CountDownLatch(1);
        submit(c -> latch.countDown());
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Backup support: commits, folds the write-ahead log into economy.db and (while hold is true) stops
     * automatic checkpoints, so a file copy of economy.db is complete and consistent.
     */
    void checkpoint(boolean hold) {
        CountDownLatch latch = new CountDownLatch(1);
        submit(c -> {
            try {
                c.commit();
                try (Statement st = c.createStatement()) {
                    st.execute("PRAGMA wal_autocheckpoint=" + (hold ? 0 : 1000));
                    if (hold) st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    void close() {
        submit(STOP);
        try {
            if (writer != null) writer.join(20_000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- loading (main thread, before the writer starts) ------------------------------------

    List<Row> loadAccounts() throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid,name,balance,frozen,accept_pay,hide_top,play_secs,"
                     + "reward_day,reward_today,created,seen,play_total FROM accounts")) {
            while (rs.next()) {
                try {
                    rows.add(new Row(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3),
                            rs.getInt(4) != 0, rs.getInt(5) != 0, rs.getInt(6) != 0, rs.getLong(7), rs.getInt(8),
                            rs.getInt(9), rs.getLong(10), rs.getLong(11), rs.getLong(12)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return rows;
    }

    Map<UUID, List<byte[]>> loadEscrow() throws SQLException {
        Map<UUID, List<byte[]>> map = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT owner,item FROM escrow")) {
            while (rs.next()) {
                try {
                    map.computeIfAbsent(UUID.fromString(rs.getString(1)), k -> new ArrayList<>()).add(rs.getBytes(2));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return map;
    }

    String meta(String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT v FROM meta WHERE k=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Sales of the last 24 hours, used to rebuild the sell limits after a restart (restarts must not reset them). */
    List<Sale> loadSales(long sinceMillis) throws SQLException {
        List<Sale> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT uuid,item,ts,paise FROM sales WHERE ts >= ? ORDER BY ts")) {
            ps.setLong(1, sinceMillis);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        out.add(new Sale(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3), rs.getLong(4)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sales WHERE ts < ?")) {
            ps.setLong(1, sinceMillis);
            ps.executeUpdate();
        }
        conn.commit();
        return out;
    }

    void logSale(UUID id, String item, long ts, long paise) {
        submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO sales(uuid,item,ts,paise) VALUES(?,?,?,?)")) {
                ps.setString(1, id.toString());
                ps.setString(2, item);
                ps.setLong(3, ts);
                ps.setLong(4, paise);
                ps.executeUpdate();
            }
        });
    }

    void pruneTransactions(long olderThanMillis) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM transactions WHERE ts < ?")) {
            ps.setLong(1, olderThanMillis);
            ps.executeUpdate();
        }
        conn.commit();
    }

    // ---- queued writes ----------------------------------------------------------------------

    void saveAccount(Row r) {
        submit(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO accounts(uuid,name,balance,frozen,accept_pay,hide_top,play_secs,reward_day,"
                            + "reward_today,created,seen,play_total) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET "
                            + "name=excluded.name,balance=excluded.balance,frozen=excluded.frozen,"
                            + "accept_pay=excluded.accept_pay,hide_top=excluded.hide_top,play_secs=excluded.play_secs,"
                            + "reward_day=excluded.reward_day,reward_today=excluded.reward_today,seen=excluded.seen,"
                            + "play_total=excluded.play_total")) {
                ps.setString(1, r.id().toString());
                ps.setString(2, r.name());
                ps.setLong(3, r.balance());
                ps.setInt(4, r.frozen() ? 1 : 0);
                ps.setInt(5, r.acceptPay() ? 1 : 0);
                ps.setInt(6, r.hideTop() ? 1 : 0);
                ps.setLong(7, r.playSecs());
                ps.setInt(8, r.rewardDay());
                ps.setInt(9, r.rewardToday());
                ps.setLong(10, r.created());
                ps.setLong(11, r.seen());
                ps.setLong(12, r.playTotal());
                ps.executeUpdate();
            }
        });
    }

    void logTx(String type, UUID from, UUID to, long amount, String note) {
        final long ts = System.currentTimeMillis();
        submit(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO transactions(ts,type,from_uuid,to_uuid,amount,note) VALUES(?,?,?,?,?,?)")) {
                ps.setLong(1, ts);
                ps.setString(2, type);
                ps.setString(3, from == null ? null : from.toString());
                ps.setString(4, to == null ? null : to.toString());
                ps.setLong(5, amount);
                ps.setString(6, note);
                ps.executeUpdate();
            }
        });
    }

    void setMeta(String key, String value) {
        submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO meta(k,v) VALUES(?,?) "
                    + "ON CONFLICT(k) DO UPDATE SET v=excluded.v")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            }
        });
    }

    void saveEscrow(String session, UUID owner, List<byte[]> items) {
        submit(c -> {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM escrow WHERE session=? AND owner=?")) {
                del.setString(1, session);
                del.setString(2, owner.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO escrow(session,owner,slot,item) VALUES(?,?,?,?)")) {
                int slot = 0;
                for (byte[] item : items) {
                    ins.setString(1, session);
                    ins.setString(2, owner.toString());
                    ins.setInt(3, slot++);
                    ins.setBytes(4, item);
                    ins.executeUpdate();
                }
            }
        });
    }

    void deleteEscrowSession(String session) {
        submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM escrow WHERE session=?")) {
                ps.setString(1, session);
                ps.executeUpdate();
            }
        });
    }

    void deleteEscrowOwner(UUID owner) {
        submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM escrow WHERE owner=?")) {
                ps.setString(1, owner.toString());
                ps.executeUpdate();
            }
        });
    }

    // ---- reads for commands (own short-lived connection, safe beside the writer in WAL mode) ----

    List<Tx> recent(UUID who, String typeOrNull, int limit) throws SQLException {
        List<Tx> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {
            StringBuilder sql = new StringBuilder("SELECT ts,type,from_uuid,to_uuid,amount,note FROM transactions WHERE 1=1");
            if (who != null) sql.append(" AND (from_uuid=? OR to_uuid=?)");
            if (typeOrNull != null) sql.append(" AND type=?");
            sql.append(" ORDER BY id DESC LIMIT ?");
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                int i = 1;
                if (who != null) {
                    ps.setString(i++, who.toString());
                    ps.setString(i++, who.toString());
                }
                if (typeOrNull != null) ps.setString(i++, typeOrNull);
                ps.setInt(i, Math.max(1, Math.min(limit, 100)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Tx(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                                rs.getLong(5), rs.getString(6)));
                    }
                }
            }
        }
        return out;
    }
}
