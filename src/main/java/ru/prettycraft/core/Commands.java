package ru.prettycraft.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Commands implements TabExecutor {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final PrettyCore plugin;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();

    public Commands(PrettyCore plugin) {
        this.plugin = plugin;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        plugin.send(s, "<red>Эта команда только для игроков.");
        return null;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("prettycore.admin")) return true;
        plugin.send(s, "<red>Недостаточно прав.");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        switch (c.getName().toLowerCase(Locale.ROOT)) {
            case "balance" -> balance(s, a);
            case "pay" -> pay(s, a);
            case "baltop" -> baltop(s);
            case "daily" -> {
                Player p = player(s);
                if (p != null) plugin.claimDaily(p);
            }
            case "menu" -> {
                Player p = player(s);
                if (p != null) plugin.menu().open(p);
            }
            case "sethome" -> sethome(s, a);
            case "home" -> home(s, a);
            case "delhome" -> delhome(s, a);
            case "homes" -> homes(s);
            case "setspawn" -> setspawn(s);
            case "spawn" -> spawn(s);
            case "event" -> event(s, a);
            case "sb" -> {
                Player p = player(s);
                if (p != null) {
                    PlayerData d = plugin.data().get(p.getUniqueId());
                    d.scoreboard = !d.scoreboard;
                    plugin.boards().update(p);
                    plugin.send(p, "Скорборд: " + (d.scoreboard ? "<green>включён" : "<red>выключен"));
                }
            }
            case "prettycore" -> admincmd(s, a);
            default -> { return false; }
        }
        return true;
    }

    // ---------- экономика ----------

    private void balance(CommandSender s, String[] a) {
        Player target = a.length > 0 ? Bukkit.getPlayerExact(a[0]) : (s instanceof Player p ? p : null);
        if (target == null) {
            plugin.send(s, "<red>Игрок не найден.");
            return;
        }
        PlayerData d = plugin.data().get(target.getUniqueId());
        plugin.send(s, "<gray>Баланс <white>" + target.getName() + "<gray>: <gold>" + plugin.fmt(d.coins) + " монет");
    }

    private void pay(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) {
            plugin.send(p, "<gray>Использование: <white>/pay <игрок> <сумма>");
            return;
        }
        Player to = Bukkit.getPlayerExact(a[0]);
        if (to == null || to.equals(p)) {
            plugin.send(p, "<red>Игрок не найден или это вы сами.");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(a[1]);
        } catch (NumberFormatException ex) {
            plugin.send(p, "<red>Неверная сумма.");
            return;
        }
        amount = Math.round(amount * 100) / 100.0;
        if (!Double.isFinite(amount) || amount < 1) {
            plugin.send(p, "<red>Минимальная сумма: 1.");
            return;
        }
        PlayerData from = plugin.data().get(p.getUniqueId());
        if (from.coins < amount) {
            plugin.send(p, "<red>Недостаточно монет.");
            return;
        }
        PlayerData target = plugin.data().get(to.getUniqueId());
        from.coins -= amount;
        target.coins += amount;
        plugin.send(p, "<green>Вы отправили <gold>" + plugin.fmt(amount) + " <green>игроку <white>" + to.getName());
        plugin.send(to, "<green>Вы получили <gold>" + plugin.fmt(amount) + " <green>от <white>" + p.getName());
    }

    private void baltop(CommandSender s) {
        plugin.send(s, "<gold><bold>Топ богачей");
        int i = 1;
        for (Map.Entry<String, Double> e : plugin.data().top(10)) {
            s.sendMessage(plugin.mm("<yellow>" + i++ + ". <white>" + e.getKey() + " <dark_gray>- <gold>"
                    + plugin.fmt(e.getValue())));
        }
    }

    // ---------- дом / спавн ----------

    private void sethome(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        String name = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "home";
        if (!NAME.matcher(name).matches()) {
            plugin.send(p, "<red>Имя: латиница, цифры и _ (до 16 символов).");
            return;
        }
        PlayerData d = plugin.data().get(p.getUniqueId());
        int max = plugin.getConfig().getInt("homes.max", 3);
        if (!d.homes.containsKey(name) && d.homes.size() >= max) {
            plugin.send(p, "<red>Лимит домов: " + max + ". Удалите лишний: /delhome <имя>");
            return;
        }
        d.homes.put(name, p.getLocation());
        plugin.send(p, "<green>Дом <white>" + name + "<green> установлен.");
    }

    private void home(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d.homes.isEmpty()) {
            plugin.send(p, "<red>У вас нет домов. Используйте /sethome");
            return;
        }
        String name;
        if (a.length > 0) {
            name = a[0].toLowerCase(Locale.ROOT);
        } else if (d.homes.size() == 1) {
            name = d.homes.keySet().iterator().next();
        } else {
            plugin.send(p, "<gray>Ваши дома: <white>" + String.join(", ", d.homes.keySet()));
            return;
        }
        Location l = d.homes.get(name);
        if (l == null) {
            plugin.send(p, "<red>Дом не найден.");
            return;
        }
        teleport(p, l);
    }

    private void delhome(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 1) {
            plugin.send(p, "<gray>Использование: <white>/delhome <имя>");
            return;
        }
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d.homes.remove(a[0].toLowerCase(Locale.ROOT)) != null) {
            plugin.send(p, "<green>Дом удалён.");
        } else {
            plugin.send(p, "<red>Дом не найден.");
        }
    }

    private void homes(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        PlayerData d = plugin.data().get(p.getUniqueId());
        plugin.send(p, d.homes.isEmpty() ? "<gray>Домов нет."
                : "<gray>Ваши дома: <white>" + String.join(", ", d.homes.keySet()));
    }

    private void setspawn(CommandSender s) {
        Player p = player(s);
        if (p == null || !admin(s)) return;
        plugin.data().setSpawn(p.getLocation());
        plugin.send(p, "<green>Спавн установлен.");
    }

    private void spawn(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        Location sp = plugin.data().spawn();
        if (sp == null) {
            plugin.send(p, "<red>Спавн ещё не установлен.");
            return;
        }
        teleport(p, sp);
    }

    private void teleport(Player p, Location dest) {
        int sec = plugin.getConfig().getInt("teleport.warmup", 3);
        BukkitTask old = pending.remove(p.getUniqueId());
        if (old != null) old.cancel();
        if (sec <= 0 || p.hasPermission("prettycore.bypass")) {
            p.teleportAsync(dest);
            return;
        }
        Location start = p.getLocation();
        plugin.send(p, "<yellow>Телепортация через <white>" + sec + " <yellow>сек. Не двигайтесь!");
        BukkitTask t = new BukkitRunnable() {
            int left = sec;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    pending.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                Location now = p.getLocation();
                if (!now.getWorld().equals(start.getWorld()) || now.distanceSquared(start) > 0.5) {
                    plugin.send(p, "<red>Телепортация отменена: вы подвигались.");
                    pending.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                if (--left <= 0) {
                    pending.remove(p.getUniqueId());
                    cancel();
                    p.teleportAsync(dest);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
        pending.put(p.getUniqueId(), t);
    }

    // ---------- события и админка ----------

    private void event(CommandSender s, String[] a) {
        EventManager em = plugin.events();
        if (a.length == 0) {
            EventManager.Type cur = em.current();
            if (cur == null) {
                plugin.send(s, "<gray>Сейчас событий нет. Они случайно проходят каждые "
                        + plugin.getConfig().getLong("events.interval-minutes", 45) + " мин.");
            } else {
                plugin.send(s, "<yellow>Идёт: <white>" + cur.title + " <gray>(" + cur.profession.color
                        + cur.profession.title + "<gray>), осталось <white>" + plugin.time(em.millisLeft()));
            }
            return;
        }
        if (!admin(s)) return;
        if (a[0].equalsIgnoreCase("stop")) {
            em.end(true);
        } else if (a[0].equalsIgnoreCase("start") && a.length > 1) {
            try {
                em.begin(EventManager.Type.valueOf(a[1].toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.send(s, "<red>Неизвестное событие.");
            }
        } else {
            plugin.send(s, "<gray>/event start <тип> | /event stop");
        }
    }

    private void admincmd(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length == 0) {
            plugin.send(s, "<gray>/pc reload | /pc give <игрок> <сумма>");
            return;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.send(s, "<green>Конфиг перезагружен.");
            }
            case "give" -> {
                if (a.length < 3) {
                    plugin.send(s, "<gray>/pc give <игрок> <сумма>");
                    return;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) {
                    plugin.send(s, "<red>Игрок не найден.");
                    return;
                }
                try {
                    double v = Double.parseDouble(a[2]);
                    if (!Double.isFinite(v)) throw new NumberFormatException();
                    plugin.data().get(t.getUniqueId()).coins += v;
                    plugin.send(s, "<green>Выдано <gold>" + plugin.fmt(v) + " <green>игроку " + t.getName());
                } catch (NumberFormatException ex) {
                    plugin.send(s, "<red>Неверная сумма.");
                }
            }
            default -> plugin.send(s, "<gray>/pc reload | /pc give <игрок> <сумма>");
        }
    }

    // ---------- автодополнение ----------

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String cmd = c.getName().toLowerCase(Locale.ROOT);
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "balance", "pay" -> {
                if (a.length == 1) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
            case "home", "delhome" -> {
                if (a.length == 1 && s instanceof Player p) out.addAll(plugin.data().get(p.getUniqueId()).homes.keySet());
            }
            case "event" -> {
                if (a.length == 1) out.addAll(List.of("start", "stop"));
                else if (a.length == 2 && a[0].equalsIgnoreCase("start")) {
                    for (EventManager.Type t : EventManager.Type.values()) out.add(t.name().toLowerCase(Locale.ROOT));
                }
            }
            case "prettycore" -> {
                if (a.length == 1) out.addAll(List.of("reload", "give"));
                else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
                    Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                }
            }
            default -> { }
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
