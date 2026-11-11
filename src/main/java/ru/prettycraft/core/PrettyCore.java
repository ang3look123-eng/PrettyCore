package ru.prettycraft.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class PrettyCore extends JavaPlugin {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long DAY = 86_400_000L;
    private static final String[] COMMANDS = {
            "balance", "pay", "baltop", "daily", "menu", "sethome", "home", "delhome",
            "homes", "setspawn", "spawn", "event", "sb", "prettycore"
    };

    private final DecimalFormat money = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private DataManager data;
    private EventManager events;
    private BoardManager boards;
    private ProfessionListener professions;
    private MenuGui menu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new DataManager(this);
        events = new EventManager(this);
        boards = new BoardManager(this);
        professions = new ProfessionListener(this);
        menu = new MenuGui(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(professions, this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(menu, this);

        Commands cmds = new Commands(this);
        for (String name : COMMANDS) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(cmds);
                c.setTabCompleter(cmds);
            }
        }

        events.start();
        boards.start();
        getServer().getScheduler().runTaskTimer(this, () -> data.saveAsync(), 6000L, 6000L);
        getLogger().info("PrettyCore включен");
    }

    @Override
    public void onDisable() {
        if (events != null) events.shutdown();
        if (boards != null) boards.shutdown();
        if (data != null) data.saveSync();
    }

    public DataManager data() { return data; }
    public EventManager events() { return events; }
    public BoardManager boards() { return boards; }
    public ProfessionListener professions() { return professions; }
    public MenuGui menu() { return menu; }

    public Component mm(String mini) {
        return MM.deserialize(mini);
    }

    public Component prefixed(String mini) {
        return MM.deserialize(getConfig().getString("prefix", "") + mini);
    }

    public void send(CommandSender to, String mini) {
        to.sendMessage(prefixed(mini));
    }

    public String fmt(double v) {
        return money.format(v);
    }

    public void giveItem(Player p, ItemStack item) {
        for (ItemStack left : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItem(p.getLocation(), left);
        }
    }

    public String time(long ms) {
        long m = Math.max(1, ms / 60000);
        return (m / 60) + " ч " + (m % 60) + " мин";
    }

    public long dailyLeft(PlayerData d) {
        return DAY - (System.currentTimeMillis() - d.lastDaily);
    }

    public void claimDaily(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        long left = dailyLeft(d);
        if (left > 0) {
            send(p, "<red>Награда уже получена. Приходите через <white>" + time(left));
            return;
        }
        long now = System.currentTimeMillis();
        d.streak = (now - d.lastDaily > 2 * DAY) ? 1 : d.streak + 1;
        d.lastDaily = now;
        int step = ((d.streak - 1) % 7) + 1;
        double reward = getConfig().getDouble("daily.base", 100) + (step - 1) * getConfig().getDouble("daily.step", 50);
        d.coins += reward;
        send(p, "<green>Ежедневная награда: <gold>+" + fmt(reward) + " монет <gray>(день " + step + "/7)");
        if (step == 7) {
            giveItem(p, new ItemStack(Material.DIAMOND, 3));
            send(p, "<aqua>Бонус за неделю подряд: <white>3 алмаза!");
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }
}
