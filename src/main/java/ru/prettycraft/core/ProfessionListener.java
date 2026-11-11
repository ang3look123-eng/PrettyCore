package ru.prettycraft.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class ProfessionListener implements Listener {
    private static final Set<Material> CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART);

    private final PrettyCore plugin;
    private final Set<Location> placed = new HashSet<>();
    private final Random rnd = new Random();

    public ProfessionListener(PrettyCore plugin) {
        this.plugin = plugin;
    }

    private static boolean isOre(Material m) {
        String n = m.name();
        return n.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    private double oreValue(Material m) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("ores");
        if (s == null) return 0;
        String n = m.name();
        for (String key : s.getKeys(false)) {
            if (n.contains(key)) return s.getDouble(key);
        }
        return 0;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Material m = e.getBlock().getType();
        if (isOre(m) || Tag.LOGS.isTagged(m)) {
            if (placed.size() > 100_000) placed.clear();
            placed.add(e.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        Block b = e.getBlock();
        Material m = b.getType();

        boolean ore = isOre(m);
        boolean log = Tag.LOGS.isTagged(m);
        if ((ore || log) && placed.remove(b.getLocation())) return; // блок ставил игрок

        var cfg = plugin.getConfig();
        Profession pr;
        double coins;
        long xp;
        if (ore) {
            coins = oreValue(m);
            if (coins <= 0) return;
            pr = Profession.MINER;
            xp = Math.max(1, Math.round(coins * 2));
        } else if (log) {
            pr = Profession.LUMBERJACK;
            coins = cfg.getDouble("professions.log.coins", 0.5);
            xp = cfg.getLong("professions.log.xp", 3);
        } else if (CROPS.contains(m) && b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge()) {
            pr = Profession.FARMER;
            coins = cfg.getDouble("professions.crop.coins", 1.0);
            xp = cfg.getLong("professions.crop.xp", 4);
        } else {
            return;
        }

        reward(p, pr, coins, xp);
        doubleDrop(p, b, pr);
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null || !(e.getEntity() instanceof Monster)) return;
        SpawnReason r = e.getEntity().getEntitySpawnReason();
        if (r == SpawnReason.SPAWNER || r == SpawnReason.SPAWNER_EGG) return; // анти-ферма
        var cfg = plugin.getConfig();
        reward(p, Profession.HUNTER,
                cfg.getDouble("professions.mob.coins", 2.0),
                cfg.getLong("professions.mob.xp", 6));
    }

    private void doubleDrop(Player p, Block b, Profession pr) {
        int lvl = plugin.data().get(p.getUniqueId()).level(pr);
        double chance = lvl * plugin.getConfig().getDouble("professions.double-drop-per-level", 0.005);
        if (rnd.nextDouble() >= chance) return;
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        ItemStack tool = p.getInventory().getItemInMainHand();
        for (ItemStack it : b.getDrops(tool, p)) {
            b.getWorld().dropItemNaturally(loc, it);
        }
        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 0.3, 0.3, 0.3);
    }

    public void reward(Player p, Profession pr, double coins, long xp) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        double mult = plugin.events().multiplier(pr);
        int before = d.level(pr);
        double bonus = 1 + before * plugin.getConfig().getDouble("professions.coin-bonus-per-level", 0.02);
        double gain = Math.round(coins * mult * bonus * 100) / 100.0;
        long xpGain = Math.max(1, Math.round(xp * mult));

        d.coins += gain;
        d.xp.merge(pr, xpGain, Long::sum);
        int after = d.level(pr);

        String boost = mult > 1 ? " <yellow>x" + plugin.fmt(mult) : "";
        p.sendActionBar(plugin.mm("<gold>+" + plugin.fmt(gain) + " <gray>• " + pr.color + pr.title
                + " <gray>+" + xpGain + " XP" + boost));

        if (after > before) levelUp(p, d, pr, after);
    }

    private void levelUp(Player p, PlayerData d, Profession pr, int level) {
        double bonus = level * 50.0;
        d.coins += bonus;
        p.showTitle(Title.title(
                plugin.mm(pr.color + "<bold>" + pr.title + " " + level),
                plugin.mm("<gray>Новый уровень! <gold>+" + plugin.fmt(bonus) + " монет"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        if (level % 10 == 0) {
            Component msg = plugin.prefixed("<yellow>" + p.getName() + " достиг <white>" + level
                    + " <yellow>уровня профессии " + pr.color + pr.title + "<yellow>!");
            Bukkit.broadcast(msg);
        }
    }
}
