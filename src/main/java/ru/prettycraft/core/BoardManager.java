package ru.prettycraft.core;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BoardManager {
    private final PrettyCore plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private BukkitTask task;

    public BoardManager(PrettyCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> Bukkit.getOnlinePlayers().forEach(this::update), 20L, 40L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        boards.clear();
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
    }

    private Scoreboard create() {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("pc", Criteria.DUMMY,
                plugin.mm("<gradient:#ff6ec7:#ffb86c><bold>PRETTYCRAFT</bold></gradient>"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.numberFormat(NumberFormat.blank());
        return sb;
    }

    public void update(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        UUID id = p.getUniqueId();
        if (!d.scoreboard) {
            if (boards.remove(id) != null) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return;
        }
        Scoreboard sb = boards.computeIfAbsent(id, k -> create());
        if (p.getScoreboard() != sb) p.setScoreboard(sb);
        Objective o = sb.getObjective("pc");
        if (o == null) return;

        Profession top = d.top();
        EventManager.Type ev = plugin.events().current();
        List<String> lines = List.of(
                "<dark_gray>━━━━━━━━━━━━━━",
                "<gray>Игрок: <white>" + p.getName(),
                "<gray>Монеты: <gold>" + plugin.fmt(d.coins),
                "<gray>Онлайн: <green>" + Bukkit.getOnlinePlayers().size(),
                " ",
                "<gray>Профессия: " + (top == null ? "<white>—" : top.color + top.title + " <white>" + d.level(top)),
                "<gray>Событие: " + (ev == null ? "<dark_gray>нет" : "<yellow>" + ev.title),
                "<dark_gray>━━━━━━━━━━━━━━",
                "<yellow>" + plugin.getConfig().getString("server-ip", "")
        );
        int total = lines.size();
        for (int i = 0; i < total; i++) {
            Score s = o.getScore("\u00a7" + Integer.toHexString(i));
            s.customName(plugin.mm(lines.get(i)));
            s.setScore(total - i);
        }
    }
}
