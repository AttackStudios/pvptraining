package net.attackstudioyt.pvptraining;

import java.util.List;
import net.attackstudioyt.pvptraining.bot.BotPlayer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Unit;

/**
 * The four kits. Mace follows the common tier-list layout: elytra but no rockets.
 * Spear swaps the elytra for a Lunge III spear. Elytra + Mace adds rockets.
 */
public final class Kits {
	private Kits() {}

	/** Hands out gear for a mode: the trainee's saved kit if they made one, else the standard kit. */
	public static void give(ServerPlayerEntity player, String mode) {
		give(player, mode, true);
	}

	/** @param allowCustom false forces the standard kit (bots, and /kit edit). */
	public static void give(ServerPlayerEntity player, String mode, boolean allowCustom) {
		player.getInventory().clear();
		player.clearStatusEffects();
		player.setHealth(player.getMaxHealth());
		player.setAbsorptionAmount(0);
		player.getHungerManager().setFoodLevel(20);
		player.getHungerManager().setSaturationLevel(20);
		player.setFireTicks(0);
		player.fallDistance = 0;
		player.extinguish();

		// Bots always fight with the standard kit so every tier stays comparable.
		boolean custom = allowCustom && !(player instanceof BotPlayer) && CustomKits.apply(player, mode, CustomKits.active(mode));
		if (!custom) {
			switch (mode) {
				case "crystal" -> crystal(player);
				case "spear" -> mace(player, true, false);
				case "elytra_mace" -> mace(player, false, true);
				default -> mace(player, false, false);
			}
		}
		player.currentScreenHandler.sendContentUpdates();
		player.playerScreenHandler.syncState();
	}

	/** Keeps hunger from ever deciding a drill (Lunge needs more than 3 drumsticks). */
	public static void feed(ServerPlayerEntity player) {
		player.getHungerManager().setFoodLevel(20);
		player.getHungerManager().setSaturationLevel(8);
	}

	private static void mace(ServerPlayerEntity p, boolean spear, boolean rockets) {
		armor(p, false);
		var inv = p.getInventory();
		inv.setStack(0, ench(p, Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 5));
		inv.setStack(1, ench(p, Items.NETHERITE_AXE, Enchantments.SHARPNESS, 5));
		inv.setStack(2, rockets ? rocketStack() : new ItemStack(Items.ENDER_PEARL, 16));
		inv.setStack(3, new ItemStack(Items.GOLDEN_APPLE, 64));
		inv.setStack(4, new ItemStack(Items.WIND_CHARGE, 64));
		inv.setStack(5, spear ? ench(p, Items.NETHERITE_SPEAR, Enchantments.LUNGE, 3) : tough(new ItemStack(Items.ELYTRA)));
		inv.setStack(6, ench(p, Items.MACE, Enchantments.BREACH, 4));
		ItemStack density = ench(p, Items.MACE, Enchantments.DENSITY, 5);
		density.addEnchantment(entry(p, Enchantments.WIND_BURST), 1);
		inv.setStack(7, density);
		inv.setStack(8, tough(new ItemStack(Items.SHIELD)));
		p.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		inv.setStack(9, new ItemStack(Items.WIND_CHARGE, 64));
		inv.setStack(10, new ItemStack(Items.GOLDEN_APPLE, 64));
		inv.setStack(11, new ItemStack(Items.ENDER_PEARL, 16));
		if (rockets) inv.setStack(12, rocketStack());
	}

	private static void crystal(ServerPlayerEntity p) {
		armor(p, true);
		var inv = p.getInventory();
		inv.setStack(0, ench(p, Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 5));
		inv.setStack(1, new ItemStack(Items.RESPAWN_ANCHOR, 64));
		inv.setStack(2, new ItemStack(Items.GLOWSTONE, 64));
		inv.setStack(3, new ItemStack(Items.END_CRYSTAL, 64));
		inv.setStack(4, new ItemStack(Items.OBSIDIAN, 64));
		inv.setStack(5, new ItemStack(Items.ENDER_PEARL, 16));
		inv.setStack(6, new ItemStack(Items.GOLDEN_APPLE, 64));
		inv.setStack(7, new ItemStack(Items.TOTEM_OF_UNDYING));
		inv.setStack(8, ench(p, Items.NETHERITE_PICKAXE, Enchantments.EFFICIENCY, 5));
		p.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		int slot = 9;
		for (int i = 0; i < 13; i++) inv.setStack(slot++, new ItemStack(Items.TOTEM_OF_UNDYING));
		for (int i = 0; i < 4; i++) inv.setStack(slot++, new ItemStack(Items.END_CRYSTAL, 64));
		for (int i = 0; i < 3; i++) inv.setStack(slot++, new ItemStack(Items.OBSIDIAN, 64));
		inv.setStack(slot++, new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
		inv.setStack(slot++, new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
		inv.setStack(slot, new ItemStack(Items.ENDER_PEARL, 16));
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 40, 0, true, false));
	}

	private static void armor(ServerPlayerEntity p, boolean blastLegs) {
		p.equipStack(EquipmentSlot.HEAD, ench(p, Items.NETHERITE_HELMET, Enchantments.PROTECTION, 4));
		p.equipStack(EquipmentSlot.CHEST, ench(p, Items.NETHERITE_CHESTPLATE, Enchantments.PROTECTION, 4));
		p.equipStack(EquipmentSlot.LEGS, ench(p, Items.NETHERITE_LEGGINGS, blastLegs ? Enchantments.BLAST_PROTECTION : Enchantments.PROTECTION, 4));
		ItemStack boots = ench(p, Items.NETHERITE_BOOTS, Enchantments.PROTECTION, 4);
		boots.addEnchantment(entry(p, Enchantments.FEATHER_FALLING), 4);
		p.equipStack(EquipmentSlot.FEET, boots);
	}

	private static ItemStack rocketStack() {
		ItemStack rockets = new ItemStack(Items.FIREWORK_ROCKET, 64);
		rockets.set(DataComponentTypes.FIREWORKS, new FireworksComponent(1, List.of()));
		return rockets;
	}

	private static ItemStack ench(ServerPlayerEntity p, Item item, RegistryKey<Enchantment> key, int level) {
		ItemStack stack = tough(new ItemStack(item));
		stack.addEnchantment(entry(p, key), level);
		return stack;
	}

	private static ItemStack tough(ItemStack stack) {
		stack.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
		return stack;
	}

	private static RegistryEntry<Enchantment> entry(ServerPlayerEntity p, RegistryKey<Enchantment> key) {
		return p.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(key);
	}
}
