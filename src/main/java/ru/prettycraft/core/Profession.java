package ru.prettycraft.core;

import org.bukkit.Material;

public enum Profession {
    MINER("Шахтёр", Material.IRON_PICKAXE, "<aqua>"),
    LUMBERJACK("Лесоруб", Material.IRON_AXE, "<gold>"),
    FARMER("Фермер", Material.IRON_HOE, "<green>"),
    HUNTER("Охотник", Material.IRON_SWORD, "<red>");

    public final String title;
    public final Material icon;
    public final String color;

    Profession(String title, Material icon, String color) {
        this.title = title;
        this.icon = icon;
        this.color = color;
    }
}
