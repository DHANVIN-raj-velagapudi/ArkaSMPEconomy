package com.arka.smp.economy;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Small YAML file with debounced async saves (used for trade preferences). */
final class YamlStore {
    private final ArkaSMPEconomy plugin;
    private final File file;
    private final YamlConfiguration yaml = new YamlConfiguration();
    private final Object lock = new Object();
    private BukkitTask pending;
    private long sequence;
    private long written;

    YamlStore(ArkaSMPEconomy plugin, String name) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), name);
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().severe("Could not read " + name + ": " + e.getMessage());
                // keep the unreadable file under a unique name: a later save must never overwrite the only copy
                String keep = name + ".broken-" + System.currentTimeMillis();
                try {
                    Files.copy(file.toPath(), file.toPath().resolveSibling(keep), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().severe("A copy of the unreadable file was kept as " + keep
                            + ". Starting with empty " + name + ".");
                } catch (IOException ignored) {
                }
            }
        }
    }

    YamlConfiguration yaml() {
        return yaml;
    }

    void markDirty() {
        if (pending != null) return;
        if (!plugin.isEnabled()) return;   // shutting down: saveNow() writes everything
        pending = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending = null;
            final String data = yaml.saveToString();
            final long id = ++sequence;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data, id));
        }, 100L);
    }

    void saveNow() {
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
        write(yaml.saveToString(), ++sequence);
    }

    private void write(String data, long id) {
        synchronized (lock) {
            if (id < written) return;
            written = id;
            try {
                Files.createDirectories(file.getParentFile().toPath());
                Path tmp = file.toPath().resolveSibling(file.getName() + ".tmp");
                Files.writeString(tmp, data, StandardCharsets.UTF_8);
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save " + file.getName() + ": " + e.getMessage());
            }
        }
    }
}
