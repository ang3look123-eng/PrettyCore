package ru.prettycraft.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public final class MenuGui implements Listener {
    private static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final PrettyCore plugin;

    public MenuGui(PrettyCore plugin) {
        this.plugin = plugin;
    }

    private ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(plugin.mm("<!italic>" + name));
        meta.lore(Arrays.stream(lore).map(s -> plugin.mm("<!italic>" + s)).toList());
        it.setItemMeta(meta);
        return it;
    }

    public void open(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27,
                plugin.mm("<gradient:#ff6ec7:#ffb86c><bold>Меню PrettyCraft</bold></gradient>"));
        h.inv = inv;

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(4, item(Material.GOLD_INGOT, "<gold><bold>Баланс",
                "<gray>Монеты: <yellow>" + plugin.fmt(d.coins)));

        int[] slots = {10, 12, 14, 16};
        Profession[] all = Profession.values();
        for (int i = 0; i < all.length; i++) inv.setItem(slots[i], profItem(all[i], d));

        inv.setItem(18, item(Material.ENDER_PEARL, "<light_purple>Спавн", "<gray>Нажмите для телепортации"));

        long left = plugin.dailyLeft(d);
        if (left > 0) {
            inv.setItem(22, item(Material.CLOCK, "<green>Ежедневная награда",
                    "<red>Уже получена", "<gray>Через: <white>" + plugin.time(left),
                    "<gray>Серия: <white>" + d.streak));
        } else {
            inv.setItem(22, item(Material.CHEST, "<green><bold>Ежедневная награда",
                    "<yellow>Нажмите, чтобы забрать!", "<gray>Серия: <white>" + d.streak));
        }

        inv.setItem(26, item(Material.OAK_SIGN, "<yellow>Скорборд",
                "<gray>Сейчас: " + (d.scoreboard ? "<green>включён" : "<red>выключен"),
                "<gray>Нажмите для переключения"));
        p.openInventory(inv);
    }

    private ItemStack profItem(Profession pr, PlayerData d) {
        int lvl = d.level(pr);
        long xp = d.xp.get(pr);
        String bar;
        String xpLine;
        if (lvl >= PlayerData.MAX_LEVEL) {
            bar = "<green>" + "■".repeat(10);
            xpLine = "<gray>XP: <white>" + xp + " <gold>(максимум)";
        } else {
            long cur = xp - PlayerData.xpFor(lvl);
            long need = PlayerData.xpFor(lvl + 1) - PlayerData.xpFor(lvl);
            int filled = (int) Math.min(10, cur * 10 / Math.max(1, need));
            bar = "<green>" + "■".repeat(filled) + "<dark_gray>" + "■".repeat(10 - filled);
            xpLine = "<gray>XP: <white>" + cur + "/" + need;
        }
        var cfg = plugin.getConfig();
        double dd = lvl * cfg.getDouble("professions.double-drop-per-level", 0.005) * 100;
        double cb = lvl * cfg.getDouble("professions.coin-bonus-per-level", 0.02) * 100;
        return item(pr.icon, pr.color + "<bold>" + pr.title,
                "<gray>Уровень: <white>" + lvl + "/" + PlayerData.MAX_LEVEL,
                bar, xpLine, " ",
                "<gray>Двойной дроп: <green>" + plugin.fmt(dd) + "%",
                "<gray>Бонус монет: <green>+" + plugin.fmt(cb) + "%");
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        switch (e.getRawSlot()) {
            case 18 -> {
                p.closeInventory();
                p.performCommand("spawn");
            }
            case 22 -> {
                plugin.claimDaily(p);
                open(p);
            }
            case 26 -> {
                PlayerData d = plugin.data().get(p.getUniqueId());
                d.scoreboard = !d.scoreboard;
                plugin.boards().update(p);
                open(p);
            }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
