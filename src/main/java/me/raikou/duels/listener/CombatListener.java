package me.raikou.duels.listener;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Implements 1.8 combat mechanics in modern Minecraft versions.
 * - Removes attack cooldown (instant attack speed)
 * - Disables sweep attacks
 * - Fishing rod knockback (push away with hit effect)
 */
public class CombatListener implements Listener {

    private final DuelsPlugin plugin;
    private final boolean enabled;
    private final Set<UUID> rodCooldown = new HashSet<>();

    public CombatListener(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("combat.legacy-pvp", true);

        if (enabled) {
            plugin.getLogger().info("1.8 Legacy PvP combat system enabled!");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled)
            return;
        applyLegacyCombat(event.getPlayer());
    }

    @EventHandler
    public void onItemSwitch(PlayerItemHeldEvent event) {
        if (!enabled)
            return;
        applyLegacyCombat(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled)
            return;

        // Disable sweep attack damage
        if (event.getCause() == EntityDamageByEntityEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
        }
    }

    /**
     * Cancel the vanilla rod pull behavior completely when reeling catches a
     * player.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFish(PlayerFishEvent event) {
        if (!enabled)
            return;

        Player fisher = event.getPlayer();

        if (plugin.getDuelManager().getDuel(fisher) == null)
            return;

        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            if (event.getCaught() instanceof Player target) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Vector current = target.getVelocity();
                    target.setVelocity(new Vector(0, Math.min(current.getY(), 0), 0));
                });

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Vector current = target.getVelocity();
                    target.setVelocity(new Vector(0, Math.min(current.getY(), 0), 0));
                }, 1L);
            }
        }
    }

    /**
     * Fishing rod knockback - PUSH players AWAY when hook hits them
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!enabled)
            return;

        if (event.getEntity() instanceof FishHook hook) {
            if (hook.getShooter() instanceof Player fisher) {
                if (plugin.getDuelManager().getDuel(fisher) == null)
                    return;

                Entity hitEntity = event.getHitEntity();
                if (hitEntity instanceof Player target && !target.equals(fisher)) {
                    if (rodCooldown.contains(target.getUniqueId()))
                        return;

                    applyRodKnockback(fisher, target);
                    applyRodHitEffect(target);

                    rodCooldown.add(target.getUniqueId());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        rodCooldown.remove(target.getUniqueId());
                    }, 10L);
                }
            }
        }
    }

    private void applyRodKnockback(Player fisher, Player target) {
        Vector knockback = target.getLocation().toVector()
                .subtract(fisher.getLocation().toVector());
        knockback.setY(0);

        if (knockback.lengthSquared() > 0) {
            knockback.normalize();
        }

        double horizontalStrength = 0.5;
        double verticalStrength = 0.35;
        Vector finalVelocity = knockback.multiply(horizontalStrength).setY(verticalStrength);

        Bukkit.getScheduler().runTask(plugin, () -> {
            target.setVelocity(finalVelocity);
        });
    }

    private void applyRodHitEffect(Player target) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            int originalNoDamageTicks = target.getNoDamageTicks();
            target.setNoDamageTicks(0);

            double originalHealth = target.getHealth();
            target.damage(0.001);

            if (target.getHealth() < originalHealth) {
                target.setHealth(
                        Math.min(originalHealth, target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
            }

            target.setNoDamageTicks(originalNoDamageTicks);
        });

        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);

        for (Player nearby : target.getWorld().getPlayers()) {
            if (nearby.getLocation().distance(target.getLocation()) < 20) {
                nearby.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
            }
        }
    }

    /**
     * Apply 1.8 combat mechanics to a player.
     */
    public void applyLegacyCombat(Player player) {
        if (!enabled)
            return;

        try {
            AttributeInstance attackSpeed = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (attackSpeed != null) {
                attackSpeed.setBaseValue(1024.0);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not apply legacy combat - ATTACK_SPEED attribute not found!");
        }
    }

    /**
     * Reset player to default combat mechanics.
     */
    public void resetCombat(Player player) {
        try {
            AttributeInstance attackSpeed = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (attackSpeed != null) {
                attackSpeed.setBaseValue(4.0);
            }
        } catch (Exception ignored) {
        }
    }
}
