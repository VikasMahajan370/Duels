package me.raikou.duels.combat;

import lombok.Getter;
import me.raikou.duels.DuelsPlugin;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * Central manager for the 1.8 Legacy PvP combat system.
 * Coordinates all combat-related handlers and provides unified configuration.
 * 
 * Features:
 * - No attack cooldown (spam-click PvP)
 * - 1.8-style knockback
 * - Sprint-reset behavior
 * - Fake sword blocking
 * - Normalized damage values
 * - Shield prevention
 */
@Getter
public class CombatManager implements Listener {

    private final DuelsPlugin plugin;
    private final DamageHandler damageHandler;
    private final KnockbackHandler knockbackHandler;
    private final LegacyBlockHandler blockHandler;
    private final GoldenAppleHandler goldenAppleHandler;
    private final ShieldHandler shieldHandler;

    // Master toggle
    private boolean enabled;

    // Knockback settings
    private double horizontalKB;
    private double verticalKB;
    private double sprintMultiplier;
    private double kbEnchantMultiplier;
    private double rodHorizontalKB;
    private double rodVerticalKB;

    // Sword blocking settings
    private boolean swordBlockEnabled;
    private double blockDamageReduction;
    private int blockCooldownTicks;
    private int blockDurationTicks;

    // Damage settings
    private boolean use18DamageValues;
    private boolean removeCrits;
    private boolean removeSweeping;

    // Golden apple settings
    private boolean gappleRemoveCooldown;
    private boolean gappleUse18Effects;

    // Shield settings
    private boolean disableShields;

    public CombatManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        loadConfig();

        // Initialize handlers
        this.damageHandler = new DamageHandler(this);
        this.knockbackHandler = new KnockbackHandler(this);
        this.blockHandler = new LegacyBlockHandler(this);
        this.goldenAppleHandler = new GoldenAppleHandler(this);
        this.shieldHandler = new ShieldHandler(this);

        if (enabled) {
            plugin.getLogger().info("§a[Combat] Legacy 1.8 PvP system initialized!");
            plugin.getLogger().info("§7  - Knockback: H=" + horizontalKB + " V=" + verticalKB);
            plugin.getLogger().info("§7  - Sword Block: " + (swordBlockEnabled ? "Enabled" : "Disabled"));
            plugin.getLogger().info("§7  - Shields: " + (disableShields ? "Disabled" : "Enabled"));
        }
    }

    /**
     * Load configuration values from config.yml
     */
    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("combat.legacy-pvp", true);

        // Knockback
        this.horizontalKB = plugin.getConfig().getDouble("combat.knockback.horizontal", 0.36);
        this.verticalKB = plugin.getConfig().getDouble("combat.knockback.vertical", 0.32);
        this.sprintMultiplier = plugin.getConfig().getDouble("combat.knockback.sprint-multiplier", 1.0);
        this.kbEnchantMultiplier = plugin.getConfig().getDouble("combat.knockback.kb-enchant-multiplier", 0.4);
        this.rodHorizontalKB = plugin.getConfig().getDouble("combat.knockback.rod-horizontal", 0.65);
        this.rodVerticalKB = plugin.getConfig().getDouble("combat.knockback.rod-vertical", 0.40);

        // Sword blocking
        this.swordBlockEnabled = plugin.getConfig().getBoolean("combat.sword-block.enabled", true);
        this.blockDamageReduction = plugin.getConfig().getDouble("combat.sword-block.damage-reduction", 0.5);
        this.blockCooldownTicks = plugin.getConfig().getInt("combat.sword-block.cooldown-ticks", 6);
        this.blockDurationTicks = plugin.getConfig().getInt("combat.sword-block.duration-ticks", 20);

        // Damage
        this.use18DamageValues = plugin.getConfig().getBoolean("combat.damage.use-1-8-values", true);
        this.removeCrits = plugin.getConfig().getBoolean("combat.damage.remove-crits", true);
        this.removeSweeping = plugin.getConfig().getBoolean("combat.damage.remove-sweeping", true);

        // Golden apple
        this.gappleRemoveCooldown = plugin.getConfig().getBoolean("combat.golden-apple.remove-cooldown", true);
        this.gappleUse18Effects = plugin.getConfig().getBoolean("combat.golden-apple.use-1-8-effects", true);

        // Shields
        this.disableShields = plugin.getConfig().getBoolean("combat.shields.disable-completely", true);
    }

    /**
     * Reload configuration and update all handlers
     */
    public void reload() {
        loadConfig();
        plugin.getLogger().info("§a[Combat] Configuration reloaded!");
    }

    /**
     * Apply legacy combat mechanics to a player.
     * Sets attack speed to maximum (no cooldown).
     */
    public void applyLegacyCombat(Player player) {
        if (!enabled)
            return;

        try {
            AttributeInstance attackSpeed = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (attackSpeed != null) {
                // Set to very high value - effectively removes attack cooldown
                attackSpeed.setBaseValue(1024.0);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Combat] Could not apply legacy combat to " + player.getName());
        }
    }

    /**
     * Reset player to default 1.9+ combat mechanics.
     */
    public void resetCombat(Player player) {
        try {
            AttributeInstance attackSpeed = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (attackSpeed != null) {
                attackSpeed.setBaseValue(4.0); // Default 1.9+ value
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Check if a player is in a duel (combat context).
     */
    public boolean isInDuel(Player player) {
        return plugin.getDuelManager().getDuel(player) != null;
    }

    // --- Event Handlers ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled)
            return;
        applyLegacyCombat(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!enabled)
            return;
        // Delay slightly to ensure attributes are properly set after respawn
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            applyLegacyCombat(event.getPlayer());
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!enabled)
            return;
        applyLegacyCombat(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemSwitch(PlayerItemHeldEvent event) {
        if (!enabled)
            return;
        // Reapply on item switch to ensure attack speed persists
        applyLegacyCombat(event.getPlayer());
    }
}
