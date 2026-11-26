package dev.nandi0813.zppbots.menu;

import dev.nandi0813.api.Interface.BotRegistry;
import dev.nandi0813.api.Interface.BotSetup;
import dev.nandi0813.api.ZonePracticeApi;
import dev.nandi0813.zppbots.ZPPBots;
import dev.nandi0813.zppbots.config.Messages;
import dev.nandi0813.zppbots.settings.Behavior.PlayStyleSetting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Summary, difficulty row, play-style row, play again and start. Start opens the core ladder, arena
 * and round selection.
 */
public final class BotSetupMenu implements InventoryHolder {

    private static final int SUMMARY_SLOT = 4;
    private static final int[] LEVEL_SLOTS = {11, 12, 13, 14, 15};
    private static final Material[] LEVEL_ICONS = {Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD};
    private static final int STYLE_FIRST_SLOT = 28;
    private static final int STYLE_ROW_WIDTH = 7;
    private static final int REMATCH_SLOT = 38;
    private static final int START_SLOT = 40;

    private final ZPPBots plugin;
    private final UUID viewer;
    private final Inventory inventory;
    private final List<PlayStyleSetting> styles;
    private final BotSetup lastSetup;
    private int level;
    private PlayStyleSetting style;

    public BotSetupMenu(ZPPBots plugin, Player player) {
        this.plugin = plugin;
        this.viewer = player.getUniqueId();
        this.styles = plugin.getProvider().allowedStyles();
        this.level = plugin.getProvider().defaultLevel();
        this.style = plugin.getProvider().defaultStyle();
        this.lastSetup = ZonePracticeApi.getInstance().getBotRegistry().getLastSetup(player);
        this.inventory = Bukkit.createInventory(this, 45, plugin.getMessages().get("SETUP-MENU.TITLE"));
        draw();
    }

    public void open() {
        Player player = Bukkit.getPlayer(viewer);
        if (player != null) player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    private void draw() {
        Messages messages = plugin.getMessages();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, button(Material.BLACK_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.GRAY), List.of()));
        }
        inventory.setItem(SUMMARY_SLOT, summary(messages));
        for (int l = 1; l <= 5; l++) {
            inventory.setItem(LEVEL_SLOTS[l - 1], icon(messages, LEVEL_ICONS[l - 1], coloredDifficulty(messages, l),
                    "DIFFICULTY." + l + ".LORE", l == plugin.getProvider().defaultLevel(), l == level));
        }
        if (styles.size() > 1) {
            int offset = STYLE_FIRST_SLOT + (STYLE_ROW_WIDTH - styles.size()) / 2;
            for (int i = 0; i < styles.size(); i++) {
                PlayStyleSetting option = styles.get(i);
                inventory.setItem(offset + i, icon(messages, styleIcon(option),
                        messages.get("SETUP-MENU.ITEM-NAME", Placeholder.unparsed("name", plugin.getProvider().styleName(option))),
                        "PLAY-STYLE." + option.name() + ".LORE", option == plugin.getProvider().defaultStyle(),
                        option == style));
            }
        }
        if (lastSetup != null) inventory.setItem(REMATCH_SLOT, rematch(messages));
        inventory.setItem(START_SLOT, button(Material.EMERALD, messages.get("SETUP-MENU.START.NAME"),
                messages.getList("SETUP-MENU.START.LORE")));
    }

    private ItemStack summary(Messages messages) {
        return button(Material.BOOK, messages.get("SETUP-MENU.SUMMARY.NAME"), messages.getList("SETUP-MENU.SUMMARY.LORE",
                Placeholder.component("difficulty", coloredDifficulty(messages, level)),
                Placeholder.unparsed("style", plugin.getProvider().styleName(style))));
    }

    private ItemStack rematch(Messages messages) {
        return button(Material.CLOCK, messages.get("SETUP-MENU.REMATCH.NAME"),
                messages.getList("SETUP-MENU.REMATCH.LORE", Placeholder.unparsed("ladder", lastSetup.ladderName())));
    }

    private Component coloredDifficulty(Messages messages, int level) {
        return messages.get("SETUP-MENU.LEVEL-COLOR." + level).append(Component.text(plugin.getProvider().difficultyName(level)));
    }

    private static Material styleIcon(PlayStyleSetting style) {
        return switch (style) {
            case DEFAULT -> Material.COMPASS;
            case SAFE -> Material.SHIELD;
            case AGGRESSIVE -> Material.IRON_AXE;
            case ADAPTIVE -> Material.ENDER_EYE;
            case DEFENSIVE -> Material.IRON_CHESTPLATE;
            case BALANCED -> Material.GOLDEN_APPLE;
            case RANDOM -> Material.ENDER_PEARL;
        };
    }

    private ItemStack icon(Messages messages, Material material, Component name, String lorePath, boolean isDefault,
                           boolean selected) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(name));
        List<Component> lore = new ArrayList<>();
        for (Component line : messages.getList(lorePath)) lore.add(plain(line));
        if (isDefault) lore.add(plain(messages.get("SETUP-MENU.DEFAULT")));
        if (selected) lore.add(plain(messages.get("SETUP-MENU.SELECTED")));
        else for (Component line : messages.getList("SETUP-MENU.FOOTER")) lore.add(plain(line));
        meta.lore(lore);
        if (selected) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(name));
        meta.lore(lore.stream().map(BotSetupMenu::plain).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static Component plain(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static void clicked(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    private void click(Player player, int slot) {
        for (int i = 0; i < LEVEL_SLOTS.length; i++) {
            if (LEVEL_SLOTS[i] == slot) {
                level = i + 1;
                clicked(player);
                draw();
                return;
            }
        }
        int styleIndex = slot - (STYLE_FIRST_SLOT + (STYLE_ROW_WIDTH - styles.size()) / 2);
        if (styles.size() > 1 && styleIndex >= 0 && styleIndex < styles.size()) {
            style = styles.get(styleIndex);
            clicked(player);
            draw();
            return;
        }
        if (slot == REMATCH_SLOT && lastSetup != null) {
            clicked(player);
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                BotRegistry registry = ZonePracticeApi.getInstance().getBotRegistry();
                if (!registry.isAvailable() || plugin.getProvider() == null) {
                    plugin.getMessages().send(player, "BOT-PRACTICE.UNAVAILABLE");
                    return;
                }
                registry.start(player, lastSetup);
            });
            return;
        }
        if (slot != START_SLOT) return;
        int chosenLevel = level;
        PlayStyleSetting chosenStyle = style;
        clicked(player);
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            BotRegistry registry = ZonePracticeApi.getInstance().getBotRegistry();
            if (!registry.isAvailable() || plugin.getProvider() == null) {
                plugin.getMessages().send(player, "BOT-PRACTICE.UNAVAILABLE");
                return;
            }
            registry.openSetup(player, plugin.getProvider().options(chosenLevel, chosenStyle));
        });
    }

    /** Cancels every move in the menu and handles clicks; ignores views of other players. */
    public static final class Clicks implements Listener {

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onClick(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder(false) instanceof BotSetupMenu menu)) return;
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || !menu.viewer.equals(player.getUniqueId())) return;
            if (event.getClickedInventory() != menu.inventory) return;
            menu.click(player, event.getRawSlot());
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDrag(InventoryDragEvent event) {
            if (event.getView().getTopInventory().getHolder(false) instanceof BotSetupMenu) event.setCancelled(true);
        }
    }
}
