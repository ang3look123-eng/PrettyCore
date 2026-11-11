package ru.prettycraft.core;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PlayerListener implements Listener {
    private final PrettyCore plugin;

    public PlayerListener(PrettyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p.getUniqueId());
        d.name = p.getName();

        e.joinMessage(plugin.mm("<dark_gray>[<green>+<dark_gray>] <gray>" + p.getName()));
        plugin.events().show(p);

        if (!p.hasPlayedBefore()) {
            d.coins += plugin.getConfig().getDouble("starter-coins", 100);
            Location sp = plugin.data().spawn();
            if (sp != null) p.teleportAsync(sp);
            plugin.send(p, "<green>Добро пожаловать! Стартовый бонус: <gold>"
                    + plugin.fmt(plugin.getConfig().getDouble("starter-coins", 100)) + " монет");
        }

        p.showTitle(Title.title(
                plugin.mm("<gradient:#ff6ec7:#ffb86c><bold>PrettyCraft</bold></gradient>"),
                plugin.mm("<gray>Откройте меню: <white>/menu")));
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.boards().update(p));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        e.quitMessage(plugin.mm("<dark_gray>[<red>-<dark_gray>] <gray>" + p.getName()));
        plugin.boards().remove(p);
        plugin.data().saveAsync();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (e.isBedSpawn() || e.isAnchorSpawn()) return;
        Location sp = plugin.data().spawn();
        if (sp != null) e.setRespawnLocation(sp);
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        PlayerData d = plugin.data().peek(e.getPlayer().getUniqueId());
        String tag;
        if (d == null || d.top() == null) {
            tag = "<dark_gray>[<gray>Новичок<dark_gray>] ";
        } else {
            Profession top = d.top();
            tag = "<dark_gray>[" + top.color + top.title + " <white>" + d.level(top) + "<dark_gray>] ";
        }
        Component prefix = plugin.mm(tag);
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) ->
                prefix.append(name)
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(message.color(NamedTextColor.WHITE))));
    }
}
