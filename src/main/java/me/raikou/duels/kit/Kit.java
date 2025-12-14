package me.raikou.duels.kit;

import lombok.Builder;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@Data
@Builder
public class Kit {
    private final String name;
    private final Material icon; // GUI display icon
    private final List<ItemStack> items;
    private final ItemStack helmet;
    private final ItemStack chestplate;
    private final ItemStack leggings;
    private final ItemStack boots;

    // Knockback settings (per-kit overrides)
    @Builder.Default
    private final double horizontalKB = -1; // -1 means use default
    @Builder.Default
    private final double verticalKB = -1;
    @Builder.Default
    private final double rodHorizontalKB = -1;
    @Builder.Default
    private final double rodVerticalKB = -1;

    /**
     * Check if this kit has custom knockback settings.
     */
    public boolean hasCustomKnockback() {
        return horizontalKB > 0 || verticalKB > 0;
    }

    /**
     * Check if this kit has custom rod knockback settings.
     */
    public boolean hasCustomRodKnockback() {
        return rodHorizontalKB > 0 || rodVerticalKB > 0;
    }

    public void equip(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        // Armor
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);

        // Items
        for (int i = 0; i < items.size(); i++) {
            if (i < 36) { // Main inventory limit
                player.getInventory().setItem(i, items.get(i));
            }
        }

        player.updateInventory();
    }
}
