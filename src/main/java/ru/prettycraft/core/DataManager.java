package ru.prettycraft.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DataManager {
    private final PrettyCore plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DataManager(PrettyCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    /** Только главный поток. */
    public PlayerData get(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    /** Безопасно из любого потока, null если игрока нет в кэше. */
    public PlayerData peek(UUID id) {
        return cache.get(id);
    }

    private PlayerData load(UUID id) {
        PlayerData d = new PlayerData(id);
        ConfigurationSection s = yaml.getConfigurationSection("players." + id);
        if (s == null) return d;
        d.name = s.getString("name", "?");
        d.coins = s.getDouble("coins");
        d.lastDaily = s.getLong("last-daily");
        d.streak = s.getInt("streak");
        d.scoreboard = s.getBoolean("scoreboard", true);
        for (Profession p : Profession.values()) d.xp.put(p, s.getLong("xp." + p.name()));
        ConfigurationSection h = s.getConfigurationSection("homes");
        if (h != null) {
            for (String k : h.getKeys(false)) {
                Location l = h.getLocation(k);
                if (l != null) d.homes.put(k, l);
            }
        }
        return d;
    }

    private void write(PlayerData d) {
        String b = "players." + d.id + ".";
        yaml.set(b + "name", d.name);
        yaml.set(b + "coins", d.coins);
        yaml.set(b + "last-daily", d.lastDaily);
        yaml.set(b + "streak", d.streak);
        yaml.set(b + "scoreboard", d.scoreboard);
        for (Profession p : Profession.values()) yaml.set(b + "xp." + p.name(), d.xp.get(p));
        yaml.set(b + "homes", null);
        d.homes.forEach((k, v) -> yaml.set(b + "homes." + k, v));
    }

    private void sync() {
        for (PlayerData d : cache.values()) write(d);
    }

    public void saveAsync() {
        sync();
        String s = yaml.saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeFile(s));
    }

    public void saveSync() {
        sync();
        writeFile(yaml.saveToString());
    }

    private synchronized void writeFile(String s) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Files.writeString(file.toPath(), s, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось сохранить data.yml: " + ex.getMessage());
        }
    }

    public Location spawn() {
        return yaml.getLocation("spawn");
    }

    public void setSpawn(Location l) {
        yaml.set("spawn", l);
        saveAsync();
    }

    public List<Map.Entry<String, Double>> top(int n) {
        sync();
        ConfigurationSection s = yaml.getConfigurationSection("players");
        if (s == null) return List.of();
        List<Map.Entry<String, Double>> l = new ArrayList<>();
        for (String k : s.getKeys(false)) {
            l.add(Map.entry(s.getString(k + ".name", "?"), s.getDouble(k + ".coins")));
        }
        l.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return l.size() > n ? l.subList(0, n) : l;
    }
}
