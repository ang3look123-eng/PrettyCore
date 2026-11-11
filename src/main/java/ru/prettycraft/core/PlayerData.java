package ru.prettycraft.core;

import org.bukkit.Location;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerData {
    public static final int MAX_LEVEL = 50;

    public final UUID id;
    public String name = "?";
    public double coins;
    public long lastDaily;
    public int streak;
    public boolean scoreboard = true;
    public final Map<Profession, Long> xp = new EnumMap<>(Profession.class);
    public final Map<String, Location> homes = new LinkedHashMap<>();

    public PlayerData(UUID id) {
        this.id = id;
        for (Profession p : Profession.values()) xp.put(p, 0L);
    }

    public static int levelFor(long xp) {
        return (int) Math.floor(Math.sqrt(xp / 50.0)) + 1;
    }

    public static long xpFor(int level) {
        return 50L * (level - 1) * (level - 1);
    }

    public int level(Profession p) {
        return Math.min(MAX_LEVEL, levelFor(xp.get(p)));
    }

    public Profession top() {
        Profession best = null;
        long max = 0;
        for (Map.Entry<Profession, Long> e : xp.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }
}
