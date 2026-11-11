package ru.prettycraft.core;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

public final class EventManager {
    public enum Type {
        GOLD_RUSH("Золотая лихорадка", Profession.MINER, BossBar.Color.YELLOW),
        TIMBER_FEST("Лесной фестиваль", Profession.LUMBERJACK, BossBar.Color.GREEN),
        HARVEST("Праздник урожая", Profession.FARMER, BossBar.Color.PINK),
        BLOOD_MOON("Кровавая луна", Profession.HUNTER, BossBar.Color.RED);

        public final String title;
        public final Profession profession;
        public final BossBar.Color color;

        Type(String title, Profession profession, BossBar.Color color) {
            this.title = title;
            this.profession = profession;
            this.color = color;
        }
    }

    private final PrettyCore plugin;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
    private Type current;
    private long startedAt, endsAt, nextAt;
    private BukkitTask task;

    public EventManager(PrettyCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        scheduleNext();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        if (current != null) end(false);
    }

    private void scheduleNext() {
        nextAt = System.currentTimeMillis() + plugin.getConfig().getLong("events.interval-minutes", 45) * 60_000L;
    }

    public Type current() { return current; }

    public double multiplier(Profession p) {
        if (current != null && current.profession == p) {
            return plugin.getConfig().getDouble("events.multiplier", 2.0);
        }
        return 1.0;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (current == null) {
            if (!plugin.getConfig().getBoolean("events.enabled", true) || now < nextAt) return;
            if (Bukkit.getOnlinePlayers().size() < plugin.getConfig().getInt("events.min-players", 1)) {
                nextAt = now + 60_000L;
                return;
            }
            Type[] all = Type.values();
            begin(all[ThreadLocalRandom.current().nextInt(all.length)]);
            return;
        }
        if (now >= endsAt) {
            end(true);
            return;
        }
        updateBar(now);
    }

    private void updateBar(long now) {
        long left = Math.max(0, endsAt - now);
        long total = Math.max(1, endsAt - startedAt);
        bar.progress(Math.min(1f, Math.max(0f, (float) left / total)));
        long sec = left / 1000;
        bar.name(plugin.mm(current.profession.color + "<bold>" + current.title + " <gray>• <yellow>x"
                + plugin.fmt(plugin.getConfig().getDouble("events.multiplier", 2.0))
                + " <gray>• " + String.format("%02d:%02d", sec / 60, sec % 60)));
    }

    public void begin(Type t) {
        if (current != null) end(false);
        current = t;
        startedAt = System.currentTimeMillis();
        endsAt = startedAt + plugin.getConfig().getLong("events.duration-minutes", 10) * 60_000L;
        bar.color(t.color);
        updateBar(startedAt);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1f);
        }
        Bukkit.broadcast(plugin.prefixed("<yellow>Началось событие <bold>" + t.title + "<reset><yellow>! "
                + "Профессия " + t.profession.color + t.profession.title
                + "<yellow>: награды и опыт x" + plugin.fmt(plugin.getConfig().getDouble("events.multiplier", 2.0))));
    }

    public void end(boolean announce) {
        if (current == null) return;
        Type old = current;
        current = null;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        if (announce) Bukkit.broadcast(plugin.prefixed("<gray>Событие <white>" + old.title + "<gray> завершилось."));
        scheduleNext();
    }

    public void show(Player p) {
        if (current != null) p.showBossBar(bar);
    }

    public long millisLeft() {
        return current == null ? 0 : Math.max(0, endsAt - System.currentTimeMillis());
    }
}
